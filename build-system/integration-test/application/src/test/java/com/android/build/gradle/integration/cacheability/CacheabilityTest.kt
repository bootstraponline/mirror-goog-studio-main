/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.cacheability

import com.google.common.truth.Truth.assertThat

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FROM_CACHE
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.FAILED
import com.android.testutils.truth.FileSubject.assertThat
import com.android.utils.FileUtils

import com.google.common.collect.Sets
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Tests cacheability of tasks.
 *
 * See https://guides.gradle.org/using-build-cache/ for information on the Gradle build cache.
 */
@RunWith(JUnit4::class)
class CacheabilityTest {

    companion object {

        private const val GRADLE_BUILD_CACHE_DIR = "gradle-build-cache"

        /**
         * The expected states of tasks when running a second build with the Gradle build cache
         * enabled from an identical project at a different location.
         */
        private val EXPECTED_TASK_STATES =
            mapOf(
                UP_TO_DATE to setOf(
                    ":clean",
                    ":preBuild",
                    ":generateDebugResources",
                    ":compileDebugSources"
                ),
                FROM_CACHE to setOf(
                    ":preDebugBuild",
                    ":compileDebugRenderscript",
                    ":javaPreCompileDebug",
                    ":generateDebugResValues",
                    ":mergeDebugResources",
                    ":compileDebugJavaWithJavac",
                    ":mergeDebugShaders",
                    ":mergeDebugAssets",
                    ":mergeExtDexDebug",
                    ":mergeDebugJniLibFolders"
                    ),
                DID_WORK to setOf(
                    ":checkDebugManifest",
                    ":generateDebugBuildConfig",
                    ":prepareLintJar",
                    ":mainApkListPersistenceDebug",
                    ":createDebugCompatibleScreenManifests",
                    ":processDebugManifest",
                    ":processDebugResources",
                    ":compileDebugShaders",
                    ":transformClassesWithDexBuilderForDebug",
                    ":mergeDexDebug",
                    ":validateSigningDebug",
                    ":signingConfigWriterDebug",
                    ":transformNativeLibsWithMergeJniLibsForDebug",
                    ":transformResourcesWithMergeJavaResForDebug",
                    ":packageDebug"
                    ),
                SKIPPED to setOf(
                    ":compileDebugAidl",
                    ":generateDebugSources",
                    ":generateDebugAssets",
                    ":processDebugJavaRes",
                    ":assembleDebug"

                    ),
                FAILED to setOf()
            )

        /**
         * Tasks that should be cacheable but are not yet cacheable.
         *
         * If you add a task to this list, remember to file a bug for it. The master bug for this
         * list is Bug 69668176.
         */
        private val NOT_YET_CACHEABLE = setOf(
            ":checkDebugManifest" /* Bug 74595857 */,
            ":generateDebugBuildConfig" /* Bug 120414109 */,
            ":prepareLintJar" /* Bug 120413672 */,
            ":mainApkListPersistenceDebug" /* Bug 74595222 */,
            ":createDebugCompatibleScreenManifests" /* Bug 120412436 */,
            ":processDebugManifest" /* Bug 120411937 */,
            ":processDebugResources" /* Bug 120414113 */,
            ":compileDebugShaders" /* Bug 120413401 */,
            ":transformClassesWithDexBuilderForDebug" /* Bug 74595921 */,
            ":mergeDexDebug" /* Bug 120413559 */,
            ":signingConfigWriterDebug" /* Bug 120411939 */,
            ":transformNativeLibsWithMergeJniLibsForDebug" /* Bug 74595223 */,
            ":transformResourcesWithMergeJavaResForDebug" /* Bug 74595224 */,
            ":packageDebug" /* Bug 74595859 */
        )

        /**
         * Tasks that are never cacheable.
         */
        private val NEVER_CACHEABLE = setOf(
            ":validateSigningDebug"
        )
    }

    @get:Rule
    var projectCopy1 = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .withGradleBuildCacheDirectory(File("../$GRADLE_BUILD_CACHE_DIR"))
        .withName("projectCopy1")
        .dontOutputLogOnFailure()
        .create()

    @get:Rule
    var projectCopy2 = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .withGradleBuildCacheDirectory(File("../$GRADLE_BUILD_CACHE_DIR"))
        .withName("projectCopy2")
        .dontOutputLogOnFailure()
        .create()

    @Test
    fun testRelocatability() {
        // Build the first project
        val buildCacheDir = File(projectCopy1.testDir.parent, GRADLE_BUILD_CACHE_DIR)
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
        projectCopy1.executor().withArgument("--build-cache").run("clean", "assembleDebug")

        // Check that the build cache has been populated
        assertThat(buildCacheDir).exists()

        // Build the second project
        val result =
            projectCopy2.executor().withArgument("--build-cache").run("clean", "assembleDebug")

        // Check that the tasks' states are as expected
        assertThat(result.upToDateTasks)
            .containsExactlyElementsIn(EXPECTED_TASK_STATES[UP_TO_DATE]!!)
        assertThat(result.fromCacheTasks)
            .containsExactlyElementsIn(EXPECTED_TASK_STATES[FROM_CACHE]!!)
        assertThat(result.didWorkTasks).containsExactlyElementsIn(EXPECTED_TASK_STATES[DID_WORK]!!)
        assertThat(result.skippedTasks).containsExactlyElementsIn(EXPECTED_TASK_STATES[SKIPPED]!!)
        assertThat(result.failedTasks).containsExactlyElementsIn(EXPECTED_TASK_STATES[FAILED]!!)

        // Sanity-check that all the tasks that did work (were not cacheable) have been looked at
        // and categorized into either NOT_YET_CACHEABLE or NEVER_CACHEABLE.
        assertThat(EXPECTED_TASK_STATES[DID_WORK]).containsExactlyElementsIn(
            Sets.union(NOT_YET_CACHEABLE, NEVER_CACHEABLE)
        )

        // Clean up the cache
        FileUtils.deleteRecursivelyIfExists(buildCacheDir)
    }
}
