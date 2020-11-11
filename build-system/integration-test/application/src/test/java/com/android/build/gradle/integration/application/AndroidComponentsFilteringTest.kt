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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.builder.model.AndroidProject
import com.android.testutils.AbstractReturnGivenBuildResultTest
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class AndroidComponentsFilteringTest: AbstractReturnGivenBuildResultTest<String,
        AndroidComponentsFilteringTest.VariantBuilder,
        List<AndroidComponentsFilteringTest.VariantInfo>>() {
    @get:Rule
    val project =
            GradleTestProject.builder().fromTestProject("emptyApp").create()

    @Test
    fun `before-unit-test filtering via new api using buildtype callback`() {
        given {
            """
                |    beforeUnitTest(selector().withBuildType("debug")) {
                |        enabled = false
                |    }
            """
        }

        expect {
            variant {
                name = "release"
                androidTest = false
            }
            variant {
                name = "debug"
                unitTest = false
            }
        }
    }

    @Test
    fun `before-android-test filtering via new api using buildtype callback`() {
        given {
            """
                |    beforeAndroidTest(selector().withBuildType("debug")) {
                |        enabled = false
                |    }
            """
        }

        expect {
            variant {
                name = "release"
                androidTest = false
            }
            variant {
                name = "debug"
                androidTest = false
            }
        }
    }

    @Test
    fun `before-unit-test filtering via new api using buildtype and flavor callback`() {
        android {
            """
                |    flavorDimensions "one"
                |    productFlavors {
                |        flavor1 {
                |            dimension "one"
                |        }
                |        flavor2 {
                |            dimension "one"
                |        }
                |    }
            """
        }

        given {
            """
                |    beforeUnitTest(
                |            selector()
                |               .withFlavor(new kotlin.Pair("one", "flavor1"))
                |               .withBuildType("debug")) {
                |        enabled = false
                |    }
            """
        }

        expect {
            variant {
                name = "flavor1Debug"
                unitTest = false
            }
            variant {
                name = "flavor1Release"
                androidTest = false
            }
            variant {
                name = "flavor2Release"
                androidTest = false
            }
            variant {
                name = "flavor2Debug"
            }
        }
    }

    @Test
    fun `before-android-test filtering via new api using buildtype and flavor callback`() {
        android {
            """
                |    flavorDimensions "one"
                |    productFlavors {
                |        flavor1 {
                |            dimension "one"
                |        }
                |        flavor2 {
                |            dimension "one"
                |        }
                |    }
            """
        }

        given {
            """
                |    beforeAndroidTest(
                |            selector()
                |               .withFlavor(new kotlin.Pair("one", "flavor1"))
                |               .withBuildType("debug")) {
                |        enabled = false
                |    }
            """
        }

        expect {
            variant {
                name = "flavor1Debug"
                androidTest = false
            }
            variant {
                name = "flavor1Release"
                androidTest = false
            }
            variant {
                name = "flavor2Release"
                androidTest = false
            }
            variant { name = "flavor2Debug" }
        }
    }

    var androidBlock: (() -> String)? = null

    /**
     * Registers an action block returning the given state as a single object
     */
    open fun android(action: () -> String) {
        checkState(TestState.START)
        androidBlock = action
    }

    override fun instantiateResulBuilder(): VariantBuilder = VariantBuilder()
    override fun defaultWhen(given: String): List<VariantInfo>? {

        androidBlock?.let {
            project.buildFile.appendText(
                    """
                |android {
                |${it().trimMargin()}
                |}
                |
            """.trimMargin()
            )
        }
        project.buildFile.appendText(
                """
                |androidComponents {
                |${given.trimMargin()}
                |}
            """.trimMargin()
        )

        return project.model().fetchAndroidProjects().onlyModel.variants.map {
            VariantInfo(
                    it.name,
                    unitTest = it.extraJavaArtifacts.any { it.name == AndroidProject.ARTIFACT_UNIT_TEST },
                    androidTest = it.extraAndroidArtifacts.any { it.name == AndroidProject.ARTIFACT_ANDROID_TEST })
        }
    }

    override fun compareResult(expected: List<VariantInfo>?, actual: List<VariantInfo>?, given: String) {
        Truth.assertThat(actual).containsExactlyElementsIn(expected)
    }

    class VariantBuilder: ResultBuilder<List<VariantInfo>> {
        private val variants = mutableListOf<VariantInfo>()

        fun variant(action: VariantInfo.() -> Unit) {
            variants.add(VariantInfo().also { action(it) })
        }

        override fun toResult(): List<VariantInfo> {
            return variants
        }
    }

    data class VariantInfo(
            var name: String = "",
            var unitTest: Boolean = true,
            var androidTest: Boolean = true
    )
}