/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2021 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.python.checks;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.symbols.Usage;
import org.sonar.plugins.python.api.tree.AssignmentStatement;
import org.sonar.plugins.python.api.tree.CompoundAssignmentStatement;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.Parameter;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.SubscriptionExpression;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.types.BuiltinTypes;
import org.sonar.python.tree.TreeUtils;

import static org.sonar.plugins.python.api.tree.Tree.Kind.ASSIGNMENT_STMT;
import static org.sonar.plugins.python.api.tree.Tree.Kind.COMPOUND_ASSIGNMENT;
import static org.sonar.plugins.python.api.tree.Tree.Kind.DEL_STMT;
import static org.sonar.plugins.python.api.tree.Tree.Kind.FUNCDEF;
import static org.sonar.plugins.python.api.tree.Tree.Kind.NAME;
import static org.sonar.plugins.python.api.tree.Tree.Kind.QUALIFIED_EXPR;
import static org.sonar.plugins.python.api.tree.Tree.Kind.SUBSCRIPTION;
import static org.sonar.python.tree.TreeUtils.getSymbolFromTree;
import static org.sonar.python.tree.TreeUtils.nonTupleParameters;

@Rule(key = "S5717")
public class ModifiedParameterValueCheck extends PythonSubscriptionCheck {

  private static final String MODIFIED_SECONDARY = "The parameter is modified.";
  private static final String ASSIGNED_SECONDARY = "The parameter is stored in another object.";

  private static final Set<String> COMMON_MUTATING_METHODS = new HashSet<>(Arrays.asList("__delitem__", "__setitem__"));
  private static final String CLEAR = "clear";
  private static final Set<String> LIST_MUTATING_METHODS = new HashSet<>(Arrays.asList("append", CLEAR, "extend", "insert", "pop", "remove", "reverse", "sort"));
  private static final Set<String> SET_MUTATING_METHODS = new HashSet<>(
          Arrays.asList("update", "intersection_update", "difference_update", "symmetric_difference_update", "add", "remove", "discard", "pop", CLEAR));
  private static final Set<String> DICT_MUTATING_METHODS = new HashSet<>(Arrays.asList("pop", CLEAR, "popitem", "setdefault", "update"));
  private static final Set<String> DEQUE_MUTATING_METHODS = new HashSet<>(Arrays.asList("appendleft", "extendleft", "popleft", "rotate"));
  static {
    DEQUE_MUTATING_METHODS.addAll(LIST_MUTATING_METHODS);
  }
  private static final Set<String> COUNTER_MUTATING_METHODS = new HashSet<>();
  static {
    COUNTER_MUTATING_METHODS.add("subtract");
    COUNTER_MUTATING_METHODS.addAll(DICT_MUTATING_METHODS);
  }

  private static final Set<String> ORDERED_DICT_MUTATING_METHODS = new HashSet<>();
  static {
    ORDERED_DICT_MUTATING_METHODS.add("move_to_end");
    ORDERED_DICT_MUTATING_METHODS.addAll(DICT_MUTATING_METHODS);
  }

  private static final Set<String> DEFAULT_DICT_MUTATING_METHODS = new HashSet<>();
  static {
    DEFAULT_DICT_MUTATING_METHODS.add("__getitem__");
    DEFAULT_DICT_MUTATING_METHODS.addAll(DICT_MUTATING_METHODS);
  }

  private static final Map<String, Set<String>> MUTATING_METHODS = new HashMap<>();
  static {
    MUTATING_METHODS.put("list", LIST_MUTATING_METHODS);
    MUTATING_METHODS.put("set", SET_MUTATING_METHODS);
    MUTATING_METHODS.put("dict", DICT_MUTATING_METHODS);
    MUTATING_METHODS.put("collections.deque", DEQUE_MUTATING_METHODS);
    MUTATING_METHODS.put("collections.UserList", LIST_MUTATING_METHODS);
    MUTATING_METHODS.put("collections.UserDict", DICT_MUTATING_METHODS);
    MUTATING_METHODS.put("collections.ChainMap", DICT_MUTATING_METHODS);
    MUTATING_METHODS.put("collections.Counter", COUNTER_MUTATING_METHODS);
    MUTATING_METHODS.put("collections.OrderedDict", ORDERED_DICT_MUTATING_METHODS);
    MUTATING_METHODS.put("collections.defaultdict", DEFAULT_DICT_MUTATING_METHODS);
  }

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(FUNCDEF, ctx -> {
      FunctionDef functionDef = (FunctionDef) ctx.syntaxNode();
      // avoid raising issues on nested function, it may have been done on purpose
      if (TreeUtils.firstAncestorOfKind(functionDef, FUNCDEF) != null) {
        return;
      }
      for (Parameter parameter : nonTupleParameters(functionDef)) {
        Map<Tree, String> mutations = getMutations(parameter);
        if (!mutations.isEmpty()) {
          PreciseIssue preciseIssue = ctx.addIssue(parameter, "Change this default value to \"None\" and initialize this parameter inside the function/method.");
          mutations.keySet().forEach(t -> preciseIssue.secondary(t, mutations.get(t)));
        }
      }
    });
  }

  @CheckForNull
  private static String defaultValueType(Expression expression) {
    for (String nonCompliantType : MUTATING_METHODS.keySet()) {
      if (expression.type().canOnlyBe(nonCompliantType)) {
        return nonCompliantType;
      }
    }
    return null;
  }

  private static boolean isUsingMemoization(Symbol symbol) {
    return symbol.name().contains("cache") || symbol.name().contains("memo");
  }

  private static Map<Tree, String> getMutations(Parameter parameter) {
    Expression defaultValue = parameter.defaultValue();
    if (defaultValue == null) {
      return Collections.emptyMap();
    }

    Optional<Symbol> paramSymbol = getSymbolFromTree(parameter.name());
    if (!paramSymbol.isPresent() || isUsingMemoization(paramSymbol.get())) {
      return Collections.emptyMap();
    }

    if (!defaultValue.type().canOnlyBe(BuiltinTypes.NONE_TYPE)) {
      List<Tree> attributeSet = getAttributeSet(paramSymbol.get());
      if (!attributeSet.isEmpty()) {
        return attributeSet.stream().collect(Collectors.toMap(tree -> tree, tree -> MODIFIED_SECONDARY));
      }
    }
    String defaultValueType = defaultValueType(defaultValue);
    Set<String> typeMutatingMethods = MUTATING_METHODS.get(defaultValueType);
    if (typeMutatingMethods == null) {
      return Collections.emptyMap();
    }
    Map<Tree, String> mutations = new HashMap<>();
    for (Usage usage : paramSymbol.get().usages()) {
      getKindOfWriteUsage(paramSymbol, defaultValueType, typeMutatingMethods, usage).ifPresent(s -> mutations.put(usage.tree().parent(), s));
    }
    return mutations;
  }

  private static Optional<String> getKindOfWriteUsage(Optional<Symbol> paramSymbol, @Nullable String defaultValueType, Set<String> typeMutatingMethods, Usage usage) {
    Tree parent = usage.tree().parent();
    if (parent.is(QUALIFIED_EXPR)) {
      QualifiedExpression qualifiedExpression = (QualifiedExpression) parent;
      return getSymbolFromTree(qualifiedExpression.qualifier()).equals(paramSymbol) && isMutatingMethod(typeMutatingMethods, qualifiedExpression.name().name()) ?
        Optional.of(MODIFIED_SECONDARY) : Optional.empty();
    }
    if (isUsedInDelStatement(usage.tree()) ||
      isUsedInLhsOfAssignment(usage.tree(), exp -> isAccessingExpression(exp, usage.tree())) ||
      isUsedInLhsOfCompoundAssignment(usage.tree()) ||
      isGetItemOnDefaultDict(defaultValueType, usage.tree())) {
      return Optional.of(MODIFIED_SECONDARY);
    }
    return mightBeReferencedOutsideOfFunction(usage.tree()) ? Optional.of(ASSIGNED_SECONDARY) : Optional.empty();
  }

  /**
   * Detects if shape of tree is equal to
   * - expression[SOMETHING]
   * - expression.SOMETHING
   */
  private static boolean isAccessingExpression(Expression expression, Tree tree) {
    return isObjectOfSubscription(tree, expression) || isQualifier(tree, expression);
  }

  /**
   * Detects case where tree might be referenced outside of function
   * - self.attr = tree
   */
  private static boolean mightBeReferencedOutsideOfFunction(Tree tree) {
    AssignmentStatement assignment = ((AssignmentStatement) TreeUtils.firstAncestorOfKind(tree, ASSIGNMENT_STMT));
    if (assignment == null) {
      return false;
    }
    return assignment.assignedValue() == tree
      && assignment.lhsExpressions().stream()
      .flatMap(expressionList -> expressionList.expressions().stream())
      .anyMatch(ModifiedParameterValueCheck::isAccessingSelf);
  }

  private static boolean isAccessingSelf(Expression expression) {
    switch (expression.getKind()) {
      case QUALIFIED_EXPR:
        return isSelf(((QualifiedExpression) expression).qualifier());
      case SUBSCRIPTION:
        return isSelf(((SubscriptionExpression) expression).object());
      default:
        return false;
    }
  }

  private static boolean isSelf(Expression expression) {
    return expression.is(NAME) && ((Name) expression).name().equals("self");
  }

  private static List<Tree> getAttributeSet(Symbol paramSymbol) {
    return paramSymbol.usages().stream()
      .map(Usage::tree)
      .filter(tree -> isUsedInLhsOfAssignment(tree, expression -> isQualifier(tree, expression)))
      .collect(Collectors.toList());
  }

  private static boolean isUsedInLhsOfCompoundAssignment(Tree tree) {
    CompoundAssignmentStatement compoundAssignmentStatement = ((CompoundAssignmentStatement) TreeUtils.firstAncestorOfKind(tree, COMPOUND_ASSIGNMENT));
    return compoundAssignmentStatement != null && isAccessingExpression(compoundAssignmentStatement.lhsExpression(), tree);
  }

  private static boolean isGetItemOnDefaultDict(@Nullable String defaultValueType, Tree tree) {
    return "collections.defaultdict".equals(defaultValueType) && isObjectOfSubscription(tree, tree.parent());
  }

  private static boolean isObjectOfSubscription(Tree usageTree, Tree tree) {
    return tree.is(SUBSCRIPTION) && ((SubscriptionExpression) tree).object() == usageTree;
  }

  private static boolean isQualifier(Tree usageTree, Tree tree) {
    return tree.is(QUALIFIED_EXPR) && ((QualifiedExpression) tree).qualifier() == usageTree;
  }

  private static boolean isUsedInLhsOfAssignment(Tree tree, Predicate<Expression> lhsPredicate) {
    AssignmentStatement assignment = ((AssignmentStatement) TreeUtils.firstAncestorOfKind(tree, ASSIGNMENT_STMT));
    if (assignment == null) {
      return false;
    }
    return assignment.lhsExpressions().stream()
      .flatMap(expressionList -> expressionList.expressions().stream())
      .anyMatch(lhsPredicate);
  }

  private static boolean isUsedInDelStatement(Tree tree) {
    return TreeUtils.firstAncestorOfKind(tree, DEL_STMT) != null;
  }

  private static boolean isMutatingMethod(Set<String> typeMutatingMethods, String method) {
    return typeMutatingMethods.contains(method) ||
      COMMON_MUTATING_METHODS.contains(method) ||
      (method.startsWith("__i") && method.endsWith("__"));
  }
}
