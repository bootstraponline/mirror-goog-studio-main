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

package com.android.build.gradle.internal.tasks.factory

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

class TaskFactoryImpl(private val taskContainer: TaskContainer):
    TaskFactory {

    override fun containsKey(name: String): Boolean {
        return taskContainer.findByName(name) != null
    }

    override fun eagerCreate(name: String): Task {
        return taskContainer.create(name)
    }

    override fun <S : Task> eagerCreate(name: String, type: Class<S>): S {
        return taskContainer.create(name, type)
    }

    override fun eagerConfigure(name: String, configAction: Action<in Task>) {
        val task = taskContainer.getByName(name)
        configAction.execute(task)
    }

    override fun findByName(name: String): Task? {
        return taskContainer.findByName(name)
    }

    override fun <T : Task> eagerCreate(creationAction: EagerTaskCreationAction<T>): T {
        val task = taskContainer.create(creationAction.name, creationAction.type)
        @Suppress("UNCHECKED_CAST")
        val taskProvider = taskContainer.named(task.name) as TaskProvider<T>
        creationAction.preConfigure(taskProvider, task.name)
        creationAction.execute(task)
        return task
    }

    override fun <T : Task> eagerCreate(
            taskName: String, taskClass: Class<T>, configAction: Action<T>): T {
        return taskContainer.create(taskName, taskClass, configAction)
    }

    override fun eagerCreate(taskName: String, configAction: Action<DefaultTask>): DefaultTask {
        return eagerCreate(taskName, DefaultTask::class.java, configAction)
    }
}