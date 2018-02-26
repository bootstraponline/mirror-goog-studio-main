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

package com.android.build.gradle.integration.performance

import com.google.common.base.Throwables
import com.google.wireless.android.sdk.stats.GradleBuildProfile
import java.nio.file.Path
import java.time.Duration

data class BenchmarkResult(
        val benchmark: Benchmark,
        val totalDuration: Duration,
        val recordedDuration: Duration,
        val profile: GradleBuildProfile?,
        val exception: Exception?
) {
    override fun toString(): String {
        return """
            scenario: ${benchmark.scenario.name}
            benchmark: ${benchmark.benchmark.name}
            benchmarkMode: ${benchmark.benchmarkMode.name}
            recordedDuration: $recordedDuration
            totalDuration: $totalDuration
            testDir: ${benchmark.projectDir()}
            hasProfile: ${if (profile != null) "yes" else "no" }
            exception: ${if (exception != null) Throwables.getRootCause(exception) else "none"}
        """.trimIndent()
    }
}
