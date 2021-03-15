/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.builder.internal

import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class UnitTestManifestGeneratorTest {

    @get: Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun testManifestGeneration() {
        val outputFile = temporaryFolder.newFile("manifest.xml")
        UnitTestManifestGenerator(
            outputFile,
            "com.foo.bar.test",
            "21",
            "21",
            "com.foo.bar",
            "unitTestRunner"
        ).generate()

        Truth.assertThat(outputFile.readText()).isEqualTo("""
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="com.foo.bar.test">

        <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="21" />

        <application android:debuggable="true" >
            <uses-library android:name="android.test.runner" />
        </application>

        <instrumentation android:name="unitTestRunner"
                         android:targetPackage="com.foo.bar"
                         android:label="Tests for com.foo.bar"/>

    </manifest>

        """.trimIndent())
    }
}
