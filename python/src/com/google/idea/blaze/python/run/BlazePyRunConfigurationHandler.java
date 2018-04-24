/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.run;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Python-specific handler for {@link BlazeCommandRunConfiguration}s. */
public final class BlazePyRunConfigurationHandler implements BlazeCommandRunConfigurationHandler {

  private final BuildSystem buildSystem;
  private final BlazePyRunConfigState state;

  public BlazePyRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    this.buildSystem = Blaze.getBuildSystem(configuration.getProject());
    this.state = new BlazePyRunConfigState(buildSystem);
  }

  @Override
  public BlazePyRunConfigState getState() {
    return state;
  }

  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment environment) {
    return new BlazePyRunConfigurationRunner();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    state.validate(buildSystem);
  }

  @Override
  @Nullable
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    if (configuration.getTarget() == null) {
      return null;
    }
    return new BlazeConfigurationNameBuilder(configuration).build();
  }

  @Override
  @Nullable
  public BlazeCommandName getCommandName() {
    return state.getCommandState().getCommand();
  }

  @Override
  public String getHandlerName() {
    return "Python Handler";
  }

  @Override
  @Nullable
  public Icon getExecutorIcon(RunConfiguration configuration, Executor executor) {
    return null;
  }
}
