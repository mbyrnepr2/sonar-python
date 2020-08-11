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
package org.sonar.python.checks;

import java.util.ArrayList;
import java.util.List;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.PythonSubscriptionCheck;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.symbols.Symbol;
import org.sonar.plugins.python.api.tree.BaseTreeVisitor;
import org.sonar.plugins.python.api.tree.Expression;
import org.sonar.plugins.python.api.tree.FunctionDef;
import org.sonar.plugins.python.api.tree.ReturnStatement;
import org.sonar.plugins.python.api.tree.Tree;
import org.sonar.plugins.python.api.tree.TypeAnnotation;
import org.sonar.plugins.python.api.tree.YieldStatement;
import org.sonar.plugins.python.api.types.InferredType;
import org.sonar.python.semantic.FunctionSymbolImpl;
import org.sonar.python.types.InferredTypes;

@Rule(key = "S5886")
public class FunctionReturnTypeCheck extends PythonSubscriptionCheck {

  private static final String MESSAGE = "Return a value of type \"%s\" instead of \"%s\" or update function \"%s\" type hint.";

  @Override
  public void initialize(Context context) {
    context.registerSyntaxNodeConsumer(Tree.Kind.FUNCDEF, ctx -> {
      FunctionDef functionDef = (FunctionDef) ctx.syntaxNode();
      Symbol symbol = functionDef.name().symbol();
      if (symbol == null || !symbol.is(Symbol.Kind.FUNCTION)) {
        return;
      }
      FunctionSymbolImpl functionSymbol = (FunctionSymbolImpl) symbol;
      InferredType declaredReturnType = functionSymbol.declaredReturnType();
      if (declaredReturnType == InferredTypes.anyType()) {
        return;
      }
      ReturnTypeVisitor returnTypeVisitor = new ReturnTypeVisitor(declaredReturnType);
      functionDef.accept(returnTypeVisitor);
      raiseIssues(ctx, functionDef, declaredReturnType, returnTypeVisitor);
    });
  }

  private static void raiseIssues(SubscriptionContext ctx, FunctionDef functionDef, InferredType declaredReturnType, ReturnTypeVisitor returnTypeVisitor) {
    String functionName = functionDef.name().name();
    String returnTypeName = InferredTypes.typeName(declaredReturnType);
    if (!returnTypeVisitor.yieldStatements.isEmpty()) {
      if (declaredReturnType.mustBeOrExtend("typing.Generator")) {
        return;
      }
      returnTypeVisitor.yieldStatements
        .forEach(y -> ctx.addIssue(y, String.format("Remove this yield statement or annotate function \"%s\" with \"typing.Generator\".", functionName)));
    }
    returnTypeVisitor.invalidReturnStatements.forEach(i -> {
      PreciseIssue issue;
      if (i.expressions().size() > 1) {
        issue = ctx.addIssue(i, String.format(MESSAGE, returnTypeName, "tuple", functionName));
      } else if (i.expressions().size() == 1 && InferredTypes.typeName(i.expressions().get(0).type()) != null) {
        issue = ctx.addIssue(i.expressions().get(0), String.format(MESSAGE, returnTypeName, InferredTypes.typeName(i.expressions().get(0).type()), functionName));
      } else {
        issue = ctx.addIssue(i, String.format("Return a value of type \"%s\" or update function \"%s\" type hint.", returnTypeName, functionName));
      }
      addSecondaries(issue, functionDef);
    });
  }

  private static void addSecondaries(PreciseIssue issue, FunctionDef functionDef) {
    issue.secondary(functionDef.name(), "Function definition.");
    TypeAnnotation returnTypeAnnotation = functionDef.returnTypeAnnotation();
    if (returnTypeAnnotation != null) {
      issue.secondary(returnTypeAnnotation.expression(), "Type hint.");
    }
  }

  private static class ReturnTypeVisitor extends BaseTreeVisitor {

    InferredType returnType;
    List<YieldStatement> yieldStatements = new ArrayList<>();
    List<ReturnStatement> invalidReturnStatements = new ArrayList<>();

    ReturnTypeVisitor(InferredType returnType) {
      this.returnType = returnType;
    }

    @Override
    public void visitReturnStatement(ReturnStatement returnStatement) {
      List<Expression> expressions = returnStatement.expressions();
      if (expressions.isEmpty()) {
        if (!returnType.mustBeOrExtend("NoneType")) {
          invalidReturnStatements.add(returnStatement);
        }
      } else if (expressions.size() > 1) {
        if (!returnType.canBeOrExtend("tuple")) {
          invalidReturnStatements.add(returnStatement);
        }
      } else {
        Expression expression = expressions.get(0);
        InferredType inferredType = expression.type();
        if (!inferredType.isCompatibleWith(returnType)) {
          invalidReturnStatements.add(returnStatement);
        }
      }
      super.visitReturnStatement(returnStatement);
    }

    @Override
    public void visitYieldStatement(YieldStatement yieldStatement) {
      yieldStatements.add(yieldStatement);
      super.visitYieldStatement(yieldStatement);
    }
  }
}