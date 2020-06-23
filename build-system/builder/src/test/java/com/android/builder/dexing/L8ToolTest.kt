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

package com.android.builder.dexing

import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import com.android.testutils.truth.FileSubject.assertThat

/**
 * Sanity test to make sure we can invoke L8 successfully
 */
class L8ToolTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testDexGeneration() {
        val output = tmp.newFolder().toPath()
        runL8(
            desugarJar,
            output,
            desugarConfig,
            bootClasspath,
            20,
            KeepRulesConfig(null, null),
            true
        )
        assertThat(getDexFileCount(output)).isEqualTo(1)
        assertThat(output.toFile().resolve("classes1000.dex")).exists()
    }

    private fun getDexFileCount(dir: Path): Long =
        Files.list(dir).filter { it.toString().endsWith(".dex") }.count()

    companion object {
        val bootClasspath = listOf(TestUtils.getPlatformFile("android.jar").toPath())
        val desugarJar = listOf(TestUtils.getDesugarLibJar())
        val desugarConfig = TestUtils.getDesugarLibConfigContent()
    }
}
