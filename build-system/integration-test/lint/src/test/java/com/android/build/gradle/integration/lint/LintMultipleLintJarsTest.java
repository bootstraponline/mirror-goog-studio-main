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

package com.android.build.gradle.integration.lint;

import static com.android.testutils.truth.FileSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test ensuring that when we have multiple service-loaded lint jars, they're all included.
 * Regression test for https://issuetracker.google.com/143455360.
 */
public class LintMultipleLintJarsTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("lintMultipleLintJars")
                    // http://b/146208910
                    .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.OFF)
                    .create();

    @Test
    public void checkBothErrorsFound() throws Exception {
        project.executor().run("clean", ":app:lintDebug");
        File file = new File(project.getSubproject("app").getProjectDir(), "lint-results.xml");
        assertThat(file).exists();
        assertThat(file).contains("id=\"ShortUniqueIdA\"");
        assertThat(file).contains("id=\"ShortUniqueIdB\"");
    }
}
