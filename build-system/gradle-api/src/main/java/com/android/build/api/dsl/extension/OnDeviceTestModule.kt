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

package com.android.build.api.dsl.extension

import com.android.builder.model.AdbOptions
import com.android.builder.testing.api.DeviceProvider
import com.android.builder.testing.api.TestServer
import org.gradle.api.Action

/** Partial extension properties for modules that contain on-device tests  */
interface OnDeviceTestModule {
    /** Adb options.  */
    val adbOptions: AdbOptions

    fun adbOptions(action: Action<AdbOptions>)

    /** List of device providers  */
    var deviceProviders: List<DeviceProvider>

    /** List of remote CI servers.  */
    var testServers: List<TestServer>
}
