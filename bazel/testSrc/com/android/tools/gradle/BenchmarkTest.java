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

package com.android.tools.gradle;

import com.android.testutils.diff.UnifiedDiff;
import com.android.tools.perflogger.BenchmarkLogger;
import com.android.tools.perflogger.BenchmarkLogger.Benchmark;
import com.android.tools.perflogger.BenchmarkLogger.MetricSample;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class BenchmarkTest {

    private static final String ROOT = "prebuilts/studio/";

    public static void main(String[] args) throws Exception {

        File distribution = null;
        File repo = null;
        String project = null;
        String metric = null;
        int warmUps = 0;
        int iterations = 0;
        List<String> tasks = new ArrayList<>();
        List<String> startups = new ArrayList<>();
        List<String> cleanups = new ArrayList<>();

        Iterator<String> it = Arrays.asList(args).iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("--project") && it.hasNext()) {
                project = it.next();
            } else if (arg.equals("--distribution") && it.hasNext()) {
                distribution = new File(it.next());
            } else if (arg.equals("--repo") && it.hasNext()) {
                repo = new File(it.next());
            } else if (arg.equals("--warmups") && it.hasNext()) {
                warmUps = Integer.valueOf(it.next());
            } else if (arg.equals("--iterations") && it.hasNext()) {
                iterations = Integer.valueOf(it.next());
            } else if (arg.equals("--startup_task") && it.hasNext()) {
                startups.add(it.next());
            } else if (arg.equals("--task") && it.hasNext()) {
                tasks.add(it.next());
            } else if (arg.equals("--cleanup_task") && it.hasNext()) {
                cleanups.add(it.next());
            } else if (arg.equals("--metric") && it.hasNext()) {
                metric = it.next();
            } else {
                throw new IllegalArgumentException("Unknown flag: " + arg);
            }
        }

        new BenchmarkTest()
                .run(
                        project,
                        metric,
                        distribution,
                        repo,
                        warmUps,
                        iterations,
                        startups,
                        cleanups,
                        tasks);
    }

    private static String getLocalGradleVersion() throws IOException {
        try (FileInputStream fis = new FileInputStream("tools/buildSrc/base/version.properties")) {
            Properties properties = new Properties();
            properties.load(fis);
            return properties.getProperty("buildVersion");
        }
    }

    public void run(
            String project,
            String metric,
            File distribution,
            File repo,
            int warmUps,
            int iterations,
            List<String> startups,
            List<String> cleanups,
            List<String> tasks)
            throws Exception {

        Benchmark benchmark = new Benchmark("GradleBenchmark [" + project + "]");
        File data = new File(ROOT + "buildbenchmarks/" + project);
        File out = new File(System.getenv("TEST_TMPDIR"), ".gradle_out");
        File src = new File(System.getenv("TEST_TMPDIR"), ".gradle_src");
        File home = new File(System.getenv("TEST_TMPDIR"), ".home");
        home.mkdirs();

        Gradle.unzip(new File(data, "src.zip"), src);
        UnifiedDiff diff = new UnifiedDiff(new File(data, "setup.diff"));
        diff.apply(src);

        try (Gradle gradle = new Gradle(src, out, distribution)) {
            gradle.addRepo(repo);
            gradle.addRepo(new File(data, "repo.zip"));
            gradle.addArgument("-Dcom.android.gradle.version=" + getLocalGradleVersion());
            gradle.addArgument("-Duser.home=" + home.getAbsolutePath());

            gradle.run(startups);

            BenchmarkLogger logger = new BenchmarkLogger(metric);
            for (int i = 0; i < warmUps + iterations; i++) {
                gradle.run(cleanups);

                long start = System.currentTimeMillis();
                gradle.run(tasks);
                if (i >= warmUps) {
                    logger.addSamples(
                            benchmark, new MetricSample(start, System.currentTimeMillis() - start));
                }
            }
            logger.commit();
        }
    }
}
