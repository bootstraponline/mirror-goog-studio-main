/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.integration.instantexecution

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.LoggingLevel
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BasicInstantExecutionTest {

    private val app = MinimalSubProject.app("com.app")
    private val lib = MinimalSubProject.lib("com.lib")

    @JvmField
    @Rule
    var project = GradleTestProject.builder()
        .setTargetGradleVersion("6.4-20200312124006+0000")
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .subproject(":lib", lib)
                .subproject(
                    ":test",
                    MinimalSubProject
                        .test("com.test")
                        .appendToBuild("android.targetProjectPath ':app'")
                )
                .build()
        ).create()

    @Before
    fun setUp() {
        project.testDir.resolve(".instant-execution-state").deleteRecursively()

        // Disable lint because of http://b/146208910
        listOf("app", "lib").forEach {
            project.getSubproject(it).buildFile.appendText("""

            android {
                lintOptions {
                    checkReleaseBuilds false
                }
            }
        """.trimIndent())
        }
    }

    @Test
    fun testUpToDate() {
        executor().run("assemble")
        assertThat(project.testDir.resolve(".instant-execution-state")).isDirectory()
        val result = executor().run("assemble")
        Truth.assertThat(result.didWorkTasks).isEmpty()
    }

    @Test
    fun testCleanBuild() {
        executor().with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false).run("assemble")
        executor().run("clean")
        executor().run("assemble")
    }

    @Test
    fun testWhenInvokedFromTheIde() {
        executor()
            .with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
            .with(BooleanOption.INCLUDE_DEPENDENCY_INFO_IN_APKS, false)
            .run("assemble")

        assertThat(project.testDir.resolve(".instant-execution-state")).isDirectory()
        executor().run("clean")
        executor()
            .with(BooleanOption.IDE_INVOKED_FROM_IDE, true)
            .run("assemble")
    }

    /** Regression test for b/146659187. */
    @Test
    fun testWithJniMerging() {
        project.getSubproject("app").file("src/main/jniLibs/subDir/empty.so").also {
            it.parentFile.mkdirs()
            it.createNewFile()
        }
        executor().run(":app:mergeDebugJniLibFolders")
        executor().run("clean")
        executor().run(":app:mergeDebugJniLibFolders")
    }

    private fun executor(): GradleTaskExecutor =
        project.executor()
            .withLoggingLevel(LoggingLevel.LIFECYCLE)
            .withArgument("-Dorg.gradle.unsafe.instant-execution=true")
            .withArgument("-Dorg.gradle.unsafe.instant-execution.fail-on-problems=true")
            .withArgument("-Dorg.gradle.unsafe.instant-execution.max-problems=0")
}