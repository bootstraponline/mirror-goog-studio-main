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

package com.android.build.gradle.internal.core

import com.android.build.api.component.ComponentIdentity
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.manifest.ManifestData
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createVariantPropertiesApiServices
import com.android.build.gradle.internal.variant.Container
import com.android.build.gradle.internal.variant.ContainerImpl
import com.android.builder.core.BuilderConstants
import com.android.builder.core.VariantTypeImpl
import com.android.testutils.AbstractBuildGivenBuildExpectTest
import org.gradle.api.provider.Provider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.mockito.Mockito
import java.io.File
import java.util.function.BooleanSupplier

class VariantDslInfoTest2 :
    AbstractBuildGivenBuildExpectTest<
            VariantDslInfoTest2.GivenData,
            VariantDslInfoTest2.ResultData>() {

    @Test
    fun `versionCode from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData {  }

            defaultConfig {
                versionCode = 12
            }
        }

        expect {
            versionCode = 12
        }
    }

    @Test
    fun `versionCode from manifest`() {
        given {
            manifestData {
                versionCode = 12
            }
        }

        expect {
            versionCode = 12
        }
    }

    @Test
    fun `versionCode defaultConfig overrides manifest`() {
        given {
            manifestData {
                versionCode = 12
            }

            defaultConfig {
                versionCode = 13
            }
        }

        expect {
            versionCode = 13
        }
    }

    @Test
    fun `versionCode from flavor overrides all`() {
        given {
            manifestData {
                versionCode = 12
            }

            defaultConfig {
                versionCode = 13
            }
            productFlavors {
                create("higherPriority") {
                    versionCode = 20
                }
                create("lowerPriority") {
                    versionCode = 14
                }
            }
        }

        expect {
            versionCode = 20
        }
    }

    @Test
    fun `versionName from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }

            defaultConfig {
                versionName = "foo"
            }
        }

        expect {
            versionName = "foo"
        }
    }

    @Test
    fun `versionName from manifest`() {
        given {
            manifestData {
                versionName = "foo"
            }
        }

        expect {
            versionName = "foo"
        }
    }

    @Test
    fun `versionName defaultConfig overrides manifest`() {
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionName = "bar"
            }
        }

        expect {
            versionName = "bar"
        }
    }

    @Test
    fun `versionName from flavor overrides all`() {
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionName = "bar3"
            }
            productFlavors {
                create("higherPriority") {
                    versionName = "bar1"
                }
                create("lowerPriority") {
                    versionName = "bar2"
                }
            }
        }

        expect {
            versionName = "bar1"
        }
    }

    @Test
    fun `versionName from manifest with suffix from defaultConfig`() {
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionNameSuffix = "-bar"
            }
        }

        expect {
            versionName = "foo-bar"
        }
    }

    @Test
    fun `versionName from manifest with full suffix`() {
        given {
            manifestData {
                versionName = "foo"
            }

            defaultConfig {
                versionNameSuffix = "-bar1"
            }
            productFlavors {
                create("higherPriority") {
                    versionNameSuffix = "-bar3"
                }
                create("lowerPriority") {
                    versionNameSuffix = "-bar2"
                }
            }

            buildType {
                versionNameSuffix = "-bar4"
            }
        }

        expect {
            versionName = "foo-bar1-bar3-bar2-bar4"
        }
    }

    @Test
    fun `instrumentationRunner defaults`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            instrumentationRunner = "android.test.InstrumentationTestRunner"
        }
    }

    @Test
    fun `instrumentationRunner defaults with legacy multidex`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                minSdkVersion(20)
                multiDexEnabled = true
            }
        }

        expect {
            instrumentationRunner = "com.android.test.runner.MultiDexTestRunner"
        }
    }

    @Test
    fun `instrumentationRunner on non test`() {
        given {
            // no specific manifest info
            manifestData { }
        }

        // provide a custom convert action to only call VariantDslInfo.instrumentationRunner
        // as it's not normally called for non-test variant type.
        convertToResult {
            instrumentationRunner = it.instrumentationRunner.orNull
        }

        exceptionRule.expect(RuntimeException::class.java)
        exceptionRule.expectMessage("instrumentationRunner is not available to non-test variant")

        expect {
            // value is not relevant here since exception will be thrown
        }
    }

    @Test
    fun `instrumentationRunner from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testInstrumentationRunner = "foo"
            }
        }

        expect {
            instrumentationRunner = "foo"
        }
    }

    @Test
    fun `instrumentationRunner from manifest`() {
        given {
            manifestData {
                instrumentationRunner = "foo"
            }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            instrumentationRunner = "foo"
        }
    }

    @Test
    fun `instrumentationRunner defaultConfig overrides manifest`() {
        given {
            manifestData {
                instrumentationRunner = "foo"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testInstrumentationRunner = "bar"
            }
        }

        expect {
            instrumentationRunner = "bar"
        }
    }

    @Test
    fun `instrumentationRunner from flavor overrides all`() {
        given {
            manifestData {
                instrumentationRunner = "foo"
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testInstrumentationRunner = "bar3"
            }
            productFlavors {
                create("higherPriority") {
                    testInstrumentationRunner = "bar1"
                }
                create("lowerPriority") {
                    testInstrumentationRunner = "bar2"
                }
            }
        }

        expect {
            instrumentationRunner = "bar1"
        }
    }

    @Test
    fun `handleProfiling defaults`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            handleProfiling = false
        }
    }

    @Test
    fun `handleProfiling on non test`() {
        given {
            // no specific manifest info
            manifestData { }
        }

        // provide a custom convert action to call VariantDslInfo.handleProfiling
        // even though this is not a test VariantType
        convertToResult {
            handleProfiling = it.handleProfiling.orNull
        }

        exceptionRule.expect(RuntimeException::class.java)
        exceptionRule.expectMessage("handleProfiling is not available to non-test variant")

        expect {
            // value is not relevant here since exception will be thrown
        }
    }

    @Test
    fun `handleProfiling from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testHandleProfiling = true
            }
        }

        expect {
            handleProfiling = true
        }
    }

    @Test
    fun `handleProfiling from manifest`() {
        given {
            manifestData {
                handleProfiling = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            handleProfiling = true
        }
    }

    @Test
    fun `handleProfiling defaultConfig overrides manifest`() {
        given {
            manifestData {
                handleProfiling = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testHandleProfiling = false
            }
        }

        expect {
            handleProfiling = false
        }
    }

    @Test
    fun `handleProfiling from flavor overrides all`() {
        given {
            manifestData {
                handleProfiling = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testHandleProfiling = true
            }
            productFlavors {
                create("higherPriority") {
                    testHandleProfiling = false
                }
                create("lowerPriority") {
                    testHandleProfiling = false
                }
            }
        }

        expect {
            handleProfiling = false
        }
    }

    @Test
    fun `functionalTest defaults`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            functionalTest = false
        }
    }

    @Test
    fun `functionalTest on non test`() {
        given {
            // no specific manifest info
            manifestData { }
        }

        // provide a custom convert action to call VariantDslInfo.functionalTest
        // even though this is not a test VariantType
        convertToResult {
            functionalTest = it.functionalTest.orNull
        }

        exceptionRule.expect(RuntimeException::class.java)
        exceptionRule.expectMessage("functionalTest is not available to non-test variant")

        expect {
            // value is not relevant here since exception will be thrown
        }
    }

    @Test
    fun `functionalTest from defaultConfig`() {
        given {
            // no specific manifest info
            manifestData { }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testFunctionalTest = true
            }
        }

        expect {
            functionalTest = true
        }
    }

    @Test
    fun `functionalTest from manifest`() {
        given {
            manifestData {
                functionalTest = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST
        }

        expect {
            functionalTest = true
        }
    }

    @Test
    fun `functionalTest defaultConfig overrides manifest`() {
        given {
            manifestData {
                functionalTest = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testFunctionalTest = false
            }
        }

        expect {
            functionalTest = false
        }
    }

    @Test
    fun `functionalTest from flavor overrides all`() {
        given {
            manifestData {
                functionalTest = true
            }

            variantType = VariantTypeImpl.ANDROID_TEST

            defaultConfig {
                testFunctionalTest = true
            }
            productFlavors {
                create("higherPriority") {
                    testFunctionalTest = false
                }
                create("lowerPriority") {
                    testFunctionalTest = false
                }
            }
        }

        expect {
            functionalTest = false
        }
    }

    // ---------------------------------------------------------------------------------------------

    @get:Rule
    val exceptionRule : ExpectedException = ExpectedException.none()

    private val projectServices = createProjectServices()
    private val services = createVariantPropertiesApiServices(projectServices)
    private val dslServices: DslServices = createDslServices(projectServices)

    override fun instantiateGiven() = GivenData(dslServices)
    override fun instantiateResult() = ResultData()

    override fun defaultWhen(given: GivenData): ResultData? {
        val componentIdentity = Mockito.mock(ComponentIdentity::class.java)
        Mockito.`when`(componentIdentity.name).thenReturn("compIdName")
        val variantDslInfo = VariantDslInfoImpl(
            componentIdentity = componentIdentity,
            variantType = given.variantType,
            defaultConfig = given.defaultConfig,
            manifestFile = File("/path/for/old/manifest"),
            buildTypeObj = given.buildType,
            productFlavorList = given.flavors,
            signingConfigOverride = null,
            manifestAttributeSupplier = null,
            testedVariantImpl = null,
            dataProvider = DirectManifestDataProvider(given.manifestData, projectServices),
            dslServices = dslServices,
            services = services,
            isInExecutionPhase = BooleanSupplier { false }
        )

        return instantiateResult().also {
            if (convertAction != null) {
                convertAction?.invoke(it, variantDslInfo)
            } else {
                it.versionCode = variantDslInfo.versionCode.orNull
                it.versionName = variantDslInfo.versionName.orNull
                // only query these if this is not a test.
                if (given.variantType.isForTesting) {
                    it.instrumentationRunner = variantDslInfo.instrumentationRunner.orNull
                    it.handleProfiling = variantDslInfo.handleProfiling.get()
                    it.functionalTest = variantDslInfo.functionalTest.get()
                }
            }
        }
    }

    override fun initResultDefaults(given: GivenData, result: ResultData) {
        // if the variant type is a test, then make sure that the result is initialized
        // with the right defaults.
        if (given.variantType.isForTesting) {
            result.instrumentationRunner = "android.test.InstrumentationTestRunner" // DEFAULT_TEST_RUNNER
            result.handleProfiling = false // DEFAULT_HANDLE_PROFILING
            result.functionalTest = false //DEFAULT_FUNCTIONAL_TEST
        }
    }

    /** optional conversion action from variantDslInfo to result Builder. */
    private var convertAction: (ResultData.(variantInfo: VariantDslInfo) -> Unit)? = null

    /**
     * registers a custom conversion from variantDslInfo to ResultBuilder.
     * This avoid having to use when {} which requires implementing all that defaultWhen()
     * does.
     */
    private fun convertToResult(action: ResultData.(variantInfo: VariantDslInfo) -> Unit) {
        convertAction = action
    }

    class GivenData(private val dslServices: DslServices) {
        /** the manifest data that represents values coming from the manifest file */
        val manifestData = ManifestData()

        /** Configures the manifest data. */
        fun manifestData(action: ManifestData.() -> Unit) {
            action(manifestData)
        }

        /** Variant type for the test */
        var variantType = VariantTypeImpl.BASE_APK

        /** default Config values */
        val defaultConfig: DefaultConfig = DefaultConfig(BuilderConstants.MAIN, dslServices)

        /** configures the default config */
        fun defaultConfig(action: DefaultConfig.() -> Unit) {
            action(defaultConfig)
        }

        val buildType: BuildType = BuildType("Build-Type", dslServices)

        fun buildType(action: BuildType.() -> Unit) {
            action(buildType)
        }

        private val productFlavors: ContainerImpl<ProductFlavor> = ContainerImpl { name -> ProductFlavor(name, dslServices) }
        val flavors: List<ProductFlavor>
            get() = productFlavors.values.toList()

        /**
         * add/configures flavors. The earlier items have higher priority over the later ones.
         */
        fun productFlavors(action: Container<ProductFlavor>.() -> Unit) {
            action(productFlavors)
        }
    }

    data class ResultData(
        var versionCode: Int? = null,
        var versionName: String? = null,
        var instrumentationRunner: String? = null,
        var handleProfiling: Boolean? = null,
        var functionalTest: Boolean? = null
    )

    /**
     * Use the ManifestData provider in the given as a ManifestDataProvider in order to
     * instantiate the ManifestBackedVariantValues object.
     */
    class DirectManifestDataProvider(data: ManifestData, projectServices: ProjectServices) :
        ManifestDataProvider {

        override val manifestData: Provider<ManifestData> =
            projectServices.providerFactory.provider { data }

        override val manifestLocation: String
            get() = "manifest-location"
    }
}