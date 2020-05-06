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

package com.android.build.gradle.internal.plugins

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.fixture.TestConstants
import com.android.build.gradle.internal.fixture.TestProjects
import com.android.builder.errors.EvalIssueException
import com.google.common.collect.ImmutableMap
import com.google.common.truth.StringSubject
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.test.assertFailsWith
import org.gradle.api.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Functional tests for the new Kotlin DSL. */
class KotlinDslTest {

    @get:Rule
    val projectDirectory = TemporaryFolder()

    private lateinit var plugin: AppPlugin
    private lateinit var android: ApplicationExtension<*, *, *, *, *>
    private lateinit var project: Project

    @Before
    fun setUp() {
        project = TestProjects.builder(projectDirectory.newFolder("project").toPath())
            .withPlugin(TestProjects.Plugin.APP)
            .build()

        initFieldsFromProject()
    }

    private fun initFieldsFromProject() {
        android =
            project.extensions.getByType(ApplicationExtension::class.java)
        android.compileSdk = TestConstants.COMPILE_SDK_VERSION
        plugin = project.plugins.getPlugin(AppPlugin::class.java)
    }

    @Test
    fun testDslLocking() {
        plugin.createAndroidTasks()
        val exception = assertFailsWith(EvalIssueException::class) {
            android.compileSdk = 28
        }
        assertThat(exception).hasMessageThat().isEqualTo(
            """
                It is too late to set property '_compileSdkVersion' to 'android-28'. (It has value 'android-${TestConstants.COMPILE_SDK_VERSION}')
                The DSL is now locked as the variants have been created.
                Either move this call earlier, or use the variant API to customize individual variants.
                """.trimIndent()
        )
    }

    @Test
    fun `compileAgainst externalNativeBuild ndkBuild ImplClass`() {

        val externalNativeBuild: com.android.build.gradle.internal.dsl.ExternalNativeBuild =
            android.externalNativeBuild as com.android.build.gradle.internal.dsl.ExternalNativeBuild

        // Using apply as made the intentionally not source compatible change here: Ib3e58c50c4a5af2ebc11882fc48d75b1e4f410fe
        externalNativeBuild.ndkBuild.apply {

            assertThat(path).isNull()
            path = File("path1")
            assertThatPath(path).endsWith("path1")
            path("path2")
            assertThatPath(path).endsWith("path2")
            setPath("path3")
            assertThatPath(path).endsWith("path3")

            assertThat(buildStagingDirectory).isNull()
            buildStagingDirectory = File("buildStagingDirectory1")
            assertThatPath(buildStagingDirectory).endsWith("buildStagingDirectory1")
            buildStagingDirectory("buildStagingDirectory2")
            assertThatPath(buildStagingDirectory).endsWith("buildStagingDirectory2")
            setBuildStagingDirectory("buildStagingDirectory3")
            assertThatPath(buildStagingDirectory).endsWith("buildStagingDirectory3")
        }

        // Using apply as made the intentionally not source compatible change here: Ib3e58c50c4a5af2ebc11882fc48d75b1e4f410fe
        externalNativeBuild.cmake.apply {
            assertThat(path).isNull()
            path = File("path1")
            assertThatPath(path).endsWith("path1")
            setPath("path3")
            assertThatPath(path).endsWith("path3")

            assertThat(buildStagingDirectory).isNull()
            buildStagingDirectory = File("buildStagingDirectory1")
            assertThatPath(buildStagingDirectory).endsWith("buildStagingDirectory1")
            setBuildStagingDirectory("buildStagingDirectory3")
            assertThatPath(buildStagingDirectory).endsWith("buildStagingDirectory3")

            assertThat(version).isNull()
            version = "version1"
            assertThat(version).isEqualTo("version1")
        }
    }

    /** Regression test for b/146488072 */
    @Test
    fun `compile against variant specific external native build impl class`() {
        (android as BaseAppModuleExtension).defaultConfig.apply {
            // Check the getters return the more specific type
            // (the arguments method is not on the interface)
            externalNativeBuild.ndkBuild.arguments("a")
            externalNativeBuild.cmake.arguments("x")

            // Check the action methods use the more specific type
            externalNativeBuild {
                ndkBuild {
                    arguments("b")
                }
            }
            externalNativeBuild {
                cmake {
                    arguments("y")
                }
            }

            assertThat(externalNativeBuild.ndkBuild.arguments)
                .containsExactly("a", "b").inOrder()
            assertThat(externalNativeBuild.cmake.arguments)
                .containsExactly("x", "y").inOrder()
        }
    }

    @Test
    fun `manifest placeholders source compatibility`() {
        (android as BaseAppModuleExtension).defaultConfig.apply {
            // Check can accept mapOf with string to string
            setManifestPlaceholders(mapOf("a" to "A"))
            assertThat(manifestPlaceholders).containsExactly("a", "A")
            // Check can add items when setter called with an immutable collection
            // (i.e. setter copies)
            setManifestPlaceholders(ImmutableMap.of())
            manifestPlaceholders["c"] = 3
            assertThat(manifestPlaceholders).containsExactly("c", 3)
            // Prior to this change
            //     New DSL: Re-add setManifestPlaceholders to preserve source compatibility
            // the set method manifestPlaceholders(Map) was implemented by the Gradle decorator.
            // Verify that the explicitly implemented method does actually set, not append.
            manifestPlaceholders = mutableMapOf("a" to "b")
            manifestPlaceholders(mapOf("d" to "D"))
            assertThat(manifestPlaceholders).containsExactly("d", "D")
        }
    }

    /** Regression test for https://b.corp.google.com/issues/155318103 */
    @Test
    fun `mergedFlavor source compatibility`() {
        val applicationVariants = (android as BaseAppModuleExtension).applicationVariants
        applicationVariants.all { variant ->
            variant.mergedFlavor.manifestPlaceholders += mapOf("a" to "b")
            variant.mergedFlavor.testInstrumentationRunnerArguments += mapOf("c" to "d")
        }
        plugin.createAndroidTasks()
        assertThat(applicationVariants).hasSize(2)
        applicationVariants.first().also { variant ->
            assertThat(variant.mergedFlavor.manifestPlaceholders).containsExactly("a", "b")
            assertThat(variant.mergedFlavor.testInstrumentationRunnerArguments).containsExactly("c", "d")
        }
    }

    @Test
    fun `testInstrumentationRunnerArguments source compatibility`() {
        android.defaultConfig.testInstrumentationRunnerArguments.put("a", "b")
        assertThat(android.defaultConfig.testInstrumentationRunnerArguments).containsExactly("a", "b")

        android.defaultConfig.testInstrumentationRunnerArguments += "c" to "d"
        assertThat(android.defaultConfig.testInstrumentationRunnerArguments).containsExactly("a", "b", "c", "d")
    }

    private fun assertThatPath(file: File?): StringSubject {
        return assertThat(file?.path)
    }
}
