/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.core.dsl.impl

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.impl.MutableAndroidVersion
import com.android.build.gradle.internal.core.dsl.AndroidTestComponentDslInfo
import com.android.build.gradle.internal.core.dsl.DynamicFeatureVariantDslInfo
import com.android.build.gradle.internal.core.dsl.TestedVariantDslInfo
import com.android.build.gradle.internal.core.dsl.features.DexingDslInfo
import com.android.build.gradle.internal.core.dsl.impl.features.DexingDslInfoImpl
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalTestedExtension
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.manifest.ManifestDataProvider
import com.android.build.gradle.internal.profile.ProfilingMode
import com.android.build.gradle.internal.services.VariantServices
import com.android.build.gradle.options.StringOption
import com.android.builder.core.ComponentType
import com.android.builder.dexing.DexingType
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

internal class AndroidTestComponentDslInfoImpl(
    componentIdentity: ComponentIdentity,
    componentType: ComponentType,
    defaultConfig: DefaultConfig,
    buildTypeObj: BuildType,
    productFlavorList: List<ProductFlavor>,
    dataProvider: ManifestDataProvider,
    services: VariantServices,
    buildDirectory: DirectoryProperty,
    override val mainVariantDslInfo: TestedVariantDslInfo,
    /**
     *  Whether there are inconsistent applicationId in the test.
     *  This trigger a mode where the namespaceForR just returns the same as namespace.
     */
    private val inconsistentTestAppId: Boolean,
    private val signingConfigOverride: SigningConfig?,
    extension: InternalTestedExtension<*, *, *, *>
) : ConsumableComponentDslInfoImpl(
    componentIdentity,
    componentType,
    defaultConfig,
    buildTypeObj,
    productFlavorList,
    services,
    buildDirectory,
    extension
), AndroidTestComponentDslInfo {
    override val namespace: Provider<String> by lazy {
        getTestComponentNamespace(extension, services, dataProvider)
    }

    override val applicationId: Property<String> =
        services.newPropertyBackingDeprecatedApi(
            String::class.java,
            initTestApplicationId(productFlavorList, defaultConfig, services)
        )

    override val minSdkVersion: MutableAndroidVersion
        get() = mainVariantDslInfo.minSdkVersion

    override val namespaceForR: Provider<String> by lazy {
        if (inconsistentTestAppId) {
            namespace
        } else {
            // For legacy reason, this code does the following:
            // - If testNamespace is set, use it.
            // - If android.namespace is set, use it with .test added
            // - else, use the variant applicationId.
            // TODO(b/176931684) Remove this and use [namespace] directly everywhere.
            extension.testNamespace?.let { services.provider { it } }
                ?: extension.namespace?.let { services.provider { it }.map { "$it.test" } }
                ?: applicationId
        }
    }

    override val isAndroidTestCoverageEnabled: Boolean
        get() = instrumentedTestDelegate.isAndroidTestCoverageEnabled

    // TODO: Android Test doesn't have isDebuggable dsl in the build type, we should move to using
    //  the value from the tested type
    override val isDebuggable: Boolean
        get() = ProfilingMode.getProfilingModeType(
            services.projectOptions[StringOption.PROFILING_MODE]
        ).isDebuggable
            ?: (buildTypeObj as? ApplicationBuildType)?.isDebuggable
            ?: false

    override val signingConfig: SigningConfig? by lazy {
        if (mainVariantDslInfo is DynamicFeatureVariantDslInfo) {
            null
        } else {
            getSigningConfig(
                buildTypeObj,
                mergedFlavor,
                signingConfigOverride,
                extension,
                services
            )
        }
    }

    override val isSigningReady: Boolean
        get() = signingConfig?.isSigningReady == true

    private val instrumentedTestDelegate by lazy {
        InstrumentedTestDslInfoImpl(
            buildTypeObj,
            productFlavorList,
            defaultConfig,
            dataProvider,
            services,
            mainVariantDslInfo.testInstrumentationRunnerArguments
        )
    }

    override val dexingDslInfo: DexingDslInfo by lazy {
        DexingDslInfoImpl(
            buildTypeObj, mergedFlavor, services
        )
    }

    override fun getInstrumentationRunner(dexingType: DexingType): Provider<String> {
        return instrumentedTestDelegate.getInstrumentationRunner(dexingType)
    }

    override val instrumentationRunnerArguments: Map<String, String>
        get() = instrumentedTestDelegate.instrumentationRunnerArguments
    override val handleProfiling: Provider<Boolean>
        get() = instrumentedTestDelegate.handleProfiling
    override val functionalTest: Provider<Boolean>
        get() = instrumentedTestDelegate.functionalTest
    override val testLabel: Provider<String?>
        get() = instrumentedTestDelegate.testLabel
}
