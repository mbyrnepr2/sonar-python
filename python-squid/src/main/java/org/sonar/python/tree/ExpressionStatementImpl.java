/*
 * SonarQube Python Plugin
 * Copyright (C) 2011-2019 SonarSource SA
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
package org.sonar.python.tree;

import com.sonar.sslr.api.AstNode;
import java.util.Collections;
import java.util.List;
import org.sonar.python.api.tree.ExpressionStatement;
import org.sonar.python.api.tree.Expression;
import org.sonar.python.api.tree.TreeVisitor;
import org.sonar.python.api.tree.Tree;

public class ExpressionStatementImpl extends PyTree implements ExpressionStatement {
  private final List<Expression> expressions;

  public ExpressionStatementImpl(AstNode astNode, List<Expression> expressions) {
    super(astNode);
    this.expressions = expressions;
  }
  @Override
  public Kind getKind() {
    return Kind.EXPRESSION_STMT;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitExpressionStatement(this);
  }

  @Override
  public List<Expression> expressions() {
    return expressions;
  }

  @Override
  public List<Tree> children() {
    return Collections.unmodifiableList(expressions);
  }
}