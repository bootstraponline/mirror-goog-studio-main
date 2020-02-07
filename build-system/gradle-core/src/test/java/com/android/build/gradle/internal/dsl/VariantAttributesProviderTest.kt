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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.core.MergedFlavor
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.createProjectServices
import com.android.builder.core.ManifestAttributeSupplier
import com.android.builder.core.VariantAttributesProvider
import com.android.testutils.TestResources
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import java.io.File

@Suppress("DEPRECATION")
class VariantAttributesProviderTest {

    companion object {
        private const val PACKAGE_NAME = "com.android.tests.builder.core"
    }

    @Rule
    @JvmField
    val rule = MockitoJUnit.rule()

    @Mock
    private lateinit var manifestSupplier: ManifestAttributeSupplier

    private lateinit var manifestFile: File

    private lateinit var defaultConfig: DefaultConfig
    private lateinit var buildType: BuildType

    private var isTestVariant: Boolean = false

    private var dslServices = run {
        val projectServices = createProjectServices(
            issueReporter = FakeSyncIssueReporter(throwOnError = true)
        )
        DslServicesImpl(projectServices, DslVariableFactory(projectServices.issueReporter))
    }


    private val provider: VariantAttributesProvider
        get() = VariantAttributesProvider(
            MergedFlavor.mergeFlavors(defaultConfig, listOf(), dslServices),
            buildType,
            isTestVariant,
            manifestSupplier,
            manifestFile,
            "full.name"
        )

    @Before
    @Throws(Exception::class)
    fun before() {
        val projectServices = createProjectServices()
        defaultConfig = DefaultConfig("main",
            DslServicesImpl(projectServices, DslVariableFactory(projectServices.issueReporter))
        )
        val projectServices1 = createProjectServices()
        buildType = BuildType("debug",
            DslServicesImpl(projectServices1, DslVariableFactory(projectServices1.issueReporter))
        )
        `when`(manifestSupplier.`package`).thenReturn(PACKAGE_NAME)
        manifestFile = TestResources.getFile(this.javaClass,"AndroidManifest.xml")
    }

    @Test
    fun getPackage() {
        val packageName = provider.packageName
        assertThat(packageName).isEqualTo(PACKAGE_NAME)
    }

    @Test
    fun testPackageOverrideNone() {
        assertThat(provider.idOverride).isNull()
    }

    @Test
    fun testIdOverrideIdFromFlavor() {
        defaultConfig.applicationId = "foo.bar"
        assertThat(provider.idOverride).isEqualTo("foo.bar")
    }

    @Test
    fun testPackageOverridePackageFromFlavorWithSuffix() {
        defaultConfig.applicationId = "foo.bar"
        buildType.applicationIdSuffix = ".fortytwo"

        assertThat(provider.idOverride).isEqualTo("foo.bar.fortytwo")
    }

    @Test
    fun testPackageOverridePackageFromFlavorWithSuffix2() {
        defaultConfig.applicationId = "foo.bar"
        buildType.applicationIdSuffix = "fortytwo"

        val supplier = provider

        assertThat(supplier.idOverride).isEqualTo("foo.bar.fortytwo")
    }

    @Test
    fun testPackageOverridePackageWithSuffixOnly() {
        buildType.applicationIdSuffix = "fortytwo"

        assertThat(provider.idOverride).isEqualTo("com.android.tests.builder.core.fortytwo")
    }

    @Test
    fun testApplicationIdFromPackageName() {
        assertThat(provider.getApplicationId("")).isEqualTo(PACKAGE_NAME)
    }

    @Test
    fun testApplicationIdFromOverride() {
        defaultConfig.applicationId = "foo.bar"
        assertThat(provider.getApplicationId("")).isEqualTo("foo.bar")
    }

    @Test
    fun testApplicationIdWithTestVariant() {
        isTestVariant = true
        defaultConfig.testApplicationId = "foo.bar.test"

        assertThat(provider.getApplicationId("foo.tested")).isEqualTo("foo.bar.test")
    }

    @Test
    fun testOriginalApplicationIdWithTestVariant() {
        isTestVariant = true
        defaultConfig.testApplicationId = "foo.bar.test"

        assertThat(provider.getOriginalApplicationId("")).isEqualTo("foo.bar.test")
    }

    @Test
    fun testOriginalApplicationId() {
        assertThat(provider.getOriginalApplicationId("")).isEqualTo(PACKAGE_NAME)
    }

    @Test
    fun testGetSplit() {
        `when`(manifestSupplier.split).thenReturn("com.split")
        assertThat(provider.split).isEqualTo("com.split")
    }

    @Test
    fun testVersionNameFromFlavorWithSuffix() {
        defaultConfig.versionName = "1.0"
        buildType.versionNameSuffix = "-DEBUG"
        assertThat(provider.getVersionName()).isEqualTo("1.0-DEBUG")
    }

    @Test
    fun testVersionNameWithSuffixOnly() {
        buildType.versionNameSuffix = "-DEBUG"

        assertThat(provider.getVersionName()).isEqualTo("-DEBUG")
    }

    @Test
    fun testVersionNameFromManifest() {
        `when`(manifestSupplier.versionName).thenReturn("MANIFEST")
        assertThat(provider.getVersionName()).isEqualTo("MANIFEST")
    }

    @Test
    fun testVersionCodeFromManifest() {
        `when`(manifestSupplier.versionCode).thenReturn(34)
        assertThat(provider.getVersionCode()).isEqualTo(34)
    }

    @Test
    fun testVersionCodeFromFlavor() {
        defaultConfig.versionCode = 32

        assertThat(provider.getVersionCode()).isEqualTo(32)
    }

    @Test
    fun testInstrumentationRunnerFromManifest() {
        `when`(manifestSupplier.instrumentationRunner).thenReturn("instrumentation-manifest")
        assertThat(provider.instrumentationRunner).isEqualTo("instrumentation-manifest")
    }

    @Test
    fun testInstrumentationRunnerFromFlavor() {
        defaultConfig.testInstrumentationRunner = "instrumentation-flavor"
        assertThat(provider.instrumentationRunner).isEqualTo("instrumentation-flavor")
    }

    @Test
    fun testFunctionalTestFromManifest() {
        `when`(manifestSupplier.functionalTest).thenReturn(false)

        assertThat(provider.functionalTest).isEqualTo(false)
    }

    @Test
    fun testFunctionalTestFromFlavor() {
        defaultConfig.setTestFunctionalTest(true)
        assertThat(provider.functionalTest).isEqualTo(true)
    }

    @Test
    fun testHandleProfilingFromManifest() {
        `when`(manifestSupplier.handleProfiling).thenReturn(false)
        assertThat(provider.handleProfiling).isEqualTo(false)
    }

    @Test
    fun testHandleProfilingFromFlavor() {
        defaultConfig.setTestHandleProfiling(true)

        assertThat(provider.handleProfiling).isEqualTo(true)
    }

    @Test
    fun testTestLabelFromManifest() {
        `when`(manifestSupplier.testLabel).thenReturn("test.label")

        assertThat(provider.testLabel).isEqualTo("test.label")
    }

    @Test
    fun testExtractNativeLibsFromManifest() {
        `when`(manifestSupplier.extractNativeLibs).thenReturn(true)

        assertThat(provider.extractNativeLibs).isEqualTo(true)
    }

    @Test
    fun testTargetPackageFromManifest() {
        `when`(manifestSupplier.targetPackage).thenReturn("target.package")

        assertThat(provider.targetPackage).isEqualTo("target.package")
    }
}
