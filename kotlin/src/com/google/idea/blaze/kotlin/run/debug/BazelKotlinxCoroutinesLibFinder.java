/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.run.debug;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.openapi.project.Project;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/**
 * Implements {@code KotlinCoroutinesLibFinder} for Bazel projects. This class searches for
 * kotlinx-coroutines-core library of version 1.3.8 or higher as IntelliJ coroutines debugging is
 * not supported for earlier versions.
 */
public class BazelKotlinxCoroutinesLibFinder implements KotlinxCoroutinesLibFinder {
  // Minimum coroutines library version to be used for comparison as coroutines debugging is not
  // available in earlier versions of the coroutines library.
  private static final DefaultArtifactVersion MIN_LIB_VERSION =
      new DefaultArtifactVersion("1.3.7-255");
  private static final Pattern LIB_PATTERN =
      Pattern.compile("(kotlinx-coroutines-core(-jvm)?)-(\\d[\\w.\\-]+)?\\.jar");

  @Override
  public Optional<ArtifactLocation> getKotlinxCoroutinesLib(@Nullable TargetIdeInfo depInfo) {
    if (depInfo == null) {
      return Optional.empty();
    }
    JavaIdeInfo javaIdeInfo = depInfo.getJavaIdeInfo();
    if (javaIdeInfo == null) {
      // The kotlinx-coroutines library jar should be in JavaIdeInfo
      return Optional.empty();
    }
    return javaIdeInfo.getJars().stream()
        .filter(j -> isKotlinxCoroutinesLib(j.getClassJar()))
        .findFirst()
        .map(j -> j.getClassJar());
  }

  @Override
  public boolean isApplicable(Project project) {
    return Blaze.getBuildSystem(project).equals(BuildSystem.Bazel);
  }

  private static boolean isKotlinxCoroutinesLib(@Nullable ArtifactLocation jarPath) {
    if (jarPath != null) {
      Matcher m = LIB_PATTERN.matcher(jarPath.getRelativePath());
      if (m.find() && m.groupCount() >= 3) {
        String version = m.group(3);
        if (new DefaultArtifactVersion(version).compareTo(MIN_LIB_VERSION) > 0) {
          return true;
        }
      }
    }
    return false;
  }
}
