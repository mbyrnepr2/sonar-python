/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2023 SonarSource SA
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
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.QualifiedExpression;
import org.sonar.plugins.python.api.tree.SubscriptionExpression;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TypeAnnotation;
import org.sonar.python.semantic.ClassSymbolImpl;
import org.sonar.python.tree.NameImpl;

@Rule(key = "S6543")
public class GenericTypeWithoutArgumentCheck extends PythonSubscriptionCheck {

  private static final String MESSAGE = "Add a type argument to this generic type.";

  private static final List<String> COLLECTIONS_NAME = Arrays.asList("typing.List", "typing.Dict", "typing.Set", "typing.Tuple");

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.RETURN_TYPE_ANNOTATION, GenericTypeWithoutArgumentCheck::checkForTypeArgument);
    context.registerSyntaxNodeConsumer(Tree.Kind.PARAMETER_TYPE_ANNOTATION, GenericTypeWithoutArgumentCheck::checkForTypeArgument);
    context.registerSyntaxNodeConsumer(Tree.Kind.VARIABLE_TYPE_ANNOTATION, GenericTypeWithoutArgumentCheck::checkForTypeArgument);
  }

  private static void checkForTypeArgument(SubscriptionContext subscriptionContext) {
    TypeAnnotation typeAnnotation = (TypeAnnotation) subscriptionContext.syntaxNode();
    Expression expression = typeAnnotation.expression();
    checkForGenericTypeWithoutArgument(subscriptionContext, expression);
  }

  private static void checkForGenericTypeWithoutArgument(SubscriptionContext subscriptionContext, Expression expression) {
    if (expression instanceof SubscriptionExpression) {
      checkNestedTypes(subscriptionContext, (SubscriptionExpression) expression);
      return;
    }

    if (typeSupportsGenericsOrIsACollection(expression) || qualifiedExpressionIsACollection(expression)) {
      subscriptionContext.addIssue(expression, MESSAGE);
    }
  }

  private static void checkNestedTypes(SubscriptionContext subscriptionContext, SubscriptionExpression expression) {
    List<Expression> expressions = expression.subscripts().expressions();
    expressions.forEach(nestedTypeExpression -> checkForGenericTypeWithoutArgument(subscriptionContext, nestedTypeExpression));
  }

  private static boolean typeSupportsGenericsOrIsACollection(Expression expression) {
    if (expression.is(Tree.Kind.NAME)) {
      NameImpl name = (NameImpl) expression;
      if (name.symbol() instanceof ClassSymbolImpl) {
        ClassSymbolImpl maybeSymbol = (ClassSymbolImpl) name.symbol();
        return Optional.ofNullable(maybeSymbol)
          .map(ClassSymbolImpl::supportsGenerics)
          .orElse(false);
      } else {
        return isACollection(name.symbol());
      }
    }
    return false;
  }

  private static boolean qualifiedExpressionIsACollection(Expression expression) {
    if (expression instanceof QualifiedExpression) {
      QualifiedExpression qualifiedExpression = (QualifiedExpression) expression;
      return isACollection(qualifiedExpression.symbol());
    }
    return false;
  }

  private static Boolean isACollection(@Nullable Symbol maybeSymbol) {
    return Optional.ofNullable(maybeSymbol)
      .map(symbol -> COLLECTIONS_NAME.contains(symbol.fullyQualifiedName()))
      .orElse(false);
  }
}
