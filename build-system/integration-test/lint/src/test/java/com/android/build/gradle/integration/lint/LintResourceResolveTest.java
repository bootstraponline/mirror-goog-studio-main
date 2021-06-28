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

import static com.android.testutils.truth.PathSubject.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.options.BooleanOption;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test making sure that the SupportAnnotationUsage does not report errors referencing R.type.name
 * resource fields. (Regression test for bug 133326990.)
 */
@RunWith(FilterableParameterized.class)
public class LintResourceResolveTest {

    @Parameterized.Parameters(name = "usePartialAnalysis = {0}")
    public static Object[] getParameters() {
        return new Object[] {true, false};
    }

    @Parameterized.Parameter public boolean usePartialAnalysis;

    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("lintResourceResolve")
                    .withConfigurationCacheMaxProblems(44)
                    .create();

    @Test
    public void checkClean() throws Exception {
        // Run twice to catch issues with configuration caching
        getExecutor().run(":app:clean", ":app:lintDebug");
        getExecutor().run(":app:clean", ":app:lintDebug");
        File file = new File(project.getSubproject("app").getProjectDir(), "lint-report.txt");
        assertThat(file).exists();
        assertThat(file).contentWithUnixLineSeparatorsIsExactly("No issues found.");

        File sarifFile = new File(project.getSubproject("app").getBuildDir(), "reports/lint-results-debug.sarif");
        assertThat(sarifFile).exists();
        assertThat(sarifFile).contains("\"$schema\" : \"https://raw.githubusercontent.com/oasis-tcs/sarif-spec/");
    }

    private GradleTaskExecutor getExecutor() {
        return project.executor().with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis);
    }
}
