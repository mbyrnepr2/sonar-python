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
package org.sonar.python.lexer;

import com.sonar.sslr.api.Token;
import com.sonar.sslr.impl.Lexer;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonar.python.api.PythonTokenType;

import static com.sonar.sslr.test.lexer.LexerMatchers.hasToken;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;


public class IPythonLexerTest {
  private static TestLexer lexer;

  @BeforeClass
  public static void init() {
    lexer = new IPythonLexerTest.TestLexer();
  }

  private static class TestLexer {
    private LexerState lexerState = new LexerState();
    private Lexer lexer = PythonLexer.ipynbLexer(lexerState);

    List<Token> lex(String code) {
      lexerState.reset();
      return lexer.lex(code);
    }
  }

  @Test
  public void cellDelimiterTest() {
    assertThat(lexer.lex("foo\n#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER"), hasToken(PythonTokenType.IPYNB_CELL_DELIMITER));
    assertThat(lexer.lex("foo #SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER"), not(hasToken(PythonTokenType.IPYNB_CELL_DELIMITER)));
    assertThat(lexer.lex("if cond:\n  foo()\n#SONAR_PYTHON_NOTEBOOK_CELL_DELIMITER"), hasToken(PythonTokenType.DEDENT));
  }
}
