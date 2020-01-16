/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl

import com.google.common.collect.ImmutableList
import javax.inject.Inject

/** Options for the adb tool. */
class AdbOptions @Inject constructor() : com.android.builder.model.AdbOptions,
    com.android.build.api.dsl.AdbOptions {

    /** The time out used for all adb operations. */
    override var timeOutInMs: Int = 0

    /** The list of FULL_APK installation options. */
    override var installOptions: Collection<String>? = null

    fun timeOutInMs(timeOutInMs: Int) {
        this.timeOutInMs = timeOutInMs
    }

    fun setInstallOptions(option: String) {
        installOptions = ImmutableList.of(option)
    }

    fun setInstallOptions(vararg options: String) {
        installOptions = ImmutableList.copyOf(options)
    }

    fun installOptions(option: String) {
        installOptions = ImmutableList.of(option)
    }

    fun installOptions(vararg options: String) {
        installOptions = ImmutableList.copyOf(options)
    }
}
