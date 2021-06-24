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

import static com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.runner.FilterableParameterized;
import com.android.build.gradle.integration.common.truth.ScannerSubject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Test for generating baselines for all variants, making sure we don't accidentally merge resources
 * files in different resource qualifiers; https://issuetracker.google.com/131073349
 */
@RunWith(FilterableParameterized.class)
public class LintBaselineTest {

    @Parameterized.Parameters(
            name = "usePartialAnalysis = {0}, lintBaselinesContinue = {1}, runLintInProcess = {2}")
    public static List<Object[]> getParameters() {
        return Arrays.asList(
                new Object[][] {
                    // default case
                    {true, false, true},
                    // test -Dlint.baselines.continue=true in and out of process
                    {true, true, true},
                    {true, true, false},
                    // test without partial analysis (use defaults for other parameters)
                    {false, false, true}
                });
    }

    @Parameterized.Parameter(0)
    public boolean usePartialAnalysis;

    @Parameterized.Parameter(1)
    public boolean lintBaselinesContinue;

    @Parameterized.Parameter(2)
    public boolean runLintInProcess;

    @Rule
    public final GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("lintBaseline")
                    .withConfigurationCacheMaxProblems(23)
                    .create();

    @Test
    public void checkBaselineRecreation() throws Exception {
        GradleTaskExecutor executor = getExecutor();
        if (!lintBaselinesContinue) {
            executor.expectFailure();
        }

        final GradleBuildResult result = executor.run(":app:lint");
        ScannerSubject.assertThat(result.getStderr()).contains("Created baseline file");
        File baselineFile =
                new File(project.getSubproject("app").getProjectDir(), "lint-baseline.xml");
        if (lintBaselinesContinue) {
            assertThat(result.getTask(":app:lintReportDebug")).didWork();
        } else {
            assertThat(result.getTask(":app:lintReportDebug")).failed();
        }
        if (usePartialAnalysis) {
            assertThat(result.getTask(":app:lintAnalyzeDebug")).didWork();
        }
        assertThat(baselineFile).exists();

        // Delete the baseline file generated by the first invocation
        assertThat(baselineFile.delete()).isTrue();

        // Run the lint task again and verify that the baseline file was re-created
        final GradleBuildResult result2 = executor.run(":app:lint");
        if (lintBaselinesContinue) {
            assertThat(result2.getTask(":app:lintReportDebug")).didWork();
        } else {
            assertThat(result2.getTask(":app:lintReportDebug")).failed();
        }
        if (usePartialAnalysis) {
            // The Analysis task doesn't need to run again if the baseline file is deleted
            //  and its inputs are otherwise unchanged FIXME(b/??? bug not yet filed STOPSHIP)
            assertThat(result2.getTask(":app:lintAnalyzeDebug")).wasUpToDate();
        }
        ScannerSubject.assertThat(result2.getStderr()).contains("Created baseline file");
        assertThat(baselineFile).exists();
    }

    @Test
    public void checkMerging() throws Exception {
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n\nandroid.lintOptions.textOutput file(\"lint-report.txt\")\n\n");
        final GradleBuildResult result;
        if (lintBaselinesContinue) {
            result = getExecutor().run(":app:lint");
        } else {
            result = getExecutor().expectFailure().run(":app:lint");
        }

        ScannerSubject.assertThat(result.getStderr()).contains("Created baseline file");

        File baselineFile =
                new File(project.getSubproject("app").getProjectDir(), "lint-baseline.xml");
        assertThat(baselineFile).exists();
        String baseline = FilesKt.readText(baselineFile, Charsets.UTF_8);
        assertThat(baseline)
                .contains(
                        ""
                                + "    <issue\n"
                                + "        id=\"UselessLeaf\"\n"
                                + "        message=\"This `LinearLayout` view is unnecessary (no children, no `background`, no `id`, no `style`)\"\n"
                                + "        errorLine1=\"    &lt;LinearLayout android:layout_width=&quot;match_parent&quot; android:layout_height=&quot;match_parent&quot;>&lt;/LinearLayout>\"\n"
                                + "        errorLine2=\"     ~~~~~~~~~~~~\">\n"
                                + "        <location\n"
                                + "            file=\"src/main/res/layout-land/my_layout.xml\"\n"
                                + "            line=\"7\"\n"
                                + "            column=\"6\"/>\n"
                                + "    </issue>\n"
                                + "\n"
                                + "    <issue\n"
                                + "        id=\"UselessLeaf\"\n"
                                + "        message=\"This `LinearLayout` view is unnecessary (no children, no `background`, no `id`, no `style`)\"\n"
                                + "        errorLine1=\"    &lt;LinearLayout android:layout_width=&quot;match_parent&quot; android:layout_height=&quot;match_parent&quot;>&lt;/LinearLayout>\"\n"
                                + "        errorLine2=\"     ~~~~~~~~~~~~\">\n"
                                + "        <location\n"
                                + "            file=\"src/main/res/layout/my_layout.xml\"\n"
                                + "            line=\"7\"\n"
                                + "            column=\"6\"/>\n"
                                + "    </issue>\n"
                                + "\n");
        // Check the written baseline means that a subsequent lint invocation passes.
        getExecutor().run("clean", ":app:lint");
        File lintResults = project.file("app/lint-report.txt");
        // Regression test for b/192572040
        assertThat(lintResults)
                .doesNotContain(
                        "errors/warnings were listed in the baseline file (lint-baseline.xml) but not found in the project");

        // FIXME(b/193249817): running "clean" alongside ":app:lint" (above) alters the
        //    `printStackTrace` @Input so Lint tasks will not be up-to-date on the next run
        //    (below) workaround: run ":app:lint" on its own, here, to prep for the next invocation
        getExecutor().run(":app:lint");

        // Check that the lint task is up-to-date during a subsequent run
        GradleBuildResult upToDateRunResult = getExecutor().run(":app:lint");
        assertThat(upToDateRunResult.getTask(":app:lintReportDebug")).wasUpToDate();
        if (usePartialAnalysis) {
            assertThat(upToDateRunResult.getTask(":app:lintAnalyzeDebug")).wasUpToDate();
        }

        // Check that the lint task is no longer up-to-date after changing the baseline file
        TestFileUtils.appendToFile(baselineFile, "\n\n\n\n");
        GradleBuildResult didWorkResult = getExecutor().run(":app:lint");
        assertThat(didWorkResult.getTask(":app:lintReportDebug")).didWork();
        if (usePartialAnalysis) {
            assertThat(didWorkResult.getTask(":app:lintAnalyzeDebug")).didWork();
        }
    }

    @Test
    public void checkBaselineFilteringJarWarnings() throws Exception {
        // This test doesn't need to be parameterized; for the baseline filtering
        // scenario, baseline-continue is unrelated (and won't work since we're
        // reusing executors to use the preferences root dir, and putting
        // expectFailure in there will break the last run), and running out
        // of process does not properly set the preference directory.
        assumeTrue(lintBaselinesContinue);
        assumeTrue(runLintInProcess);

        // invoke a build to force initialization of preferencesRootDir
        GradleTaskExecutor executor = getExecutor();
        executor.run("tasks");

        File prefsLintDir = FileUtils.join(executor.getPreferencesRootDir(), ".android", "lint");
        FileUtils.cleanOutputDir(prefsLintDir);
        File file = new File(prefsLintDir, "sample-custom-checks.jar");
        try {
            FileUtils.createFile(file, "FOO_BAR");

            TestFileUtils.appendToFile(
                    project.getSubproject("app").getBuildFile(),
                    "\n\nandroid.lintOptions.textOutput file(\"lint-report.txt\")\n\n");
            final GradleBuildResult result = executor.run(":app:lint");

            ScannerSubject.assertThat(result.getStderr()).contains("Created baseline file");

            File baselineFile =
                    new File(project.getSubproject("app").getProjectDir(), "lint-baseline.xml");
            assertThat(baselineFile).exists();
            String baseline = FilesKt.readText(baselineFile, Charsets.UTF_8);
            assertThat(baseline)
                    .contains(
                            ""
                                    + "    <issue\n"
                                    + "        id=\"UselessLeaf\"\n"
                                    + "        message=\"This `LinearLayout` view is unnecessary (no children, no `background`, no `id`, no `style`)\"\n"
                                    + "        errorLine1=\"    &lt;LinearLayout android:layout_width=&quot;match_parent&quot; android:layout_height=&quot;match_parent&quot;>&lt;/LinearLayout>\"\n"
                                    + "        errorLine2=\"     ~~~~~~~~~~~~\">\n"
                                    + "        <location\n"
                                    + "            file=\"src/main/res/layout-land/my_layout.xml\"\n"
                                    + "            line=\"7\"\n"
                                    + "            column=\"6\"/>\n"
                                    + "    </issue>\n"
                                    + "\n");
            assertThat(baseline)
                    .contains("(sample-custom-checks.jar); this will stop working soon.");

            // Check the written baseline means that a subsequent lint invocation passes.
            executor.run("clean", ":app:lint");
            File lintResults = project.file("app/lint-report.txt");
            assertThat(lintResults)
                    .doesNotContain(
                            "errors/warnings were listed in the baseline file (lint-baseline.xml) but not found in the project");
        } finally {
            // Make sure we don't leave this jar around in a shared directory to affect later tests
            boolean deleted = file.delete();
            assertThat(deleted).isTrue();
        }
    }

    private GradleTaskExecutor getExecutor() {
        return project.executor()
                .with(BooleanOption.USE_LINT_PARTIAL_ANALYSIS, usePartialAnalysis)
                .with(BooleanOption.RUN_LINT_IN_PROCESS, runLintInProcess)
                .withArgument("-Dlint.baselines.continue=" + lintBaselinesContinue);
    }
}
