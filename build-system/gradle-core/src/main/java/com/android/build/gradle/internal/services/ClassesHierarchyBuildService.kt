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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.instrumentation.ClassesHierarchyData
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.objectweb.asm.Opcodes.ASM7
import java.io.File

/**
 * A build service for sharing [ClassesHierarchyData] objects between workers of the same variant.
 */
abstract class ClassesHierarchyBuildService : BuildService<BuildServiceParameters.None>,
    AutoCloseable {
    private val classesHierarchyDataMap = mutableMapOf<String, ClassesHierarchyData>()

    @Synchronized
    fun getClassesHierarchyData(projectName: String, variantName: String): ClassesHierarchyData {
        return classesHierarchyDataMap.computeIfAbsent("$projectName-$variantName") {
            ClassesHierarchyData(ASM7)
        }
    }

    override fun close() {
        classesHierarchyDataMap.clear()
    }

    class RegistrationAction(project: Project) :
        ServiceRegistrationAction<ClassesHierarchyBuildService, BuildServiceParameters.None>(
            project,
            ClassesHierarchyBuildService::class.java
        ) {
        override fun configure(parameters: BuildServiceParameters.None) {
            // do nothing
        }
    }
}
