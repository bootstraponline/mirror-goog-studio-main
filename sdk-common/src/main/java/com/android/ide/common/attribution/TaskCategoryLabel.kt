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

package com.android.ide.common.attribution

/**
* Enum class for Category-Based Task Analyzer for Build Analyzer.
* Each field corresponds to a specific atomic function/execution of a task.
* Each task has at least 1 field.
 **/
enum class TaskCategoryLabel {
    // Tasks that perform compilation-related actions
    COMPILATION,
    // Tasks that perform test invocation or execution
    TEST,
}
