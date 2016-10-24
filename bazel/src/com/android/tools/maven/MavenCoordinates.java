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

package com.android.tools.maven;

import org.apache.maven.model.Model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenCoordinates {
    static final Pattern MAVEN_COORDINATES = Pattern.compile("^([^:]+):([^:]+):(.+)$");

    static boolean isMavenCoordinate(String s) {
        return MAVEN_COORDINATES.matcher(s).matches();
    }

    public final String groupId;
    public final String artifactId;
    public final String version;

    public MavenCoordinates(Model model) {
        groupId = model.getGroupId();
        artifactId = model.getArtifactId();
        version = model.getVersion();
    }

    public MavenCoordinates(String coordinates) {
        Matcher matcher = MavenCoordinates.MAVEN_COORDINATES.matcher(coordinates);
        if (matcher.matches()) {
            groupId = matcher.group(1);
            artifactId = matcher.group(2);
            version = matcher.group(3);
        } else {
            throw new IllegalArgumentException("Invalid maven coordinated: " + coordinates);
        }
    }
}
