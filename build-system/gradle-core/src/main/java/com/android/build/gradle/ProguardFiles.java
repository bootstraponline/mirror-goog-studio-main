/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.builder.Version;
import com.android.builder.model.AndroidProject;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;

/**
 * Deals with the default ProGuard files for Gradle.
 */
public class ProguardFiles {

    public enum ProguardFile {
        /** Default when not using the "postprocessing" DSL block. */
        DONT_OPTIMIZE("proguard-android.txt"),

        /** Variant of the above which does not disable optimizations. */
        OPTIMIZE("proguard-android-optimize.txt"),

        /**
         * Does not disable any actions, includes optimizations config. To be used with the new
         * "postprocessing" DSL block.
         */
        NO_ACTIONS("proguard-defaults.txt"),
        ;

        @NonNull public final String fileName;

        ProguardFile(@NonNull String fileName) {
            this.fileName = fileName;
        }
    }

    @VisibleForTesting
    public static final Set<String> KNOWN_FILE_NAMES =
            Arrays.stream(ProguardFile.values()).map(pf -> pf.fileName).collect(Collectors.toSet());

    public static final String UNKNOWN_FILENAME_MESSAGE =
            "Supplied proguard configuration file name is unsupported. Valid values are: "
                    + KNOWN_FILE_NAMES;

    /**
     * Creates and returns a new {@link File} with the requested default ProGuard file contents.
     *
     * <p><b>Note:</b> If the file is already there it just returns it.
     *
     * <p>There are 2 default rules files
     * <ul>
     *     <li>proguard-android.txt
     *     <li>proguard-android-optimize.txt
     * </ul>
     *
     * @param name the name of the default ProGuard file.
     * @param project used to determine the output location.
     */
    public static File getDefaultProguardFile(@NonNull String name, @NonNull Project project) {
        if (!KNOWN_FILE_NAMES.contains(name)) {
            throw new IllegalArgumentException(UNKNOWN_FILENAME_MESSAGE);
        }

        return FileUtils.join(
                project.getRootProject().getBuildDir(),
                AndroidProject.FD_INTERMEDIATES,
                "proguard-files",
                name + "-" + Version.ANDROID_GRADLE_PLUGIN_VERSION);
    }

    /**
     * Extracts all default ProGuard files into the build directory.
     */
    public static void extractBundledProguardFiles(@NonNull Project project) throws IOException {
        for (String name : KNOWN_FILE_NAMES) {
            File defaultProguardFile = getDefaultProguardFile(name, project);
            if (!defaultProguardFile.isFile()) {
                createProguardFile(name, defaultProguardFile);
            }
        }
    }

    @VisibleForTesting
    public static void createProguardFile(@NonNull String name, @NonNull File destination)
            throws IOException {
        ProguardFile proguardFile = null;
        for (ProguardFile knownFile : ProguardFile.values()) {
            if (knownFile.fileName.equals(name)) {
                proguardFile = knownFile;
                break;
            }
        }
        Preconditions.checkArgument(proguardFile != null, "Unknown file " + name);

        Files.createParentDirs(destination);
        StringBuilder sb = new StringBuilder();

        append(sb, "proguard-header.txt");
        sb.append("\n");

        switch (proguardFile) {
            case DONT_OPTIMIZE:
                sb.append(
                        "# Optimization is turned off by default. Dex does not like code run\n"
                                + "# through the ProGuard optimize steps (and performs some\n"
                                + "# of these optimizations on its own).\n"
                                + "# Note that if you want to enable optimization, you cannot just\n"
                                + "# include optimization flags in your own project configuration file;\n"
                                + "# instead you will need to point to the\n"
                                + "# \"proguard-android-optimize.txt\" file instead of this one from your\n"
                                + "# project.properties file.\n"
                                + "-dontoptimize\n");
                break;
            case OPTIMIZE:
                sb.append(
                        "# Optimizations: If you don't want to optimize, use the proguard-android.txt configuration file\n"
                                + "# instead of this one, which turns off the optimization flags.\n");
                append(sb, "proguard-optimizations.txt");
                break;
            case NO_ACTIONS:
                sb.append(
                        "# Optimizations can be turned on and off in the 'postprocessing' DSL block.\n"
                                + "# The configuration below is applied if optimizations are enabled.\n");
                append(sb, "proguard-optimizations.txt");
                break;
        }

        sb.append("\n");
        append(sb, "proguard-common.txt");

        Files.write(sb.toString(), destination, UTF_8);
    }

    private static void append(StringBuilder sb, String resourceName) throws IOException {
        sb.append(Resources.toString(ProguardFiles.class.getResource(resourceName), UTF_8));
    }
}
