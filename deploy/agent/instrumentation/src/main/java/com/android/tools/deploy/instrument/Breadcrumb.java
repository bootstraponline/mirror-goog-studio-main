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

package com.android.tools.deploy.instrument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.CRC32;

/**
 * Instrumentation class allowing an agent to check whether a previous agent has instrumented the
 * currently running app, and if so, whether that instrumentation dex matches the instrumentation
 * dex that the current agent is using.
 */
@SuppressWarnings("unused") // Used by native instrumentation code.
public final class Breadcrumb {
    // The CRC of the dex library containing this class. Set during checkHash().
    private static long checksum = -1;

    // Whether the agent that loaded this dex library finished instrumenting successfully.
    private static boolean finishedInstrumenting = false;

    // Checks whether or not the instrumentation library at libraryPath is identical to the
    // previous library that was loaded. If no previous library was loaded, returns true.
    public static boolean checkHash(String libraryPath) throws IOException {
        Path path = Paths.get(libraryPath);
        byte[] bytes = Files.readAllBytes(path);

        CRC32 crc = new CRC32();
        crc.update(bytes);

        if (checksum == -1 || checksum == 0) {
            checksum = crc.getValue();
            return true;
        }

        return checksum == crc.getValue();
    }

    // Marks that future agents do not need to instrument.
    public static void setFinishedInstrumenting() {
        assert (!finishedInstrumenting);
        finishedInstrumenting = true;
    }

    // Checks if a previous run of the agent finished instrumenting.
    public static boolean isFinishedInstrumenting() {
        return finishedInstrumenting;
    }
}
