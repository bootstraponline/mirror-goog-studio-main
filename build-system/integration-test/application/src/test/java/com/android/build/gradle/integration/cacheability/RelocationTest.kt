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

import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * This is a very simple test that attempts to make sure we don't regress with regards to what
 * things can be cached across machines.
 *
 *
 * See https://guides.gradle.org/using-build-cache/ for information on what Gradle's build cache
 * is and how to use it.
 */
@RunWith(JUnit4::class)
class RelocationTest {

    @Rule
    var projectCopy1 = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .dontOutputLogOnFailure()
        .withName("projectCopy1")
        .create()

    @Rule
    var projectCopy2 = GradleTestProject.builder()
        .fromTestApp(HelloWorldApp.forPlugin("com.android.application"))
        .dontOutputLogOnFailure()
        .withName("projectCopy2")
        .create()

    /**
     * Each GradleTestProject is exactly the same, and the "withName" method ensures that they live
     * in separate directories already so there's no need to move them. This test simply runs a
     * clean build on both and asserts that there are no tasks that did work in the second build
     * unless we have specifically listed them in `KNOWN_PROBLEMS` above.
     *
     *
     * If you've landed here because you have inadvertently caused a test failure, don't just add
     * your task to the `KNOWN_PROBLEMS` list. Spend time figuring out how you broke the
     * relocatability of the task and see if you can fix it.
     */
    @Test
    @Throws(Exception::class)
    fun testRelocatability() {
        // Make sure the projects are in two separate directories.
        assertThat(projectCopy1.mainSrcDir.absolutePath)
            .isNotEqualTo(projectCopy2.mainSrcDir.absolutePath)

        projectCopy1.executor().withArgument("--build-cache").run("clean", "assembleDebug")
        val result =
            projectCopy2.executor().withArgument("--build-cache").run("clean", "assembleDebug")
        val difference = Sets.difference(
            KNOWN_PROBLEMS,
            Sets.difference(result.notUpToDateTasks, result.skippedTasks)
        )

        assertThat(difference).isEmpty()
    }

    companion object {
        /**
         * The vast majority of our tasks should be relocatable and cacheable. This means that we are
         * generating Gradle cache keys that do not rely on constantly-changing inputs or overly
         * sensitive paths.
         *
         *
         * There are some legitimate exceptions:
         *
         *
         * - Lifecycle tasks. These tasks exists to give users a "hook" in to different parts of the
         * build lifecycle and perform no action themselves. We don't add them in to the list below
         * because we can identify them programmatically and filter them out.
         *
         *
         * - Tasks that have no output. A good example in our code base is the "validateSigning"
         * task. This task performs some action but does not itself have an output.
         *
         *
         * - Tasks that rely on external, unpredictable inputs. As far as I know, we don't have any
         * of these and we should strive to keep it that way.
         *
         *
         * If your task does not fit in to any of the above categories, it should not be in the
         * following list. Anything in the following list
         */
        private val KNOWN_PROBLEMS = ImmutableSet.builder<String>()
            // Legitimately uncacheable or unrelocatable tasks.
            .add(":validateSigningDebug")

            // Tasks we need to work on to achieve relocatability. Anything in this list
            // should not be here, and a bug should exist for each to work on removing them.
            .add(":checkDebugManifest")
            .add(":mainApkListPersistenceDebug")
            .add(":transformClassesWithDexBuilderForDebug")
            .add(":transformNativeLibsWithMergeJniLibsForDebug")
            .add(":transformResourcesWithMergeJavaResForDebug")
            .add(":packageDebug")
            .build()
    }
}
