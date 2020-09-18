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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.AvdComponentsBuildService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal

/**
 * Task for clearing the gradle avd folder of avd devices.
 */
abstract class ManagedDeviceCleanTask: NonIncrementalGlobalTask() {
    @get: Internal
    abstract val avdService: Property<AvdComponentsBuildService>

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ManagedDeviceCleanRunnable::class.java) {
            it.initializeWith(projectName, path, analyticsService)
            it.avdService.set(avdService)
        }
    }

    abstract class ManagedDeviceCleanRunnable : ProfileAwareWorkAction<ManagedDeviceCleanParams>() {
        override fun run() {
            parameters.avdService.get().deleteAllAvds()
        }
    }

    abstract class ManagedDeviceCleanParams : ProfileAwareWorkAction.Parameters() {
        abstract val avdService: Property<AvdComponentsBuildService>
    }

    class CreationAction(
        override val name: String,
        globalScope: GlobalScope
    ) : GlobalTaskCreationAction<ManagedDeviceCleanTask>(globalScope) {

        override val type: Class<ManagedDeviceCleanTask>
            get() = ManagedDeviceCleanTask::class.java

        override fun configure(task: ManagedDeviceCleanTask) {
            task.avdService.setDisallowChanges(globalScope.avdComponents)
            task.analyticsService.set(
                getBuildService(
                    task.project.gradle.sharedServices, AnalyticsService::class.java
                )
            )
        }
    }
}
