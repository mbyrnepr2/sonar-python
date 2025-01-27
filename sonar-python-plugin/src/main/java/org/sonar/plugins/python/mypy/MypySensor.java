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
package org.sonar.plugins.python.mypy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.python.ExternalIssuesSensor;
import org.sonar.plugins.python.TextReportReader;

public class MypySensor extends ExternalIssuesSensor {

  private static final Logger LOG = Loggers.get(MypySensor.class);

  public static final String LINTER_NAME = "Mypy";
  public static final String LINTER_KEY = "mypy";
  public static final String REPORT_PATH_KEY = "sonar.python.mypy.reportPaths";

  private static final String FALLBACK_RULE_KEY = "unknown_mypy_rule";

  // Pattern -> Location ': ' Severity ':' Message '['Code']'
  // Location -> File ':' StartLine
  private static final Pattern PATTERN =
    Pattern.compile("^(?<file>[^:]+):(?<startLine>\\d+)(?::(?<startCol>\\d+))?(?::\\d+:\\d+)?: (?<severity>\\S+[^:]): (?<message>.*?)(?: \\[(?<code>.*)])?\\s*$");

  @Override
  protected void importReport(File reportPath, SensorContext context, Set<String> unresolvedInputFiles) throws IOException {
    List<TextReportReader.Issue> issues = parse(reportPath, context.fileSystem());
    issues.forEach(i -> saveIssue(context, i, unresolvedInputFiles, LINTER_KEY));
  }

  private static List<TextReportReader.Issue> parse(File report, FileSystem fileSystem) throws IOException {
    List<TextReportReader.Issue> issues = new ArrayList<>();
    try (Scanner scanner = new Scanner(report.toPath(), fileSystem.encoding().name())) {
      while (scanner.hasNextLine()) {
        TextReportReader.Issue issue = parseLine(scanner.nextLine());
        if (issue != null) {
          issues.add(issue);
        }
      }
    }
    return issues;
  }

  private static TextReportReader.Issue parseLine(String line) {
    if (line.length() > 0) {
      Matcher m = PATTERN.matcher(line);
      if (m.matches()) {
        return extractIssue(m);
      }
      LOG.debug("Cannot parse the line: {}", line);
    }

    return null;
  }

  private static TextReportReader.Issue extractIssue(Matcher m) {
    String severity = m.group("severity");
    if (!"error".equals(severity)) {
      return null;
    }

    String filePath = m.group("file");
    int lineNumber = Integer.parseInt(m.group("startLine"));
    String message = m.group("message");
    String errorCode = m.group("code");
    if (errorCode == null) {
      // Sometimes mypy does not report an error code, however the API expects a non-null error code.
      errorCode = FALLBACK_RULE_KEY;
    }

    Integer columnNumber = Optional.ofNullable(m.group("startCol"))
      .map(Integer::parseInt)
      .map(i -> i - 1)
      .orElse(null);

    return new TextReportReader.Issue(filePath, errorCode, message, lineNumber, columnNumber);
  }

  @Override
  protected boolean shouldExecute(Configuration conf) {
    return conf.hasKey(REPORT_PATH_KEY);
  }

  @Override
  protected String linterName() {
    return LINTER_NAME;
  }

  @Override
  protected String reportPathKey() {
    return REPORT_PATH_KEY;
  }

  @Override
  protected Logger logger() {
    return LOG;
  }
}
