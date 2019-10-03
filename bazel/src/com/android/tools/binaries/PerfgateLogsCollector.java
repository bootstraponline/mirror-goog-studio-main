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

package com.android.tools.binaries;

import com.google.common.io.ByteStreams;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PerfgateLogsCollector {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("Usage: perfgate_logs_collector <base_dir> <data.bes> <out.zip> <err.txt>");
            return;
        }

        String logs = args[0];
        String bes = args[1];
        String zip = args[2];
        String err = args[3];

        Map<String, BuildEventStreamProtos.NamedSetOfFiles> files = new HashMap<>();
        Map<String, BuildEventStreamProtos.BuildEvent> completed = new HashMap<>();
        Map<String, File> outputZips = new HashMap<>();
        try (InputStream is = new BufferedInputStream(new FileInputStream(bes));
                PrintStream os = new PrintStream(new FileOutputStream(err))) {
            BuildEventStreamProtos.BuildEvent event;
            while ((event = BuildEventStreamProtos.BuildEvent.parseDelimitedFrom(is)) != null) {
                if (event.getPayloadCase()
                        == BuildEventStreamProtos.BuildEvent.PayloadCase.NAMED_SET_OF_FILES) {
                    BuildEventStreamProtos.NamedSetOfFiles set = event.getNamedSetOfFiles();
                    files.put(event.getId().getNamedSet().getId(), set);
                } else if (event.getPayloadCase()
                        == BuildEventStreamProtos.BuildEvent.PayloadCase.COMPLETED) {
                    completed.put(event.getId().getTargetCompleted().getLabel(), event);
                } else if (event.getPayloadCase()
                        == BuildEventStreamProtos.BuildEvent.PayloadCase.TEST_RESULT) {
                    String label = event.getId().getTestResult().getLabel();
                    final int run = event.getId().getTestResult().getRun();
                    for (BuildEventStreamProtos.File file :
                            event.getTestResult().getTestActionOutputList()) {
                        if (file.getName().equals("test.outputs__outputs.zip")) {
                            BuildEventStreamProtos.BuildEvent e = completed.get(label);
                            if (e == null) {
                                os.println(
                                        "Test target has outputs.zip, but "
                                                + label
                                                + " not completed.");
                                continue;
                            }
                            Set<Integer> runs = new HashSet<>();
                            for (BuildEventStreamProtos.BuildEventId id : e.getChildrenList()) {
                                if (id.getIdCase() == BuildEventStreamProtos.BuildEventId.IdCase.TEST_RESULT) {
                                    if (id.getTestResult().getRun() > 0) {
                                        runs.add(id.getTestResult().getRun());
                                    }
                                }
                            }
                            BuildEventStreamProtos.TargetComplete targetComplete = e.getCompleted();
                            for (BuildEventStreamProtos.OutputGroup outputGroup :
                                    targetComplete.getOutputGroupList()) {
                                for (BuildEventStreamProtos.BuildEventId.NamedSetOfFilesId id :
                                        outputGroup.getFileSetsList()) {
                                    BuildEventStreamProtos.NamedSetOfFiles set =
                                            files.get(id.getId());
                                    if (set == null) {
                                        os.printf(
                                                "File set \"%s\" for target %s not found",
                                                id.getId(), label);
                                        continue;
                                    }

                                    Set<String> candidates = new HashSet<>();
                                    boolean found = false;
                                    for (BuildEventStreamProtos.File aFile : set.getFilesList()) {
                                        String logPath = aFile.getName();
                                        if (logPath.endsWith(".exe")) {
                                            logPath = logPath.substring(0, logPath.length() - 4);
                                        }
                                        if (logPath.endsWith(".sh")) {
                                            logPath = logPath.substring(0, logPath.length() - 3);
                                        }
                                        if (runs.size() > 1) {
                                            logPath = logPath + String.format("/run_%d_of_%d", run, runs.size());
                                        }
                                        String rel = logPath + "/test.outputs/outputs.zip";
                                        candidates.add(rel);
                                        final File candidate = new File(logs, rel);
                                        if (candidate.exists()) {
                                            outputZips.put(rel, candidate);
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        os.printf("Cannot find output.zip for %s, tried:\n", label);
                                        for (String candidate : candidates) {
                                            os.println("  " + candidate);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(new File(zip)))) {
            for (Map.Entry<String, File> entry : outputZips.entrySet()) {
                ZipEntry e = new ZipEntry(entry.getKey());
                try (InputStream fis = new BufferedInputStream(new FileInputStream(entry.getValue()))) {
                    out.putNextEntry(e);
                    ByteStreams.copy(fis, out);
                    out.closeEntry();
                }
            }
        }
    }
}
