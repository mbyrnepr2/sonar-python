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
package org.sonar.python.checks.cdk;

import java.util.function.BiConsumer;
import org.sonar.check.Rule;
import org.sonar.plugins.python.api.SubscriptionContext;
import org.sonar.plugins.python.api.tree.CallExpression;

import static org.sonar.python.checks.cdk.CdkPredicate.isNone;
import static org.sonar.python.checks.cdk.CdkUtils.getArgument;

@Rule(key = "S6327")
public class DisabledSNSTopicEncryptionCheck extends AbstractCdkResourceCheck {
  private static final String OMITTING_MESSAGE = "Omitting \"%s\" disables SNS topics encryption. Make sure it is safe here.";

  @Override
  protected void registerFqnConsumer() {
    checkFqn("aws_cdk.aws_sns.Topic", checkTopic("master_key"));
    checkFqn("aws_cdk.aws_sns.CfnTopic", checkTopic("kms_master_key_id"));
  }

  private static BiConsumer<SubscriptionContext, CallExpression> checkTopic(String argMasterKeyName) {
    return (ctx, callExpression) -> getArgument(ctx, callExpression, argMasterKeyName)
      .ifPresentOrElse(
        argMasterKey -> argMasterKey.addIssueIf(isNone(), omittingMessage(argMasterKeyName)),
        () -> ctx.addIssue(callExpression.callee(), omittingMessage(argMasterKeyName)));
  }

  private static String omittingMessage(String argName) {
    return String.format(OMITTING_MESSAGE, argName);
  }
}
