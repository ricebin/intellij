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

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BazelKotlinxCoroutinesLibFinder}. */
@RunWith(JUnit4.class)
public class BazelKotlinxCoroutinesLibFinderTest extends BlazeRunConfigurationProducerTestCase {

  private TargetIdeInfo kotlinxCoroutinesLib;
  private TargetIdeInfo kotlinxCoroutinesOldVersion;
  private TargetIdeInfo kotlinxCoroutinesLibNoJavaIdeInfo;
  private TargetIdeInfo kotlinxCoroutinesLibNoJars;
  private TargetIdeInfo kotlinxCoroutinesLibMultipleJars;

  @Override
  protected BuildSystem buildSystem() {
    return BuildSystem.Bazel;
  }

  @Before
  public final void setup() {
    kotlinxCoroutinesLib =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel("//kotlinx_coroutines:jar")
            .setJavaInfo(
                JavaIdeInfo.builder()
                    .addJar(
                        LibraryArtifact.builder()
                            .setClassJar(
                                ArtifactLocation.builder()
                                    .setRelativePath("kotlinx-coroutines-core-1.4.2.jar")
                                    .build())))
            .build();

    kotlinxCoroutinesOldVersion =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel("//kotlinx_coroutines:jar")
            .setJavaInfo(
                JavaIdeInfo.builder()
                    .addJar(
                        LibraryArtifact.builder()
                            .setClassJar(
                                ArtifactLocation.builder()
                                    .setRelativePath("kotlinx-coroutines-core-1.2.0.jar")
                                    .build())))
            .build();

    kotlinxCoroutinesLibNoJavaIdeInfo =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel("//kotlinx_coroutines:jar")
            .build();

    kotlinxCoroutinesLibNoJars =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel("//kotlinx_coroutines:jar")
            .setJavaInfo(JavaIdeInfo.builder())
            .build();

    kotlinxCoroutinesLibMultipleJars =
        TargetIdeInfo.builder()
            .setKind("kt_jvm_library")
            .setLabel("//kotlinx_coroutines:jar")
            .setJavaInfo(
                JavaIdeInfo.builder()
                    .addJar(
                        LibraryArtifact.builder()
                            .setClassJar(
                                ArtifactLocation.builder()
                                    .setRelativePath("another-lib-jar.jar")
                                    .build()))
                    .addJar(
                        LibraryArtifact.builder()
                            .setClassJar(
                                ArtifactLocation.builder()
                                    .setRelativePath("kotlinx-coroutines-core-1.4.2.jar")
                                    .build())))
            .build();
  }

  @Test
  public void kotlinxCoroutinesLibFound() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .addDependency("//kotlinx_coroutines:jar")
                    .build())
            .addTarget(kotlinxCoroutinesLib)
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
    PsiFile mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "import kotlinx.coroutines.runBlocking",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinxCoroutinesLibFinderHelper.findKotlinxCoroutinesLib(
            (BlazeCommandRunConfiguration) config);
    assertThat(coroutinesLibLocation.isPresent()).isTrue();
    assertThat(coroutinesLibLocation.get().getRelativePath())
        .isEqualTo("kotlinx-coroutines-core-1.4.2.jar");
  }

  @Test
  public void kotlinxCoroutinesTransitiveDependencyFound() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .addRuntimeDep("//com/google/binary:lib_kt")
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_library")
                    .setLabel("//com/google/binary:lib_kt")
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .addDependency("//kotlinx_coroutines:jar")
                    .build())
            .addTarget(kotlinxCoroutinesLib)
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
    PsiFile mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "import kotlinx.coroutines.runBlocking",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinxCoroutinesLibFinderHelper.findKotlinxCoroutinesLib(
            (BlazeCommandRunConfiguration) config);
    assertThat(coroutinesLibLocation.isPresent()).isNotNull();
    assertThat(coroutinesLibLocation.get().getRelativePath())
        .isEqualTo("kotlinx-coroutines-core-1.4.2.jar");
  }

  @Test
  public void kotlinxCoroutinesNotFound() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
    PsiFile mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinxCoroutinesLibFinderHelper.findKotlinxCoroutinesLib(
            (BlazeCommandRunConfiguration) config);
    assertThat(coroutinesLibLocation.isPresent()).isFalse();
  }

  @Test
  public void oldKotlinxCoroutinesLibVersionUsed() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .addDependency("//kotlinx_coroutines:jar")
                    .build())
            .addTarget(kotlinxCoroutinesOldVersion)
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
    PsiFile mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "import kotlinx.coroutines.runBlocking",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinxCoroutinesLibFinderHelper.findKotlinxCoroutinesLib(
            (BlazeCommandRunConfiguration) config);
    assertThat(coroutinesLibLocation.isPresent()).isFalse();
  }

  @Test
  public void missingBlazeProjectData() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .addDependency("//kotlinx_coroutines:jar")
                    .build())
            .addTarget(kotlinxCoroutinesLib)
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MutableMockBlazeProjectDataManager(builder.build()));
    PsiFile mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "import kotlinx.coroutines.runBlocking",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);

    // Set BlazeProjectData to null in the MutableMockBlazeProjectDataManager
    ((MutableMockBlazeProjectDataManager) BlazeProjectDataManager.getInstance(config.getProject()))
        .nullifyBlazeProjectData();

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinxCoroutinesLibFinderHelper.findKotlinxCoroutinesLib(
            (BlazeCommandRunConfiguration) config);
    assertThat(coroutinesLibLocation.isPresent()).isFalse();
  }

  @Test
  public void missingKotlinxCoroutinesLibFinderEP() throws Throwable {
    // Unregister all EPs of KotlinxCoroutinesLibFinder
    KotlinxCoroutinesLibFinder.EP_NAME.getExtensionList().stream()
        .forEach(
            ep ->
                Extensions.getRootArea()
                    .getExtensionPoint(KotlinxCoroutinesLibFinder.EP_NAME)
                    .unregisterExtension(ep));

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .addDependency("//kotlinx_coroutines:jar")
                    .build())
            .addTarget(kotlinxCoroutinesLib)
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
    PsiFile mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "import kotlinx.coroutines.runBlocking",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinxCoroutinesLibFinderHelper.findKotlinxCoroutinesLib(
            (BlazeCommandRunConfiguration) config);
    assertThat(coroutinesLibLocation.isPresent()).isFalse();
  }

  @Test
  public void invalidKotlinxCoroutinesLib_noJavaIdeInfo() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .addDependency("//kotlinx_coroutines:jar")
                    .build())
            .addTarget(kotlinxCoroutinesLibNoJavaIdeInfo)
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
    PsiFile mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "import kotlinx.coroutines.runBlocking",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinxCoroutinesLibFinderHelper.findKotlinxCoroutinesLib(
            (BlazeCommandRunConfiguration) config);
    assertThat(coroutinesLibLocation.isPresent()).isFalse();
  }

  @Test
  public void invalidKotlinxCoroutinesLib_noJarsFound() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .addDependency("//kotlinx_coroutines:jar")
                    .build())
            .addTarget(kotlinxCoroutinesLibNoJars)
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
    PsiFile mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "import kotlinx.coroutines.runBlocking",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinxCoroutinesLibFinderHelper.findKotlinxCoroutinesLib(
            (BlazeCommandRunConfiguration) config);
    assertThat(coroutinesLibLocation.isPresent()).isFalse();
  }

  @Test
  public void invalidKotlinxCoroutinesLib_noIDEInfo() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .addDependency("//kotlinx_coroutines:jar")
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
    PsiFile mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "import kotlinx.coroutines.runBlocking",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinxCoroutinesLibFinderHelper.findKotlinxCoroutinesLib(
            (BlazeCommandRunConfiguration) config);
    assertThat(coroutinesLibLocation.isPresent()).isFalse();
  }

  @Test
  public void invalidKotlinxCoroutinesLib_multipleJarsFound() throws Throwable {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("kt_jvm_binary")
                    .setLabel("//com/google/binary:main_kt")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainKt"))
                    .addSource(sourceRoot("com/google/binary/Main.kt"))
                    .addDependency("//kotlinx_coroutines:jar")
                    .build())
            .addTarget(kotlinxCoroutinesLibMultipleJars)
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
    PsiFile mainFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/Main.kt"),
            "package com.google.binary",
            "import kotlinx.coroutines.runBlocking",
            "fun main(args: Array<String>) {}");

    RunConfiguration config = createConfigurationFromLocation(mainFile);
    assertThat(config).isNotNull();
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);

    Optional<ArtifactLocation> coroutinesLibLocation =
        KotlinxCoroutinesLibFinderHelper.findKotlinxCoroutinesLib(
            (BlazeCommandRunConfiguration) config);
    assertThat(coroutinesLibLocation.isPresent()).isTrue();
    assertThat(coroutinesLibLocation.get().getRelativePath())
        .isEqualTo("kotlinx-coroutines-core-1.4.2.jar");
  }

  private static class MutableMockBlazeProjectDataManager extends MockBlazeProjectDataManager {

    private boolean nullBlazeProjectData;

    public MutableMockBlazeProjectDataManager(BlazeProjectData blazeProjectData) {
      super(blazeProjectData);
      this.nullBlazeProjectData = false;
    }

    private void nullifyBlazeProjectData() {
      this.nullBlazeProjectData = true;
    }

    @Override
    public BlazeProjectData getBlazeProjectData() {
      if (nullBlazeProjectData) {
        return null;
      }
      return super.getBlazeProjectData();
    }
  }
}
