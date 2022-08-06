/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools;

import com.android.tools.deployer.Sites;
import org.junit.Assert;
import org.junit.Test;

public class TestSites {
    @Test
    public void testSites() {
        String pkg = "foo";

        String appDate = Sites.appData(pkg);
        String codeCache = Sites.appCodeCache(pkg);
        String studio = Sites.appStudio(pkg);
        String logs = Sites.appLog(pkg);
        String startup = Sites.appStartupAgent(pkg);
        String overlays = Sites.appOverlays(pkg);
        String liveLiteral = Sites.appLiveLiteral(pkg);

        String deviceStudioFolder = Sites.deviceStudioFolder();
        Assert.assertEquals("/data/local/tmp/.studio/", deviceStudioFolder);

        String installerExecutableFolder = Sites.installerExecutableFolder();
        Assert.assertEquals("/data/local/tmp/.studio/bin/", installerExecutableFolder);

        String installerTmpFolder = Sites.installerTmpFolder();
        Assert.assertEquals("/data/local/tmp/.studio/tmp/", installerTmpFolder);

        String installerBinary = Sites.installerBinary();
        Assert.assertEquals("installer", installerBinary);

        String installerPath = Sites.installerPath();
        Assert.assertEquals("/data/local/tmp/.studio/bin/installer", installerPath);
    }
}
