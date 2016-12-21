/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.integration.databinding;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.ide.common.process.ProcessException;
import com.android.testutils.apk.Aar;
import com.android.testutils.apk.Apk;
import com.google.common.base.Joiner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(FilterableParameterized.class)
public class DataBindingTest {

    @Parameterized.Parameters(name="library={0},forExperimentalPlugin={1},withoutAdapters={2},jack={3}")
    public static Collection<Object[]> getParameters() {
        List<Object[]> options = new ArrayList<>();
        for (int i = 0 ; i < 16; i ++) {
            options.add(new Object[]{
                (i & 1) != 0, (i & 2) != 0, (i & 4) != 0, (i & 8) != 0
            });
        }
        return options;
    }
    private final boolean myWithoutAdapters;
    private final boolean myLibrary;
    private final boolean myUseJack;
    private final String buildFile;

    public DataBindingTest(boolean library, boolean forExperimentalPlugin, boolean withoutAdapters,
            boolean useJack) {
        myWithoutAdapters = withoutAdapters;
        myLibrary = library;
        List<String> options = new ArrayList<>();
        if (library) {
            options.add("library");
        }
        if (withoutAdapters) {
            options.add("withoutadapters");
        }
        if (forExperimentalPlugin) {
            options.add("forexperimental");
        }
        myUseJack = useJack;
        project = GradleTestProject.builder()
                .fromTestProject("databinding")
                .useExperimentalGradleVersion(forExperimentalPlugin)
                .withJack(myUseJack)
                .withBuildToolsVersion(
                        useJack ? GradleTestProject.UPCOMING_BUILD_TOOL_VERSION : null)
                .create();
        buildFile = options.isEmpty()
                ? null
                : "build." + Joiner.on('-').join(options) + ".gradle";
    }

    @Rule
    public final GradleTestProject project;

    @Before
    public void skipLibraryJack() {
        Assume.assumeTrue(!myUseJack || !myLibrary);
    }

    @Test
    public void checkApkContainsDataBindingClasses() throws IOException, ProcessException {
        project.setBuildFile(buildFile);
        GradleBuildResult result = project.executor().run("assembleDebug");
        assertThat(result.getTask(":dataBindingProcessLayoutsDebug")).wasExecuted();

        if (myLibrary) {
            Aar aar = project.getAar("debug");
            assertThat(aar).doesNotContainClass("Landroid/g/testapp/databinding/ActivityMainBinding;");
            assertThat(aar).doesNotContainClass("Landroid/databinding/adapters/Converters;");
            assertThat(aar).doesNotContainClass("Landroid/databinding/DataBindingComponent;");
        } else {
            Apk apk = project.getApk("debug");
            assertThat(apk).containsClass("Landroid/databinding/testapp/databinding/ActivityMainBinding;");
            assertThat(apk).containsClass("Landroid/databinding/DataBindingComponent;");
            if (myWithoutAdapters) {
                assertThat(apk).doesNotContainClass("Landroid/databinding/adapters/Converters;");
            } else {
                assertThat(apk).containsClass("Landroid/databinding/adapters/Converters;");
            }
        }
    }
}
