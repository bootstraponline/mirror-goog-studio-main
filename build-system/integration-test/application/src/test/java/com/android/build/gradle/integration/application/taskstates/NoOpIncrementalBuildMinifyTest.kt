/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.application.taskstates

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.DID_WORK
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.SKIPPED
import com.android.build.gradle.integration.common.truth.TaskStateList.ExecutionState.UP_TO_DATE
import com.android.build.gradle.integration.common.utils.TaskStateAssertionHelper
import com.android.build.gradle.options.BooleanOption
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Similar to [NoOpIncrementalBuildTaskStatesTest], but targeting the release build type with
 * `minifyEnabled=true` to verify a different set of tasks.
 */
class NoOpIncrementalBuildMinifyTest {

    companion object {

        private val EXPECTED_TASK_STATES = mapOf(
            UP_TO_DATE to setOf(
                ":assembleDebugAndroidTest",
                ":assembleRelease",
                ":bundleDebugClassesToCompileJar",
                ":bundleReleaseClassesToCompileJar",
                ":bundleReleaseClassesToRuntimeJar",
                ":checkDebugAarMetadata",
                ":checkDebugAndroidTestAarMetadata",
                ":checkDebugAndroidTestDuplicateClasses",
                ":checkReleaseAarMetadata",
                ":checkReleaseDuplicateClasses",
                ":collectReleaseDependencies",
                ":compileDebugAndroidTestJavaWithJavac",
                ":compileDebugJavaWithJavac",
                ":compileReleaseArtProfile",
                ":compileReleaseJavaWithJavac",
                ":compressDebugAndroidTestAssets",
                ":compressReleaseAssets",
                ":createDebugAndroidTestApkListingFileRedirect",
                ":createReleaseApkListingFileRedirect",
                ":createDebugCompatibleScreenManifests",
                ":createReleaseCompatibleScreenManifests",
                ":desugarDebugAndroidTestFileDependencies",
                ":dexBuilderDebugAndroidTest",
                ":extractDeepLinksDebug",
                ":extractDeepLinksRelease",
                ":extractProguardFiles",
                ":generateDebugAndroidTestAssets",
                ":generateDebugAndroidTestBuildConfig",
                ":generateDebugAndroidTestResValues",
                ":generateDebugAndroidTestResources",
                ":generateDebugBuildConfig",
                ":generateDebugResValues",
                ":generateDebugResources",
                ":generateReleaseAssets",
                ":generateReleaseBuildConfig",
                ":generateReleaseResValues",
                ":generateReleaseResources",
                ":javaPreCompileDebug",
                ":javaPreCompileDebugAndroidTest",
                ":javaPreCompileRelease",
                ":javaPreCompileReleaseUnitTest",
                ":lintAnalyzeDebug",
                ":lintReportDebug",
                ":mapDebugAndroidTestSourceSetPaths",
                ":mapDebugSourceSetPaths",
                ":mergeDebugAndroidTestAssets",
                ":mergeDebugAndroidTestJavaResource",
                ":mergeDebugAndroidTestJniLibFolders",
                ":mergeDebugAndroidTestResources",
                ":mergeDebugAndroidTestShaders",
                ":mergeDebugResources",
                ":mergeDexDebugAndroidTest",
                ":mergeExtDexDebugAndroidTest",
                ":mergeReleaseArtProfile",
                ":mergeReleaseAssets",
                ":mergeReleaseGeneratedProguardFiles",
                ":mergeReleaseJavaResource",
                ":mergeReleaseJniLibFolders",
                ":mergeReleaseResources",
                ":mergeReleaseShaders",
                ":mapReleaseSourceSetPaths",
                ":minifyReleaseWithR8",
                ":optimizeReleaseResources",
                ":packageDebugAndroidTest",
                ":packageRelease",
                ":preBuild",
                ":preDebugBuild",
                ":preReleaseBuild",
                ":preReleaseUnitTestBuild",
                ":processDebugAndroidTestManifest",
                ":processDebugAndroidTestResources",
                ":processDebugMainManifest",
                ":processDebugManifest",
                ":processDebugManifestForPackage",
                ":processDebugResources",
                ":processReleaseMainManifest",
                ":processReleaseManifest",
                ":processReleaseManifestForPackage",
                ":processReleaseResources",
                ":sdkReleaseDependencyData",
                ":validateSigningDebugAndroidTest",
                ":writeDebugAndroidTestSigningConfigVersions",
                ":writeReleaseAppMetadata",
                ":writeReleaseSigningConfigVersions"
            ),
            SKIPPED to setOf(
                ":compileDebugAidl",
                ":compileDebugAndroidTestAidl",
                ":compileDebugAndroidTestShaders",
                ":compileReleaseAidl",
                ":compileReleaseShaders",
                ":compileReleaseUnitTestJavaWithJavac",
                ":extractReleaseNativeSymbolTables",
                ":lint",
                ":mergeDebugAndroidTestNativeLibs",
                ":mergeReleaseNativeDebugMetadata",
                ":mergeReleaseNativeLibs",
                ":preDebugAndroidTestBuild",
                ":processDebugAndroidTestJavaRes",
                ":processReleaseJavaRes",
                ":processReleaseUnitTestJavaRes",
                ":stripReleaseDebugSymbols",
                ":testReleaseUnitTest",
            ),
            /*
             * If you add a task to this DID_WORK set, please add an explanation and/or file a bug.
             */
            DID_WORK to setOf(
                // lintDebug is intentionally not up-to-date because it must print the lint issues
                // stdout or stderr if necessary.
                ":lintDebug",
            )
        )
    }

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .create()

    @Before
    fun setUp() {
        project.buildFile.appendText(
            """
            android.buildTypes {
                release { minifyEnabled true }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `check task states`() {
        val result = project.executor().run {
            val tasks =
                listOf("assembleRelease", "testReleaseUnitTest", "assembleDebugAndroidTest", "lint")
            run(tasks)
            run(tasks)
        }
        TaskStateAssertionHelper(result.taskStates)
            .assertTaskStatesByGroups(EXPECTED_TASK_STATES, exhaustive = true)
    }
}
