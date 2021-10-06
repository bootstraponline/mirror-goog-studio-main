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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.model.ReferenceModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.model.v2.ide.SyncIssue
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import org.junit.Rule
import org.junit.Test

class HelloWorldAppModelTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareVersions(goldenFile = "Versions")
        with(result).compareBasicAndroidProject(goldenFile = "BasicAndroidProject")
        with(result).compareAndroidProject(goldenFile = "AndroidProject")
        with(result).compareAndroidDsl(goldenFile = "AndroidDsl")
        with(result).compareVariantDependencies(goldenFile = "VariantDependencies")
    }
}

class ApplicationIdInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                applicationId = "customized.application.id"
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class DisabledAidlInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    aidl = false
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

class DisabledRenderScriptInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    renderScript = false
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

class DisabledResValuesInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    resValues = false
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        ensureAndroidProjectDeltaIsEmpty()
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class DisabledShadersInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    shaders = false
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        ensureAndroidProjectDeltaIsEmpty()
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

class DisabledBuildConfigInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    buildConfig = false
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class EnabledMlModelBindingInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildFeatures {
                    mlModelBinding = true
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

class DefaultBuildTypeAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildTypes {
                    named("debug") {
                        isDefault = true
                    }
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        ensureAndroidProjectDeltaIsEmpty()
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class DefaultFlavorAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                productFlavors {
                    named("flavorA") {
                        dimension = "foo"
                    }
                    named("flavorB") {
                        dimension = "foo"
                    }
                }
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                productFlavors {
                    named("flavorA") {
                        isDefault = true
                    }
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        ensureAndroidProjectDeltaIsEmpty()
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class AarApiJarModelTest : ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            dependencies {
                implementation(
                    MavenRepoGenerator.Library(
                        "com.example:myaar:1",
                        "aar",
                        generateAarWithContent(
                            packageName = "com.example.myaar",
                            mainJar = TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/MainClass")),
                            secondaryJars = mapOf(
                                "impl1.jar" to TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/Impl1")),
                                "impl2.jar" to TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/Impl2"))
                            ),
                            apiJar = TestInputsGenerator.jarWithEmptyClasses(listOf("com/example/ApiClass"))
                        )
                    )
                )
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    },
    variantName = "debug"
) {

    @Test
    fun `test AndroidProject model`() {
        ensureAndroidProjectDeltaIsEmpty()
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }

    @Test
    fun `test dependencies model`() {
        compareVariantDependenciesWith(goldenFileSuffix = "WithAar")
    }
}

class AndroidTestNamespaceWithCustomAppIdTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                applicationId = "com.custom.appid"
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test AndroidProject model`() {
        // TODO(b/176931684) Once we stop using applicationId for the androidTestNamespace then
        //  this will need to be changed as the namespace will be not be impacted by the
        //  DSL-set appId
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class AndroidTestNamespaceWithCustomNamespaceTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                namespace = "com.custom.namespace"
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test AndroidProject model`() {
        // TODO(b/176931684) Once we stop using applicationId for the androidTestNamespace then
        //  this will need to be changed as the namespace will be impacted by the DSL-set ns
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

class LintChecksInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            dependencies {
                lintChecks(localJar {
                    name = "lint-checks.jar"
                    addClass("com/example/MainClass")
                })
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }
}

class ResValuesInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            android {
                buildTypes {
                    named("debug") {
                        resValue("string", "foo", "val")
                        resValue("drawable", "foo", "val")
                    }
                }
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {
    @Test
    fun `test AndroidDsl model`() {
        compareAndroidDslWith(goldenFileSuffix = "AndroidDsl")
    }
}

/**
 * This test disables the debug variant.
 */
class DisabledVariantInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableVariant("debug")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * This test disables the only AndroidTest component of the project (debug)
 */
class DisabledAndroidTestInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableAndroidTest("debug")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * This test disables the debug UnitTest component of the project (There is still the release
 * one)
 */
class DisabledUnitTestInAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableUnitTest("debug")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * This test disables a single variant in a 4 variant setup (2 build types, 2 flavors)
 */
class DisabledSingleVariantInFlavorAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                productFlavors {
                    named("one") {
                        dimension = "foo"
                    }
                    named("two") {
                        dimension = "foo"
                    }
                }
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableVariant("oneDebug")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * This test disables  variants in a 4 variant setup (2 build types, 2 flavors) by the build type
 * (ie all variant of build type debug)
 */
class DisabledVariantByBuildTypeInFlavorAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                productFlavors {
                    named("one") {
                        dimension = "foo"
                    }
                    named("two") {
                        dimension = "foo"
                    }
                }
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableVariant("oneDebug")
                disableVariant("twoDebug")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * This test disables  variants in a 4 variant setup (2 build types, 2 flavors) by the flavor
 * (ie all variant of flavor "one")
 */
class DisabledVariantByFlavorInFlavorAppModelTest: ReferenceModelComparator(
    referenceConfig = {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
                productFlavors {
                    named("one") {
                        dimension = "foo"
                    }
                    named("two") {
                        dimension = "foo"
                    }
                }
            }
        }
    },
    deltaConfig = {
        rootProject {
            androidComponents {
                disableVariant("oneDebug")
                disableVariant("oneRelease")
            }
        }
    },
    syncOptions = {
        ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
    }
) {

    @Test
    fun `test BasicAndroidProject model`() {
        compareBasicAndroidProjectWith(goldenFileSuffix = "BasicAndroidProject")
    }

    @Test
    fun `test AndroidProject model`() {
        compareAndroidProjectWith(goldenFileSuffix = "AndroidProject")
    }

    @Test
    fun `test AndroidDsl model`() {
        ensureAndroidDslDeltaIsEmpty()
    }
}

/**
 * Tests case where there's an artifact relocated via Gradle metadata
 */
class RelocatedArtifactTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")

        with(result).compareVariantDependencies(goldenFile = "VariantDependencies")
    }
}

class NoVariantModelTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
            }
            androidComponents {
                disableVariant("debug")
                disableVariant("release")
            }
        }
    }

    @Test
    fun `test models`() {
        val result = project.modelV2()
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels()

        with(result).compareBasicAndroidProject(goldenFile = "BasicAndroidProject")
        with(result).compareAndroidProject(goldenFile = "AndroidProject")
        with(result).compareAndroidDsl(goldenFile = "AndroidDsl")
    }
}
