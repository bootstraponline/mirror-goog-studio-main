/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.tools.lint.client.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;

/**
 * Exception thrown when there is a circular dependency, such as a circular dependency of library
 * project references
 */
public class CircularDependencyException extends RuntimeException {
    @Nullable private Project project;

    @Nullable private Location location;

    public CircularDependencyException(@NonNull String message) {
        super(message);
    }

    /**
     * Returns the associated project, if any
     *
     * @return the associated project, if any
     */
    @Nullable
    public Project getProject() {
        return project;
    }

    /**
     * Sets the associated project, if any
     *
     * @param project the associated project, if any
     */
    public void setProject(@Nullable Project project) {
        this.project = project;
    }

    /**
     * Returns the associated location, if any
     *
     * @return the associated location, if any
     */
    @Nullable
    public Location getLocation() {
        return location;
    }

    /**
     * Sets the associated location, if any
     *
     * @param location the associated location, if any
     */
    public void setLocation(@Nullable Location location) {
        this.location = location;
    }
}
