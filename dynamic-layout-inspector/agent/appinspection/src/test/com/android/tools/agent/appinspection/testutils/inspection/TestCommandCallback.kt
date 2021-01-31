/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.agent.appinspection.testutils.inspection

import androidx.inspection.Inspector
import java.util.concurrent.Executor

class TestCommandCallback : Inspector.CommandCallback {
    val replyListeners = mutableListOf<(ByteArray) -> Unit>()

    override fun reply(response: ByteArray) {
        replyListeners.forEach { it(response) }
    }

    override fun addCancellationListener(executor: Executor, runnable: Runnable) =
        throw NotImplementedError()
}
