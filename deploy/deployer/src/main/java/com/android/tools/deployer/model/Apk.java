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
package com.android.tools.deployer.model;

import com.android.tools.deployer.ZipUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;

public class Apk {
    public final String name;
    public final String checksum;
    public final String path;
    public final String packageName;

    // If this APK contains instrumentation, this list specifies the other packages that the APK is
    // intended to instrument. The code in the APK will run in these packages' processes. This is
    // most common in the form of an instrumented unit test run from studio.
    public final List<String> targetPackages;

    public final Map<String, ApkEntry> apkEntries;

    private Apk(
            String name,
            String checksum,
            String path,
            String packageName,
            List<String> targetPackages,
            Map<String, ApkEntry> apkEntries) {
        this.name = name;
        this.checksum = checksum;
        this.path = path;
        this.packageName = packageName;
        this.targetPackages = targetPackages;
        this.apkEntries = apkEntries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String checksum;
        private String path;
        private String packageName;
        private List<String> targetPackages;
        private ImmutableMap.Builder<String, ApkEntry> apkEntries;

        public Builder() {
            this.name = "";
            this.checksum = "";
            this.path = "";
            this.packageName = "";
            this.targetPackages = null;
            this.apkEntries = ImmutableMap.builder();
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setChecksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder setPath(String path) {
            this.path = path;
            return this;
        }

        public Builder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder setTargetPackages(List<String> targetPackages) {
            this.targetPackages = targetPackages;
            return this;
        }

        @VisibleForTesting
        public Builder addApkEntry(String name, long checksum) {
            this.apkEntries.put(name, new ApkEntry(name, checksum, null));
            return this;
        }

        public Builder addApkEntry(ZipUtils.ZipEntry zipEntry) {
            this.apkEntries.put(zipEntry.name, new ApkEntry(zipEntry));
            return this;
        }

        public Apk build() {
            targetPackages = targetPackages == null ? ImmutableList.of() : targetPackages;
            Apk apk =
                    new Apk(name, checksum, path, packageName, targetPackages, apkEntries.build());
            apk.apkEntries.values().forEach(entry -> entry.setApk(apk));
            return apk;
        }
    }
}
