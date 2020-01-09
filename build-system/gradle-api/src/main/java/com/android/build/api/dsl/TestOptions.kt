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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/** Options for running tests. */
@Incubating
interface TestOptions<UnitTestOptionsT: UnitTestOptions> {
    /** Options for controlling unit tests execution. */
    val unitTests: UnitTestOptionsT

    /** Options for controlling unit tests execution. */
    fun unitTests(action: UnitTestOptionsT.() -> Unit)

    /** Name of the results directory. */
    var resultsDir: String?

    /** Name of the reports directory. */
    var reportDir: String?

    /**
     * Disables animations during instrumented tests you run from the command line.
     *
     * If you set this property to `true`, running instrumented tests with Gradle from the command
     * line executes `am instrument` with the `--no-window-animation` flag.
     * By default, this property is set to `false`.
     *
     * This property does not affect tests that you run using Android Studio. To learn more about
     * running tests from the command line, see
     * [Test from the Command Line](https://d.android.com/studio/test/command-line.html).
     */
    var animationsDisabled: Boolean

    /**
     * Specifies whether to use on-device test orchestration.
     *
     * If you want to [use Android Test Orchestrator](https://developer.android.com/training/testing/junit-runner.html#using-android-test-orchestrator)
     * you need to specify `"ANDROID_TEST_ORCHESTRATOR"`, as shown below.
     * By default, this property is set to `"HOST"`, which disables on-device orchestration.
     *
     * ```
     * android {
     *   testOptions {
     *     execution 'ANDROID_TEST_ORCHESTRATOR'
     *   }
     * }
     * ```
     *
     * @since 3.0.0
     */
    var execution: String
}