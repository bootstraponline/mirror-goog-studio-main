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

package com.android.tools.utp.plugins.deviceprovider.gradle

import com.google.testing.platform.api.config.AndroidSdk
import com.google.testing.platform.api.config.Environment
import com.google.testing.platform.api.config.Setup
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.proto.api.config.AdbConfigProto
import com.google.testing.platform.runtime.android.AndroidDeviceProvider

/**
 * Means to provide a device controller to the [AndroidGradleDeviceLauncher]
 */
interface DeviceControllerFactory {
    /** Provides a DeviceController to the Device provider */
    fun getController(
            provider: AndroidDeviceProvider,
            environment: Environment,
            testSetup: Setup,
            androidSdk: AndroidSdk,
            adbConfig: AdbConfigProto.AdbConfig,
            context: Context,
    ): DeviceController
}
