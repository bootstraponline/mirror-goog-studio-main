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

package com.android.build.gradle.internal.variant

import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter
import com.android.build.gradle.internal.services.createDslServices
import com.android.builder.core.ComponentTypeImpl
import com.android.builder.model.SyncIssue
import com.android.testutils.AbstractReturnGivenReturnExpectTest
import com.google.common.truth.Truth

abstract class AbstractVariantInputModelTest<ResultT>:
    AbstractReturnGivenReturnExpectTest<TestVariantInputModel, ResultT>() {

    protected val dslServices = createDslServices()
    private var defaultBuildTypes = false
    private var issueChecker: ((List<SyncIssue>) -> Unit)? = {
        Truth.assertThat(it).named("SyncIssues").isEmpty()
    }

    /**
     * Entry point for creating [VariantInputModel] instance during tests, backed by real build types
     * and product flavors, instantiated and configured via Kotlin DSL
     */
    fun android(action: VariantInputModelDsl.() -> Unit): TestVariantInputModel {
        val modelBuilder = VariantInputModelBuilder(ComponentTypeImpl.BASE_APK)
        if (defaultBuildTypes) {
            modelBuilder.createDefaults()
        }
        action(modelBuilder)

        return modelBuilder.toModel()
    }

    final override fun compareResult(expected: ResultT?, actual: ResultT?, given: TestVariantInputModel) {
        compareResult(expected, actual)
        issueChecker?.invoke((dslServices.issueReporter as FakeSyncIssueReporter).syncIssues)
    }

    abstract fun compareResult(expected: ResultT?, actual: ResultT?)

    fun withIssueChecker(action: (List<SyncIssue>) -> Unit) {
        checkState(TestState.GIVEN)
        issueChecker = action
    }

    fun useDefaultBuildTypes() {
        checkState(TestState.START)
        defaultBuildTypes = true
    }
}
