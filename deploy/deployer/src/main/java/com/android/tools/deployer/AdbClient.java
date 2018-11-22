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

package com.android.tools.deployer;

import com.android.ddmlib.*;
import com.android.utils.ILogger;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AdbClient {
    private final IDevice device;
    private final ILogger logger;

    public AdbClient(IDevice device, ILogger logger) {
        this.device = device;
        this.logger = logger;
    }

    enum InstallResult {
        OK,
        INSTALL_FAILED_VERSION_DOWNGRADE,
        UNKNOWN_ERROR,
        DEVICE_NOT_RESPONDING,
        INSTALL_FAILED_UPDATE_INCOMPATIBLE,
        INCONSISTENT_CERTIFICATES,
        INSTALL_FAILED_DEXOPT,
        NO_CERTIFICATE,
        INSTALL_FAILED_OLDER_SDK,
        DEVICE_NOT_FOUND,
    }

    /**
     * Executes the given command and sends {@code input} to stdin and returns stdout as a byte[]
     */
    public byte[] shell(String[] parameters, InputStream input) throws IOException {
        logger.info("SHELL: " + String.join(" ", parameters));
        ByteArrayOutputReceiver receiver;
        try (Trace ignored = Trace.begin("adb shell" + Arrays.toString(parameters))) {
            receiver = new ByteArrayOutputReceiver();
            // Use 8000ms timeout since dex2oat on O can make install timeout.
            device.executeShellCommand(
                    String.join(" ", parameters), receiver, 8000, TimeUnit.MILLISECONDS, input);
            return receiver.toByteArray();
        } catch (AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | TimeoutException e) {
            throw new IOException(e);
        }
    }

    public InstallResult install(List<String> apks, List<String> options) {
        List<File> files = apks.stream().map(File::new).collect(Collectors.toList());
        try {
            device.installPackages(files, true, options, 10, TimeUnit.SECONDS);
            return InstallResult.OK;
        } catch (InstallException e) {
            System.err.println(e.getMessage());
            String code = e.getErrorCode();
            InstallResult result = InstallResult.UNKNOWN_ERROR;
            try {
                result = InstallResult.valueOf(code);
            } catch (IllegalArgumentException | NullPointerException ignored) {
            }
            return result;
        }
    }

    public boolean uninstall(String packageName) {
        try {
            device.uninstallPackage(packageName);
            return true;
        } catch (InstallException e) {
        }
        return false;
    }

    public List<String> getAbis() {
        return device.getAbis();
    }

    public void push(String from, String to) throws IOException {
        try (Trace ignored = Trace.begin("adb push")) {
            device.pushFile(from, to);
        } catch (SyncException | TimeoutException | AdbCommandRejectedException e) {
            throw new IOException(e);
        }
    }

    // TODO: Replace this to void copying the full byte[] incurred when calling stream.toByteArray()
    private class ByteArrayOutputReceiver implements IShellOutputReceiver {

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        @Override
        public void addOutput(byte[] data, int offset, int length) {
            stream.write(data, offset, length);
        }

        @Override
        public void flush() {}

        @Override
        public boolean isCancelled() {
            return false;
        }

        byte[] toByteArray() {
            return stream.toByteArray();
        }
    }
}
