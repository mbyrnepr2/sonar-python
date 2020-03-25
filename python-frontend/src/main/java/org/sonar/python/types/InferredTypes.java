/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2020 SonarSource SA
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
package org.sonar.python.types;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.plugins.python.api.symbols.ClassSymbol;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.Name;
import org.sonar.plugins.python.api.tree.SubscriptionExpression;
import org.sonar.plugins.python.api.tree.Tree.Kind;
import org.sonar.plugins.python.api.tree.TypeAnnotation;
import org.sonar.plugins.python.api.types.BuiltinTypes;
import org.sonar.plugins.python.api.types.InferredType;
import org.sonar.python.tree.TreeUtils;

public class InferredTypes {

  private static final Map<String, String> ALIASED_ANNOTATIONS = new HashMap<>();
  static {
    ALIASED_ANNOTATIONS.put("typing.List", BuiltinTypes.LIST);
    ALIASED_ANNOTATIONS.put("typing.Tuple", BuiltinTypes.TUPLE);
    ALIASED_ANNOTATIONS.put("typing.Dict", BuiltinTypes.DICT);
    ALIASED_ANNOTATIONS.put("typing.Set", BuiltinTypes.SET);
    ALIASED_ANNOTATIONS.put("typing.FrozenSet", "frozenset");
    ALIASED_ANNOTATIONS.put("typing.Deque", "deque");
    ALIASED_ANNOTATIONS.put("typing.DefaultDict", "defaultdict");
    ALIASED_ANNOTATIONS.put("typing.Type", "type");
  }

  public static final InferredType INT = runtimeBuiltinType(BuiltinTypes.INT);
  public static final InferredType FLOAT = runtimeBuiltinType(BuiltinTypes.FLOAT);
  public static final InferredType COMPLEX = runtimeBuiltinType(BuiltinTypes.COMPLEX);

  public static final InferredType STR = runtimeBuiltinType(BuiltinTypes.STR);

  public static final InferredType SET = runtimeBuiltinType(BuiltinTypes.SET);
  public static final InferredType DICT = runtimeBuiltinType(BuiltinTypes.DICT);
  public static final InferredType LIST = runtimeBuiltinType(BuiltinTypes.LIST);
  public static final InferredType TUPLE = runtimeBuiltinType(BuiltinTypes.TUPLE);

  public static final InferredType NONE = runtimeBuiltinType(BuiltinTypes.NONE_TYPE);

  public static final InferredType BOOL = runtimeBuiltinType(BuiltinTypes.BOOL);

  private InferredTypes() {
  }

  public static InferredType anyType() {
    return AnyType.ANY;
  }

  private static InferredType runtimeBuiltinType(String fullyQualifiedName) {
    return new RuntimeType(TypeShed.typeShedClass(fullyQualifiedName));
  }

  public static InferredType runtimeType(@Nullable Symbol typeClass) {
    if (typeClass instanceof ClassSymbol) {
      return new RuntimeType((ClassSymbol) typeClass);
    }
    return anyType();
  }

  public static InferredType or(InferredType t1, InferredType t2) {
    return UnionType.or(t1, t2);
  }

  public static InferredType declaredType(TypeAnnotation typeAnnotation, Map<String, Symbol> builtinSymbols) {
    Expression expression = typeAnnotation.expression();
    if (expression.is(Kind.NAME) && !((Name) expression).name().equals("Any")) {
      // TODO change it to DeclaredType instance
      return InferredTypes.runtimeType(((Name) expression).symbol());
    }
    if (expression.is(Kind.SUBSCRIPTION)) {
      SubscriptionExpression subscription = (SubscriptionExpression) expression;
      return TreeUtils.getSymbolFromTree(subscription.object())
        .flatMap(symbol -> InferredTypes.genericType(symbol, builtinSymbols))
        .orElse(InferredTypes.anyType());
    }
    return InferredTypes.anyType();
  }

  private static Optional<InferredType> genericType(Symbol symbol, Map<String, Symbol> builtinSymbols) {
    return Optional.ofNullable(ALIASED_ANNOTATIONS.get(symbol.fullyQualifiedName()))
      .map(fqn -> InferredTypes.runtimeType(builtinSymbols.get(fqn)));
  }
}
