/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.integration.library;

import static com.android.build.gradle.integration.common.utils.LibraryGraphHelper.Type.JAVA;
import static com.android.build.gradle.integration.common.utils.TestFileUtils.appendToFile;
import static com.android.testutils.truth.DexSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.annotations.NonNull;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelBuilder;
import com.android.build.gradle.integration.common.fixture.ModelContainer;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtils;
import com.android.build.gradle.integration.common.utils.LibraryGraphHelper;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.internal.coverage.JacocoOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.testutils.apk.Apk;
import com.android.testutils.apk.Dex;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.truth.Truth8;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Test for jacoco agent runtime dependencies. */
public class JacocoDependenciesTest {

    // AGP keeps an old version of the Jacoco libraries for testing version changes.
    private final String oldJacocoVersion = "0.7.4.201502262128";

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("projectWithModules").create();

    @Before
    public void setUp() throws Exception {
        Files.asCharSink(project.getSettingsFile(), Charsets.UTF_8)
                .write("include 'app', 'library'");

        appendToFile(
                project.getBuildFile(),
                "\nsubprojects {\n"
                        + "    apply from: \"$rootDir/../commonLocalRepo.gradle\"\n"
                        + "}\n");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\ndependencies {\n" + "    api project(':library')\n" + "}\n");

        appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\nandroid.buildTypes.debug.enableAndroidTestCoverage = true");

        appendToFile(
                project.getSubproject("library").getBuildFile(),
                "\nandroid.buildTypes.debug.enableAndroidTestCoverage = true");
    }

    @Test
    public void checkJacocoInApp() throws IOException, InterruptedException {
        project.executor().run("clean", "app:assembleDebug");

        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();

        Optional<Dex> dexOptional = apk.getMainDexFile();
        Truth8.assertThat(dexOptional).isPresent();

        assertThat(dexOptional.get()).containsClasses("Lorg/jacoco/agent/rt/IAgent;");
    }

    @Test
    public void checkJacocoInLibAndroidTest() throws IOException, InterruptedException {
        project.executor().run("clean", "library:assembleDebugAndroidTest");

        Apk apk =
                project.getSubproject(":library")
                        .getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG);
        assertThat(apk.getFile()).isFile();

        Optional<Dex> dexOptional = apk.getMainDexFile();
        Truth8.assertThat(dexOptional).isPresent();

        assertThat(dexOptional.get()).containsClasses("Lorg/jacoco/agent/rt/IAgent;");
    }

    @Test
    public void checkJacocoNotInAppWhenLibraryHasTestCoverageEnabled()
            throws IOException, InterruptedException {
        TestFileUtils.searchAndReplace(
                project.getSubproject("app").getBuildFile(),
                "android.buildTypes.debug.enableAndroidTestCoverage = true",
                "android.buildTypes.debug.enableAndroidTestCoverage = false");

        project.executor().run("clean", "app:assembleDebug");

        Apk apk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.DEBUG);
        assertThat(apk.getFile()).isFile();

        Optional<Dex> dexOptional = apk.getMainDexFile();
        Truth8.assertThat(dexOptional).isPresent();

        assertThat(dexOptional.get()).doesNotContainClasses("Lorg/jacoco/agent/rt/IAgent;");
    }

    @Test
    public void checkDefaultVersion() throws IOException {
        assertAgentMavenCoordinates(
                "org.jacoco:org.jacoco.agent:" + JacocoOptions.DEFAULT_VERSION + ":runtime@jar");
    }

    @Test
    public void checkVersionForced() throws IOException {
        TestFileUtils.searchAndReplace(
                project.getSubproject("app").getBuildFile(),
                "apply plugin: 'com.android.application'",
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "dependencies {\n"
                        + "  implementation "
                        + "'org.jacoco:org.jacoco.agent:"
                        + oldJacocoVersion
                        + ":runtime'\n"
                        + "}\n");
        assertAgentMavenCoordinates(
                "org.jacoco:org.jacoco.agent:" + JacocoOptions.DEFAULT_VERSION + ":runtime@jar");
    }

    @Test
    public void checkAgentRuntimeVersionWhenOverridden() throws IOException {
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n" + "android.jacoco.version '" + oldJacocoVersion + "'\n");
        assertAgentMavenCoordinates(
                "org.jacoco:org.jacoco.agent:" + oldJacocoVersion + ":runtime@jar");
    }

    private void assertAgentMavenCoordinates(@NonNull String expected) throws IOException {
        assertAgentMavenCoordinates(project.model(), expected);
    }

    private void assertAgentMavenCoordinates(
            @NonNull ModelBuilder modelBuilder, @NonNull String expected) throws IOException {
        ModelContainer<AndroidProject> container =
                modelBuilder
                        .level(AndroidProject.MODEL_LEVEL_LATEST)
                        .withFullDependencies()
                        .fetchAndroidProjects();
        LibraryGraphHelper helper = new LibraryGraphHelper(container);
        Variant appDebug =
                AndroidProjectUtils.getVariantByName(
                        container.getOnlyModelMap().get(":app"), "debug");

        DependencyGraphs dependencyGraphs = appDebug.getMainArtifact().getDependencyGraphs();
        assertThat(
                        helper.on(dependencyGraphs)
                                .forPackage()
                                .withType(JAVA)
                                .mapTo(LibraryGraphHelper.Property.COORDINATES))
                .named("jacoco agent runtime jar")
                .containsExactly(expected);
    }
}
