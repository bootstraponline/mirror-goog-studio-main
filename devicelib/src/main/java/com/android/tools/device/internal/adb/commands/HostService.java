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

package com.android.tools.device.internal.adb.commands;

import com.android.annotations.NonNull;
import com.google.common.base.Charsets;

/**
 * Services offered by the adb server running on a host.
 *
 * <p>The list of commands and the protocol are described in adb's sources at
 * system/core/adb/OVERVIEW.TXT.
 */
public enum HostService {
    VERSION("host:version"),
    KILL("host:kill"),
    DEVICES("host:devices-l"),
    TRACK_DEVICES("host:track-devices"),
    FEATURES("host:host-features");

    private final String cmd;

    HostService(@NonNull String cmd) {
        this.cmd = cmd;
    }

    @NonNull
    public byte[] getCommand() {
        return cmd.getBytes(Charsets.UTF_8);
    }

    @Override
    public String toString() {
        return cmd;
    }
}
