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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.python.api.tree.Expression;
import org.sonar.python.api.tree.ParenthesizedExpression;
import org.sonar.python.api.tree.Token;
import org.sonar.python.api.tree.TreeVisitor;
import org.sonar.python.api.tree.Tree;

public class ParenthesizedExpressionImpl extends PyTree implements ParenthesizedExpression {

  private final Token leftParenthesis;
  private final Expression expression;
  private final Token rightParenthesis;

  public ParenthesizedExpressionImpl(Token leftParenthesis, Expression expression, Token rightParenthesis) {
    super(leftParenthesis, rightParenthesis);
    this.leftParenthesis = leftParenthesis;
    this.expression = expression;
    this.rightParenthesis = rightParenthesis;
  }

  @Override
  public Token leftParenthesis() {
    return leftParenthesis;
  }

  @Override
  public Expression expression() {
    return expression;
  }

  @Override
  public Token rightParenthesis() {
    return rightParenthesis;
  }

  @Override
  public void accept(TreeVisitor visitor) {
    visitor.visitParenthesizedExpression(this);
  }

  @Override
  public List<Tree> childs() {
    return Stream.of(leftParenthesis, expression, rightParenthesis).collect(Collectors.toList());
  }

  @Override
  public Kind getKind() {
    return Kind.PARENTHESIZED;
  }
}
