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

package com.android.build.gradle.internal.cxx.configure

import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.io.StringReader

class PlatformConfiguratorTest {
    private val defaultApiLevelFromDsl = AndroidVersion.DEFAULT.apiLevel
    private val expectedNdkR17MetaPlatforms = "{\n" +
            "  \"min\": 16,\n" +
            "  \"max\": 28,\n" +
            "  \"aliases\": {\n" +
            "    \"20\": 19,\n" +
            "    \"25\": 24,\n" +
            "    \"J\": 16,\n" +
            "    \"J-MR1\": 17,\n" +
            "    \"J-MR2\": 18,\n" +
            "    \"K\": 19,\n" +
            "    \"L\": 21,\n" +
            "    \"L-MR1\": 22,\n" +
            "    \"M\": 23,\n" +
            "    \"N\": 24,\n" +
            "    \"N-MR1\": 24,\n" +
            "    \"O\": 26,\n" +
            "    \"O-MR1\": 27,\n" +
            "    \"P\": 28\n" +
            "  }\n" +
            "}"

    private fun expectedNdkR17MetaPlatforms() : NdkMetaPlatforms {
        return NdkMetaPlatforms.fromReader(StringReader(expectedNdkR17MetaPlatforms))
    }

    private fun platformConfiguratorNdk16() : PlatformConfigurator {
        val root = File("./16").absoluteFile
        root.deleteRecursively()
        File(root, "platforms/android-14/arch-x86").mkdirs()
        File(root, "platforms/android-15/arch-x86").mkdirs()
        File(root, "platforms/android-16/arch-x86").mkdirs()
        File(root, "platforms/android-18/arch-x86").mkdirs()
        File(root, "platforms/android-19/arch-x86").mkdirs()
        File(root, "platforms/android-21/arch-x86").mkdirs()
        File(root, "platforms/android-22/arch-x86").mkdirs()
        File(root, "platforms/android-23/arch-x86").mkdirs()
        File(root, "platforms/android-24/arch-x86").mkdirs()
        File(root, "platforms/android-26/arch-x86").mkdirs()
        File(root, "platforms/android-27/arch-x86").mkdirs()
        return PlatformConfigurator(root)
    }

    private fun platformConfiguratorNdk17() : PlatformConfigurator {
        val root = File("./17").absoluteFile
        root.deleteRecursively()
        File(root, "platforms/android-14/arch-x86").mkdirs()
        File(root, "platforms/android-15/arch-x86").mkdirs()
        File(root, "platforms/android-16/arch-x86").mkdirs()
        File(root, "platforms/android-18/arch-x86").mkdirs()
        File(root, "platforms/android-19/arch-x86").mkdirs()
        File(root, "platforms/android-21/arch-x86").mkdirs()
        File(root, "platforms/android-22/arch-x86").mkdirs()
        File(root, "platforms/android-23/arch-x86").mkdirs()
        File(root, "platforms/android-24/arch-x86").mkdirs()
        File(root, "platforms/android-26/arch-x86").mkdirs()
        File(root, "platforms/android-27/arch-x86").mkdirs()
        File(root, "platforms/android-28/arch-x86").mkdirs()
        return PlatformConfigurator(root)
    }

    private fun platformConfiguratorNdkInvalid() : PlatformConfigurator {
        val root = File("./invalid").absoluteFile
        root.deleteRecursively()
        return PlatformConfigurator(root)
    }

    private fun platformConfiguratorNdk17ButHasWeirdAndroidFolder() : PlatformConfigurator {
        val root = File("./17-weird").absoluteFile
        root.deleteRecursively()
        File(root, "platforms/android-14/arch-x86").mkdirs()
        File(root, "platforms/android-15/arch-x86").mkdirs()
        File(root, "platforms/android-16/arch-x86").mkdirs()
        File(root, "platforms/android-18/arch-x86").mkdirs()
        File(root, "platforms/android-19/arch-x86").mkdirs()
        File(root, "platforms/android-21/arch-x86").mkdirs()
        File(root, "platforms/android-22/arch-x86").mkdirs()
        File(root, "platforms/android-23/arch-x86").mkdirs()
        File(root, "platforms/android-24/arch-x86").mkdirs()
        File(root, "platforms/android-26/arch-x86").mkdirs()
        File(root, "platforms/android-27/arch-x86").mkdirs()
        File(root, "platforms/android-28/arch-x86").mkdirs()
        File(root, "platforms/android-bob/arch-x86").mkdirs()
        return PlatformConfigurator(root)
    }

    private fun platformConfiguratorMissingSomePlatforms() : PlatformConfigurator {
        val root = File("./17-incomplete").absoluteFile
        root.deleteRecursively()
        File(root, "platforms/android-19/arch-x86").mkdirs()
        File(root, "platforms/android-21/arch-x86").mkdirs()
        File(root, "platforms/android-24/arch-x86").mkdirs()
        return PlatformConfigurator(root)
    }

    data class FindSuitablePlatformVersionInteraction(
        val version : Int,
        val messages : List<String>)

    private fun findSuitablePlatformVersion(
        platformConfigurator: PlatformConfigurator,
        abiName: String,
        minSdkVersion: Int?,
        codeName: String?,
        ndkMetaPlatforms: NdkMetaPlatforms? = null) : FindSuitablePlatformVersionInteraction {
        val messages = mutableListOf<String>()
        val androidVersion = if (minSdkVersion == null && codeName == null) {
            null
        } else {
            AndroidVersion(minSdkVersion ?: 0, codeName)
        }
        val version = platformConfigurator.findSuitablePlatformVersionLogged(
            abiName,
            androidVersion,
            ndkMetaPlatforms,
            "debug",
            error = { message -> messages += "ERROR: $message"},
            warn = { message -> messages += "WARN: $message"},
            info = { message -> messages += "INFO: $message"})
        return FindSuitablePlatformVersionInteraction(
            version = version,
            messages = messages
        )
    }

    @Test
    fun testPlatformJustFoundNdk16() {
        val configurator = platformConfiguratorNdk16()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            27,
            null)
        assertThat(platform.version).isEqualTo(27)
        assertThat(platform.messages).hasSize(0)
    }

    @Test
    fun testNdkPlatformFallbackNdk16() {
        val configurator = platformConfiguratorNdk16()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            28,
            null)
        assertThat(platform.version).isEqualTo(27)
        assertThat(platform.messages).containsExactly("ERROR: Platform version " +
                "'28' is beyond '27', the maximum API level supported by this NDK.")
    }

    @Test
    fun testPlatformTooLowClimbToMinimumNdk16() {
        val configurator = platformConfiguratorNdk16()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            13,
            null)
        assertThat(platform.version).isEqualTo(14)
        assertThat(platform.messages).hasSize(0)
    }

    @Test
    fun testPlatformJustFoundNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            28,
            null)
        assertThat(platform.version).isEqualTo(28)
        assertThat(platform.messages).hasSize(0)
    }

    @Test
    fun testNdkPlatformFallbackNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            29,
            null)
        assertThat(platform.version).isEqualTo(28)
        assertThat(platform.messages).containsExactly("ERROR: Platform version '29' " +
                "is beyond '28', the maximum API level supported by this NDK.")
    }

    @Test
    fun testPlatformTooLowClimbToMinimumNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            13,
            null)
        assertThat(platform.version).isEqualTo(14)
        assertThat(platform.messages).hasSize(0)
    }

    @Test
    fun testPlatformFoundByCodeNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "P")
        assertThat(platform.version).isEqualTo(28)
        assertThat(platform.messages).containsExactly(
            "INFO: Version minSdkVersion='P' is mapped to '28'.")
    }

    @Test
    fun testAliasInMinSdkVersionPositionNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            20,
            null)
        assertThat(platform.messages).containsExactly("INFO: Version minSdkVersion='20' " +
                "is mapped to '19'.")
        assertThat(platform.version).isEqualTo(19)
    }

    @Test
    fun testPlatformUnknownMrCodeNdk17() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "O-MR2" // <- doesn't exist
           )
        assertThat(platform.version).isEqualTo(28)
        assertThat(platform.messages).containsExactly("ERROR: API codeName 'O-MR2' " +
                "is not recognized.")
    }

    // ------------------
    @Test
    fun testNoVersionSpecifiedNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            null,
            expectedNdkR17MetaPlatforms())
        assertThat(platform.version).isEqualTo(16)
        assertThat(platform.messages).containsExactly("INFO: Neither codeName nor " +
                "minSdkVersion specified. Using minimum platform version for 'x86'.")
    }

    @Test
    fun testPlatformJustFoundNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            28,
            null,
            expectedNdkR17MetaPlatforms())
        assertThat(platform.version).isEqualTo(28)
        assertThat(platform.messages).hasSize(0)
    }

    @Test
    fun testNdkPlatformFallbackNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            29,
            null,
            expectedNdkR17MetaPlatforms())
        assertThat(platform.version).isEqualTo(28)
        assertThat(platform.messages).containsExactly("ERROR: Platform version '29' " +
                "is beyond '28', the maximum API level supported by this NDK.")
    }

    @Test
    fun testPlatformTooLowClimbToMinimumNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            13,
            null,
            expectedNdkR17MetaPlatforms())
        assertThat(platform.version).isEqualTo(16)
        assertThat(platform.messages).hasSize(0)
    }

    @Test
    fun testPlatformFoundByCodeNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "P",
            expectedNdkR17MetaPlatforms())
        assertThat(platform.version).isEqualTo(28)
        assertThat(platform.messages).containsExactly(
            "INFO: Version minSdkVersion='P' is mapped to '28'.")
    }

    @Test
    fun testAliasInMinSdkVersionPositionNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            20,
            null,
            expectedNdkR17MetaPlatforms())
        assertThat(platform.messages).containsExactly("INFO: Version minSdkVersion='20' " +
                "is mapped to '19'.")
        assertThat(platform.version).isEqualTo(19)
    }

    @Test
    fun testPlatformMrCodeNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "O-MR1",
            expectedNdkR17MetaPlatforms())
        assertThat(platform.version).isEqualTo(27)
        assertThat(platform.messages).containsExactly(
            "INFO: Version minSdkVersion='O-MR1' is mapped to '27'.")
    }

    @Test
    fun testPlatformUnknownMrCodeNdk17PlatformsMeta() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "O-MR2", // <- doesn't exist
            expectedNdkR17MetaPlatforms())
        assertThat(platform.version).isEqualTo(28)
        assertThat(platform.messages).containsExactly("ERROR: API codeName 'O-MR2' " +
                "is not recognized.")
    }

    // ------------------

    @Test
    fun testWeirdABI() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "bob",
            13,
            null)
        assertThat(platform.version).isEqualTo(AndroidVersion.MIN_RECOMMENDED_API)
        assertThat(platform.messages).containsExactly("ERROR: Specified abi='bob' " +
                "is not recognized.")
    }

    @Test
    fun testBothMinSdkAndCodeNameAgree() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            28,
            "P")
        assertThat(platform.version).isEqualTo(28)
        assertThat(platform.messages).containsExactly(
            "INFO: Version minSdkVersion='P' is mapped to '28'.",
            "WARN: Both codeName and minSdkVersion specified. They agree but only " +
                    "one should be specified.")
    }

    @Test
    fun testBothMinSdkAndCodeNameDisagree() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            27,
            "P")
        assertThat(platform.messages).containsExactly(
            "INFO: Version minSdkVersion='P' is mapped to '28'.",
            "ERROR: Disagreement between codeName='P' and minSdkVersion='27'. " +
                    "Only one should be specified.")
        assertThat(platform.version).isEqualTo(27)
    }

    @Test
    fun testMissingNDKFolder() {
        val configurator = platformConfiguratorNdkInvalid()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            27,
            "P")
        assertThat(platform.version).isEqualTo(AndroidVersion.MIN_RECOMMENDED_API)
        val message = platform.messages.first()
        assertThat(message).contains("does not contain 'platforms'.")
    }

    @Test
    fun testPlatformConfiguratorNdk17ButHasWeirdAndroidFolder() {
        val configurator = platformConfiguratorNdk17ButHasWeirdAndroidFolder()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            20,
            null)
        assertThat(platform.version).isEqualTo(19)
        assertThat(platform.messages).containsExactly("INFO: Version minSdkVersion='20' " +
                "is mapped to '19'.")
    }

    @Test
    fun testVeryOldCodenameGetsPromoted() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "J")
        assertThat(platform.version).isEqualTo(16)
        assertThat(platform.messages).containsExactly("INFO: Version " +
                "minSdkVersion='J' is mapped to '16'.")
    }

    @Test
    fun testNotYetKnownCodenameFallsBackToMaximumForNdk() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            defaultApiLevelFromDsl,
            "Z")
        assertThat(platform.version).isEqualTo(28)
        assertThat(platform.messages).containsExactly("ERROR: API codeName 'Z' is not recognized.")
    }

    @Test
    fun testEmptyVersionInfoInBuildGradle() {
        val configurator = platformConfiguratorNdk17()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            null,
            null)
        assertThat(platform.version).isEqualTo(22)
        assertThat(platform.messages).isEmpty()
    }

    @Test
    fun testAgainstIncompletePlatformsFolder() {
        val configurator = platformConfiguratorMissingSomePlatforms()
        val platform = findSuitablePlatformVersion(
            configurator,
            "x86",
            null,
            null)
        assertThat(platform.version).isEqualTo(19)
        assertThat(platform.messages).containsExactly("ERROR: Expected platform " +
                "folder platforms/android-22, using platform API 19 instead.")
    }
}