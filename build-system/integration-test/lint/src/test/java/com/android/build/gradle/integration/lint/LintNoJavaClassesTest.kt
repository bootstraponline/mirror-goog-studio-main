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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.testutils.truth.FileSubject.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Integration test for the bug scenario in issue https://issuetracker.google.com/152539667.
 * where a project is configured without Java sources and with BuildConfig generation
 * turned off, such that the javac output folder does not exist. This test
 * makes sure that we handle the classpath correctly such that type resolution to
 * Kotlin libraries works correctly.
 */
class LintNoJavaClassesTest {
    @Rule
    @JvmField
    var project = GradleTestProject.builder().fromTestProject("lintNoJavaClasses").create()

    @Test
    @Throws(Exception::class)
    fun checkNoMissingClass() {
        project.execute("clean", ":app:lintDebug")
        val app = project.getSubproject("app")
        val file = File(app.testDir, "lint-results.txt")
        assertThat(file).exists()
        assertThat(file).contentWithUnixLineSeparatorsIsExactly("No issues found.")
    }
}