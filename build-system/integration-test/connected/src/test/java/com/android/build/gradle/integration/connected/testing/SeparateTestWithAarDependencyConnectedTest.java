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

package com.android.build.gradle.integration.connected.testing;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import java.io.IOException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class SeparateTestWithAarDependencyConnectedTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("separateTestModule")
                    .create();

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Before
    public void setup() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "\n"
                        + "android {\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion = '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion "
                        + TestVersions.SUPPORT_LIB_MIN_SDK
                        + "\n"
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    api 'com.android.support:appcompat-v7:"
                        + TestVersions.SUPPORT_LIB_VERSION
                        + "'\n"
                        + "}\n");

        // fail fast if no response
        project.getSubproject("app").addAdbTimeout();
        project.getSubproject("test").addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        project.execute("uninstallAll");
    }

    @Test
    public void connectedCheck() throws IOException, InterruptedException {
        project.execute("clean", ":test:connectedCheck");
    }
}
