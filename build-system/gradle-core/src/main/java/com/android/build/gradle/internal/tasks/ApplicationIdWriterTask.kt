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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.MODULE
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.METADATA_APP_ID_DECLARATION
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.METADATA_VALUES
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.TaskConfigAction
import com.android.build.gradle.internal.scope.VariantScope
import java.io.File
import java.io.IOException
import java.util.function.Supplier
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Task responsible for publishing the application ID for other modules to consume.
 *
 * If the module is an application module, it publishes the value coming from the variant config.
 *
 * If the module is a base feature, it consumes the value coming from the (installed) application
 * module and republishes it.
 *
 * Both dynamic-feature and feature modules consumes it, from the application module and the base
 * feature module respectively.
 */
open class ApplicationIdWriterTask : AndroidVariantTask() {

    @get:Internal lateinit var applicationIdSupplier: Supplier<String?> private set
    @get:Input val applicationId get() = applicationIdSupplier.get()

    @get:InputFiles
    @get:Optional
    var appIdFromBaseFeature: FileCollection? = null
        private set

    @get:OutputFile
    lateinit var outputFile: File
        private set

    @TaskAction
    @Throws(IOException::class)
    fun fullTaskAction() {
        val packageId = if (appIdFromBaseFeature != null && !appIdFromBaseFeature!!.isEmpty) {
            ApplicationId.load(appIdFromBaseFeature!!.singleFile).applicationId
        } else {
            applicationId as String
        }
        val declaration = ApplicationId(packageId)
        declaration.save(outputFile)
    }

    class ConfigAction(private val variantScope: VariantScope) :
        TaskConfigAction<ApplicationIdWriterTask> {

        override fun getName(): String {
            return variantScope.getTaskName("write", "ApplicationId")
        }

        override fun getType(): Class<ApplicationIdWriterTask> {
            return ApplicationIdWriterTask::class.java
        }

        override fun execute(task: ApplicationIdWriterTask) {
            task.variantName = variantScope.fullVariantName

            // default value of the app ID to publish. This may get overwritten by something
            // coming from an application module.
            task.applicationIdSupplier = TaskInputHelper.memoize {
                variantScope.variantConfiguration.applicationId
            }

            // publish the ID for the dynamic features (whether it's hybrid or not) to consume.
            task.outputFile = variantScope.artifacts.appendArtifact(
                InternalArtifactType.FEATURE_APPLICATION_ID_DECLARATION,
                task,
                ApplicationId.PERSISTED_FILE_NAME
            )
            if (variantScope.type.isHybrid) {
                //if this is a feature, get the Application ID from the metadata config
                task.appIdFromBaseFeature = variantScope.getArtifactFileCollection(
                    METADATA_VALUES, MODULE, METADATA_APP_ID_DECLARATION
                )
            } else {
                //if this is the base application, publish the feature to the metadata config
                variantScope.artifacts.appendArtifact(
                    InternalArtifactType.METADATA_APP_ID_DECLARATION,
                    listOf(task.outputFile),
                    task
                )
            }
        }
    }
}
