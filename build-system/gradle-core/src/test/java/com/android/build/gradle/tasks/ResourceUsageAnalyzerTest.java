/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.tasks;

import static com.android.build.gradle.tasks.ResourceUsageAnalyzer.NO_MATCH;
import static com.android.build.gradle.tasks.ResourceUsageAnalyzer.REPLACE_DELETED_WITH_EMPTY;
import static com.android.build.gradle.tasks.ResourceUsageAnalyzer.convertFormatStringToRegexp;
import static com.android.build.shrinker.LinkedResourcesFormat.BINARY;
import static com.google.common.truth.Truth.assertThat;
import static java.io.File.separatorChar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.shrinker.DummyContent;
import com.android.ide.common.resources.usage.ResourceUsageModel;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.ResourceType;
import com.android.testutils.TestResources;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** TODO: Test Resources#getIdentifier() handling */
@SuppressWarnings("SpellCheckingInspection")
@RunWith(Parameterized.class)
public class ResourceUsageAnalyzerTest {

    @NonNull private final RVarient rVarient;

    public ResourceUsageAnalyzerTest(@NonNull RVarient param) {
        this.rVarient = param;
    }

    enum CodeInput {
        NO_SHRINKER,
        PROGUARD,
        R8
    }

    enum RVarient {
        R_JAVA,
        R_JAR,
        R_TXT
    }

    @Parameterized.Parameters(name = "rVarient={0}")
    public static RVarient[] getParameters() {
        return new RVarient[] {RVarient.R_JAVA, RVarient.R_JAR, RVarient.R_TXT};
    }

    @ClassRule
    public static TemporaryFolder sTemporaryFolder = new TemporaryFolder();

    @Test
    public void testObfuscatedInPlace() throws Exception {
        check(CodeInput.PROGUARD, true);
    }

    @Test
    public void testObfuscatedCopy() throws Exception {
        check(CodeInput.PROGUARD, false);
    }

    @Test
    public void testNoProGuardInPlace() throws Exception {
        check(CodeInput.NO_SHRINKER, true);
    }

    @Test
    public void testNoProGuardCopy() throws Exception {
        check(CodeInput.NO_SHRINKER, false);
    }

    @Test
    public void testR8InPlace() throws Exception {
        check(CodeInput.R8, true);
    }

    @Test
    public void testR8Copy() throws Exception {
        check(CodeInput.R8, false);
    }

    @Test
    public void testConfigOutput() throws Exception {
        File dir = sTemporaryFolder.newFolder();

        File mapping;
        File classes;

        classes = createUnproguardedClasses(dir);
        mapping = null;

        File rDir = createResourceSources(dir);
        File mergedManifest = createMergedManifest(dir);
        File resources = createResourceFolder(dir, false);

        ResourceUsageAnalyzer analyzer =
                new ResourceUsageAnalyzer(
                        rDir,
                        Collections.singleton(classes),
                        mergedManifest,
                        mapping,
                        resources,
                        null);
        analyzer.analyze();
        checkState(analyzer);
        assertEquals(
                ""
                        + "attr/myAttr1#remove\n"
                        + "attr/myAttr2#remove\n"
                        + "dimen/activity_horizontal_margin#\n"
                        + "dimen/activity_vertical_margin#\n"
                        + "drawable/avd_heart_fill#remove\n"
                        + "drawable/avd_heart_fill_1#remove\n"
                        + "drawable/avd_heart_fill_2#remove\n"
                        + "drawable/ic_launcher#\n"
                        + "drawable/unused#remove\n"
                        + "id/action_settings#\n"
                        + "id/action_settings2#remove\n"
                        + "layout/activity_main#\n"
                        + "menu/main#\n"
                        + "menu/menu2#remove\n"
                        + "raw/android_wear_micro_apk#\n"
                        + "raw/index1#remove\n"
                        + "raw/my_js#remove\n"
                        + "raw/my_used_raw_drawable#remove\n"
                        + "raw/styles2#remove\n"
                        + "string/action_settings#\n"
                        + "string/action_settings2#remove\n"
                        + "string/alias#remove\n"
                        + "string/app_name#\n"
                        + "string/hello_world#\n"
                        + "style/AppTheme#remove\n"
                        + "style/MyStyle#\n"
                        + "style/MyStyle_Child#\n"
                        + "xml/android_wear_micro_apk#\n",
                analyzer.getModel().dumpConfig());
    }

    @Test
    public void testToolsKeepDiscard() throws Exception {
        File dir = sTemporaryFolder.newFolder();
        File resources = createResourceFolder(dir, true);

        ResourceUsageAnalyzer analyzer =
                new ResourceUsageAnalyzer(
                        createResourceSources(dir),
                        Collections.singleton(createR8Dex(dir)),
                        createMergedManifest(dir),
                        createMappingFile(dir),
                        resources,
                        null);

        analyzer.analyze();
        checkState(analyzer);

        assertEquals(
                ""
                        + "@attr/myAttr1 : reachable=false\n"
                        + "@attr/myAttr2 : reachable=false\n"
                        + "@dimen/activity_horizontal_margin : reachable=true\n"
                        + "@dimen/activity_vertical_margin : reachable=true\n"
                        + "@drawable/avd_heart_fill : reachable=false\n"
                        + "    @drawable/avd_heart_fill_1\n"
                        + "    @drawable/avd_heart_fill_2\n"
                        + "@drawable/avd_heart_fill_1 : reachable=true\n"
                        + "@drawable/avd_heart_fill_2 : reachable=false\n"
                        + "@drawable/ic_launcher : reachable=true\n"
                        + "@drawable/unused : reachable=false\n"
                        + "@id/action_settings : reachable=true\n"
                        + "@id/action_settings2 : reachable=false\n"
                        + "@layout/activity_main : reachable=true\n"
                        + "    @dimen/activity_vertical_margin\n"
                        + "    @dimen/activity_horizontal_margin\n"
                        + "    @string/hello_world\n"
                        + "    @style/MyStyle_Child\n"
                        + "@menu/main : reachable=false\n"
                        + "    @string/action_settings\n"
                        + "@menu/menu2 : reachable=false\n"
                        + "    @string/action_settings2\n"
                        + "@raw/android_wear_micro_apk : reachable=true\n"
                        + "@raw/index1 : reachable=false\n"
                        + "    @raw/my_used_raw_drawable\n"
                        + "@raw/keep : reachable=false\n"
                        + "@raw/my_js : reachable=false\n"
                        + "@raw/my_used_raw_drawable : reachable=false\n"
                        + "@raw/styles2 : reachable=false\n"
                        + "@string/action_settings : reachable=false\n"
                        + "@string/action_settings2 : reachable=false\n"
                        + "@string/alias : reachable=false\n"
                        + "    @string/app_name\n"
                        + "@string/app_name : reachable=true\n"
                        + "@string/hello_world : reachable=true\n"
                        + "@style/AppTheme : reachable=false\n"
                        + "@style/MyStyle : reachable=true\n"
                        + "@style/MyStyle_Child : reachable=true\n"
                        + "    @style/MyStyle\n"
                        + "@xml/android_wear_micro_apk : reachable=true\n"
                        + "    @raw/android_wear_micro_apk\n",
                analyzer.getModel().dumpResourceModel());

        String serialized = analyzer.getModel().serialize(true);
        assertEquals(
                ""
                        + "attr[\n"
                        + "myAttr1(D,7f010000),\n"
                        + "myAttr2(D,7f010001)],\n"
                        + "dimen[\n"
                        + "activity_vertical_margin(U,7f040001),\n"
                        + "activity_horizontal_margin(U,7f040000)],\n"
                        + "drawable[\n"
                        + "avd_heart_fill_1(R),\n"
                        + "avd_heart_fill_2(E),\n"
                        + "avd_heart_fill(D),\n"
                        + "unused(D,7f020001),\n"
                        + "ic_launcher(U,7f020000)],\n"
                        + "id[\n"
                        + "action_settings(U,7f080000),\n"
                        + "action_settings2(D,7f080001)],\n"
                        + "layout[\n"
                        + "activity_main(U,7f030000)],\n"
                        + "menu[\n"
                        + "main(D,7f070000),\n"
                        + "menu2(D)],\n"
                        + "raw[\n"
                        + "my_js(D,7f090003),\n"
                        + "styles2(D,7f090002),\n"
                        + "android_wear_micro_apk(U,7f090000),\n"
                        + "index1(D,7f090001),\n"
                        + "keep(D),\n"
                        + "my_used_raw_drawable(E,7f090004)],\n"
                        + "string[\n"
                        + "action_settings(D,7f050000),\n"
                        + "app_name(U,7f050002),\n"
                        + "hello_world(U,7f050003),\n"
                        + "alias(D,7f050001),\n"
                        + "action_settings2(D,7f050004)],\n"
                        + "style[\n"
                        + "MyStyle(U,7f060001),\n"
                        + "MyStyle_Child(U,7f060002),\n"
                        + "AppTheme(D,7f060000)],\n"
                        + "xml[\n"
                        + "android_wear_micro_apk(U,7f0a0000)];\n"
                        + "6^4^5,b^2^3^16^1a,c^14,d^18,11^13,17^15,1a^19,1c^10;\n"
                        + "@drawable/avd_heart_fill_1;\n"
                        + "@menu/main;\n",
                prettyPrintSerializedModel(serialized));

        // Deserialize and serialize again to make sure the two models again
        ResourceUsageModel deserialized = ResourceUsageModel.deserialize(serialized);
        assertEquals(serialized, deserialized.serialize(true));
    }

    private void check(CodeInput codeInput, boolean inPlace) throws Exception {
        File dir = sTemporaryFolder.newFolder();

        File mapping;
        File classes;
        switch (codeInput) {
            case PROGUARD:
                classes = createProguardedClasses(dir);
                mapping = createMappingFile(dir);
                break;
            case NO_SHRINKER:
                classes = createUnproguardedClasses(dir);
                mapping = null;
                break;
            case R8:
                classes = createR8Dex(dir);
                mapping = createMappingFile(dir);
                break;
            default:
                throw new AssertionError();
        }
        File rSource = createResourceSources(dir);

        File mergedManifest = createMergedManifest(dir);
        File resources = createResourceFolder(dir, false);

        ResourceUsageAnalyzer analyzer =
                new ResourceUsageAnalyzer(
                        rSource,
                        Collections.singleton(classes),
                        mergedManifest,
                        mapping,
                        resources,
                        null);
        analyzer.analyze();
        checkState(analyzer);
        assertEquals(
                ""
                        + "@attr/myAttr1 : reachable=false\n"
                        + "@attr/myAttr2 : reachable=false\n"
                        + "@dimen/activity_horizontal_margin : reachable=true\n"
                        + "@dimen/activity_vertical_margin : reachable=true\n"
                        + "@drawable/avd_heart_fill : reachable=false\n"
                        + "    @drawable/avd_heart_fill_1\n"
                        + "    @drawable/avd_heart_fill_2\n"
                        + "@drawable/avd_heart_fill_1 : reachable=false\n"
                        + "@drawable/avd_heart_fill_2 : reachable=false\n"
                        + "@drawable/ic_launcher : reachable=true\n"
                        + "@drawable/unused : reachable=false\n"
                        + "@id/action_settings : reachable=true\n"
                        + "@id/action_settings2 : reachable=false\n"
                        + "@layout/activity_main : reachable=true\n"
                        + "    @dimen/activity_vertical_margin\n"
                        + "    @dimen/activity_horizontal_margin\n"
                        + "    @string/hello_world\n"
                        + "    @style/MyStyle_Child\n"
                        + "@menu/main : reachable=true\n"
                        + "    @string/action_settings\n"
                        + "@menu/menu2 : reachable=false\n"
                        + "    @string/action_settings2\n"
                        + "@raw/android_wear_micro_apk : reachable=true\n"
                        + "@raw/index1 : reachable=false\n"
                        + "    @raw/my_used_raw_drawable\n"
                        + "@raw/my_js : reachable=false\n"
                        + "@raw/my_used_raw_drawable : reachable=false\n"
                        + "@raw/styles2 : reachable=false\n"
                        + "@string/action_settings : reachable=true\n"
                        + "@string/action_settings2 : reachable=false\n"
                        + "@string/alias : reachable=false\n"
                        + "    @string/app_name\n"
                        + "@string/app_name : reachable=true\n"
                        + "@string/hello_world : reachable=true\n"
                        + "@style/AppTheme : reachable=false\n"
                        + "@style/MyStyle : reachable=true\n"
                        + "@style/MyStyle_Child : reachable=true\n"
                        + "    @style/MyStyle\n"
                        + "@xml/android_wear_micro_apk : reachable=true\n"
                        + "    @raw/android_wear_micro_apk\n",
                analyzer.getModel().dumpResourceModel());

        String serialized = checkSerialization(analyzer);
        assertEquals(
                ""
                        + "attr[\n"
                        + "myAttr1(D,7f010000),\n"
                        + "myAttr2(D,7f010001)],\n"
                        + "dimen[\n"
                        + "activity_vertical_margin(U,7f040001),\n"
                        + "activity_horizontal_margin(U,7f040000)],\n"
                        + "drawable[\n"
                        + "avd_heart_fill_1(E),\n"
                        + "avd_heart_fill_2(E),\n"
                        + "avd_heart_fill(D),\n"
                        + "unused(D,7f020001),\n"
                        + "ic_launcher(U,7f020000)],\n"
                        + "id[\n"
                        + "action_settings(U,7f080000),\n"
                        + "action_settings2(D,7f080001)],\n"
                        + "layout[\n"
                        + "activity_main(U,7f030000)],\n"
                        + "menu[\n"
                        + "main(U,7f070000),\n"
                        + "menu2(D)],\n"
                        + "raw[\n"
                        + "my_js(D,7f090003),\n"
                        + "styles2(D,7f090002),\n"
                        + "android_wear_micro_apk(U,7f090000),\n"
                        + "index1(D,7f090001),\n"
                        + "my_used_raw_drawable(E,7f090004)],\n"
                        + "string[\n"
                        + "action_settings(U,7f050000),\n"
                        + "app_name(U,7f050002),\n"
                        + "hello_world(U,7f050003),\n"
                        + "alias(D,7f050001),\n"
                        + "action_settings2(D,7f050004)],\n"
                        + "style[\n"
                        + "MyStyle(U,7f060001),\n"
                        + "MyStyle_Child(U,7f060002),\n"
                        + "AppTheme(D,7f060000)],\n"
                        + "xml[\n"
                        + "android_wear_micro_apk(U,7f0a0000)];\n"
                        + "6^4^5,b^2^3^15^19,c^13,d^17,11^12,16^14,19^18,1b^10;\n"
                        + ";\n"
                        + ";\n",
                prettyPrintSerializedModel(serialized));

        File unusedBitmap = new File(resources, "drawable-xxhdpi" + separatorChar + "unused.png");
        assertTrue(unusedBitmap.exists());

        if (ResourceUsageAnalyzer.TWO_PASS_AAPT) {
            File destination = inPlace ? null : sTemporaryFolder.newFolder();

            analyzer.setDryRun(true);
            analyzer.removeUnused(destination);
            assertTrue(unusedBitmap.exists());

            analyzer.setDryRun(false);
            analyzer.removeUnused(destination);

            if (inPlace) {
                assertFalse(unusedBitmap.exists());
            } else {
                assertTrue(unusedBitmap.exists());
                assertTrue(new File(destination, "values" + separatorChar + "values.xml").exists());
                assertFalse(new File(destination, "drawable-xxhdpi" + separatorChar +
                        "unused.png").exists());
            }
            File values = new File(inPlace ? resources : destination,
                    "values" + separatorChar + "values.xml");
            assertTrue(values.exists());
            assertEquals(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "\n"
                + "    <attr name=\"myAttr1\" format=\"integer\" />\n"
                + "    <attr name=\"myAttr2\" format=\"boolean\" />\n"
                + "\n"
                + "    <dimen name=\"activity_horizontal_margin\">16dp</dimen>\n"
                + "    <dimen name=\"activity_vertical_margin\">16dp</dimen>\n"
                + "\n"
                + "    <string name=\"action_settings\">Settings</string>\n"
                + "    <string name=\"app_name\">ShrinkUnitTest</string>\n"
                + "    <string name=\"hello_world\">Hello world!</string>\n"
                + "\n"
                + "</resources>",

                Files.toString(values, Charsets.UTF_8));
            if (destination != null) {
                deleteDir(destination);
            }
        } else {
            List<File> files = Lists.newArrayList();
            addFiles(resources, files);
            Collections.sort(files, (file, file2) -> file.getPath().compareTo(file2.getPath()));

            // Generate a .zip file from a directory
            File uncompressedFile = File.createTempFile("uncompressed", ".ap_");
            String prefix = resources.getPath() + File.separatorChar;
            try (FileOutputStream fos = new FileOutputStream(uncompressedFile);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {
                for (File file : files) {
                    if (file.equals(resources)) {
                        continue;
                    }
                    assertTrue(file.getPath().startsWith(prefix));
                    String relative = "res/" + file.getPath().substring(prefix.length())
                            .replace(File.separatorChar, '/');
                    boolean isValuesFile = relative.equals("res/values/values.xml");
                    if (isValuesFile) {
                        relative = "resources.arsc";
                    }
                    ZipEntry ze = new ZipEntry(relative);
                    zos.putNextEntry(ze);
                    if (!file.isDirectory() && !isValuesFile) {
                        byte[] bytes = Files.toByteArray(file);
                        zos.write(bytes);
                    }
                    zos.closeEntry();
                }
            }

            assertEquals(""
                    + "res/drawable\n"
                    + "res/drawable-hdpi\n"
                    + "res/drawable-hdpi/ic_launcher.png\n"
                    + "res/drawable-mdpi\n"
                    + "res/drawable-mdpi/ic_launcher.png\n"
                    + "res/drawable-xxhdpi\n"
                    + "res/drawable-xxhdpi/ic_launcher.png\n"
                    + "res/drawable-xxhdpi/unused.png\n"
                    + "res/drawable/avd_heart_fill.xml\n"
                    + "res/layout\n"
                    + "res/layout/activity_main.xml\n"
                    + "res/menu\n"
                    + "res/menu/main.xml\n"
                    + "res/menu/menu2.xml\n"
                    + "res/raw\n"
                    + "res/raw/android_wear_micro_apk.apk\n"
                    + "res/raw/index1.html\n"
                    + "res/raw/my_js.js\n"
                    + "res/raw/styles2.css\n"
                    + "res/values\n"
                    + "resources.arsc\n"
                    + "res/xml\n"
                    + "res/xml/android_wear_micro_apk.xml\n",
                    dumpZipContents(uncompressedFile));

            System.out.println(uncompressedFile);


            File compressedFile = File.createTempFile("compressed", ".ap_");

            analyzer.rewriteResourcesInApkFormat(uncompressedFile, compressedFile, BINARY);

            // Check contents
            assertEquals(""
                    + "res/drawable\n"
                    + "res/drawable-hdpi\n"
                    + "res/drawable-hdpi/ic_launcher.png\n"
                    + "res/drawable-mdpi\n"
                    + "res/drawable-mdpi/ic_launcher.png\n"
                    + "res/drawable-xxhdpi\n"
                    + "res/drawable-xxhdpi/ic_launcher.png\n"
                    + (REPLACE_DELETED_WITH_EMPTY ? "res/drawable-xxhdpi/unused.png\n" : "")
                    + (REPLACE_DELETED_WITH_EMPTY ? "res/drawable/avd_heart_fill.xml\n" : "")
                    + "res/layout\n"
                    + "res/layout/activity_main.xml\n"
                    + "res/menu\n"
                    + "res/menu/main.xml\n"
                    + "res/menu/menu2.xml\n"
                    + "res/raw\n"
                    + "res/raw/android_wear_micro_apk.apk\n"
                    + (REPLACE_DELETED_WITH_EMPTY ? "res/raw/index1.html\n" : "")
                    + (REPLACE_DELETED_WITH_EMPTY ? "res/raw/my_js.js\n" : "")
                    + (REPLACE_DELETED_WITH_EMPTY ? "res/raw/styles2.css\n" : "")
                    + "res/values\n"
                    + "resources.arsc\n"
                    + "res/xml\n"
                    + "res/xml/android_wear_micro_apk.xml\n",
                    dumpZipContents(compressedFile));

            if (REPLACE_DELETED_WITH_EMPTY) {
                assertTrue(Arrays.equals(DummyContent.TINY_PNG,
                        getZipContents(compressedFile, "res/drawable-xxhdpi/unused.png")));
            }

            analyzer.close();

            uncompressedFile.delete();
            compressedFile.delete();
        }

        deleteDir(dir);
    }

    private static String prettyPrintSerializedModel(String serialized) {
        return serialized
                .replace("),", "),\n")
                .replace("[", "[\n")
                .replace("],", "],\n")
                .replace(";", ";\n");
    }

    private static String dumpZipContents(File zipFile) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(zipFile)) {
            try (ZipInputStream zis = new ZipInputStream(fis)) {
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    sb.append(entry.getName());
                    sb.append('\n');
                    entry = zis.getNextEntry();
                }
            }
        }

        return sb.toString();
    }

    @Nullable
    private static byte[] getZipContents(File zipFile, String name) throws IOException {
        try (FileInputStream fis = new FileInputStream(zipFile)) {
            try (ZipInputStream zis = new ZipInputStream(fis)) {
                ZipEntry entry = zis.getNextEntry();
                while (entry != null) {
                    if (name.equals(entry.getName())) {
                        return ByteStreams.toByteArray(zis);
                    }
                    entry = zis.getNextEntry();
                }
            }
        }

        return null;
    }

    private static void addFiles(File file, List<File> files) {
        files.add(file);
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) {
                for (File f : list) {
                    addFiles(f, files);
                }
            }
        }
    }

    private static File createResourceFolder(File dir, boolean addKeepXml) throws IOException {
        File resources = new File(dir, "app/build/res/all/release".replace('/', separatorChar));
        //noinspection ResultOfMethodCallIgnored
        resources.mkdirs();

        createFile(resources, "drawable-hdpi/ic_launcher.png", new byte[0]);
        createFile(resources, "drawable-mdpi/ic_launcher.png", new byte[0]);
        createFile(resources, "drawable-xxhdpi/ic_launcher.png", new byte[0]);
        createFile(resources, "drawable-xxhdpi/unused.png", new byte[0]);

        createFile(resources, "layout/activity_main.xml", ""
                + "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:paddingLeft=\"@dimen/activity_horizontal_margin\"\n"
                + "    android:paddingRight=\"@dimen/activity_horizontal_margin\"\n"
                + "    android:paddingTop=\"@dimen/activity_vertical_margin\"\n"
                + "    android:paddingBottom=\"@dimen/activity_vertical_margin\"\n"
                + "    tools:context=\".MainActivity\">\n"
                + "\n"
                + "    <TextView\n"
                + "        style=\"@style/MyStyle.Child\"\n"
                + "        android:text=\"@string/hello_world\"\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\" />\n"
                + "\n"
                + "</RelativeLayout>");

        createFile(resources, "menu/main.xml", ""
                + "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    tools:context=\".MainActivity\" >\n"
                + "    <item android:id=\"@+id/action_settings\"\n"
                + "        android:title=\"@string/action_settings\"\n"
                + "        android:orderInCategory=\"100\"\n"
                + "        android:showAsAction=\"never\" />\n"
                + "</menu>");

        createFile(resources, "menu/menu2.xml", ""
                + "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                + "    tools:context=\".MainActivity\" >\n"
                + "    <item android:id=\"@+id/action_settings2\"\n"
                + "        android:title=\"@string/action_settings2\"\n"
                + "        android:orderInCategory=\"100\"\n"
                + "        android:showAsAction=\"never\" />\n"
                + "</menu>");

        createFile(resources, "values/values.xml", ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "\n"
                + "    <attr name=\"myAttr1\" format=\"integer\" />\n"
                + "    <attr name=\"myAttr2\" format=\"boolean\" />\n"
                + "\n"
                + "    <dimen name=\"activity_horizontal_margin\">16dp</dimen>\n"
                + "    <dimen name=\"activity_vertical_margin\">16dp</dimen>\n"
                + "\n"
                + "    <string name=\"action_settings\">Settings</string>\n"
                + "    <string name=\"action_settings2\">Settings2</string>\n"
                + "    <string name=\"alias\"> @string/app_name </string>\n"
                + "    <string name=\"app_name\">ShrinkUnitTest</string>\n"
                + "    <string name=\"hello_world\">Hello world!</string>\n"
                + "\n"
                + "    <style name=\"AppTheme\" parent=\"android:Theme.Holo\"></style>\n"
                + "\n"
                + "    <style name=\"MyStyle\">\n"
                + "        <item name=\"myAttr1\">50</item>\n"
                + "    </style>\n"
                + "\n"
                + "    <style name=\"MyStyle.Child\">\n"
                + "        <item name=\"myAttr2\">true</item>\n"
                + "    </style>\n"
                + "\n"
                + "</resources>");

        createFile(resources, "raw/android_wear_micro_apk.apk",
                "<binary data>");

        createFile(resources, "xml/android_wear_micro_apk.xml", ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<wearableApp package=\"com.example.shrinkunittest.app\">\n"
                + "    <versionCode>1</versionCode>\n"
                + "    <versionName>1.0' platformBuildVersionName='5.0-1521886</versionName>\n"
                + "    <rawPathResId>android_wear_micro_apk</rawPathResId>\n"
                + "</wearableApp>");

        if (addKeepXml) {
            createFile(
                    resources,
                    "raw/keep.xml",
                    ""
                            + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                            + "<resources xmlns:tools=\"http://schemas.android.com/tools\"\n"
                            + "    tools:keep=\"@drawable/avd_heart_fill_1\" "
                            + "    tools:discard=\"@menu/main\" />");
        }

        // RAW content for HTML/web
        createFile(
                resources,
                "raw/index1.html",
                ""
                        // TODO: Test single quotes, attribute without quotes, spaces around = etc,
                        // prologue, xhtml
                        + "<!DOCTYPE html>\n"
                        + "<html>\n"
                        + "<!--\n"
                        + " Blah blah\n"
                        + "-->\n"
                        + "<head>\n"
                        + "  <meta charset=\"utf-8\">\n"
                        + "  <link href=\"http://fonts.googleapis.com/css?family=Alegreya:400italic,900italic|Alegreya+Sans:300\" rel=\"stylesheet\">\n"
                        + "  <link href=\"http://yui.yahooapis.com/2.8.0r4/build/reset/reset-min.css\" rel=\"stylesheet\">\n"
                        + "  <link href=\"static/landing.css\" rel=\"stylesheet\">\n"
                        + "  <script src=\"http://ajax.googleapis.com/ajax/libs/jquery/2.0.3/jquery.min.js\"></script>\n"
                        + "  <script src=\"static/modernizr.custom.14469.js\"></script>\n"
                        + "  <meta name=\"viewport\" content=\"width=690\">\n"
                        + "  <style type=\"text/css\">\n"
                        + "html, body {\n"
                        + "  margin: 0;\n"
                        + "  height: 100%;\n"
                        + "  background-image: url(file:///android_res/raw/my_used_raw_drawable);\n"
                        + "}\n"
                        + "</style>"
                        + "</head>\n"
                        + "<body>\n"
                        + "\n"
                        + "<div id=\"container\">\n"
                        + "\n"
                        + "  <div id=\"logo\"></div>\n"
                        + "\n"
                        + "  <div id=\"text\">\n"
                        + "    <p>\n"
                        + "      More ignored text here\n"
                        + "    </p>\n"
                        + "  </div>\n"
                        + "\n"
                        + "  <a id=\"playlink\" href=\"file/foo.png\">&nbsp;</a>\n"
                        + "</div>\n"
                        + "<script>\n"
                        + "\n"
                        + "if (Modernizr.cssanimations &&\n"
                        + "    Modernizr.svg &&\n"
                        + "    Modernizr.csstransforms3d &&\n"
                        + "    Modernizr.csstransitions) {\n"
                        + "\n"
                        + "  // progressive enhancement\n"
                        + "  $('#device-screen').css('display', 'block');\n"
                        + "  $('#device-frame').css('background-image', 'url( 'drawable-mdpi/tilted.png')' );\n"
                        + "  $('#opentarget').css('visibility', 'visible');\n"
                        + "  $('body').addClass('withvignette');\n"
                        + "</script>\n"
                        + "\n"
                        + "</body>\n"
                        + "</html>");

        createFile(resources, "raw/styles2.css", ""
                + "/**\n"
                + " * Copyright 2014 Google Inc.\n"
                + " */\n"
                + "\n"
                + "html, body {\n"
                + "  margin: 0;\n"
                + "  height: 100%;\n"
                + "  -webkit-font-smoothing: antialiased;\n"
                + "}\n"
                + "#logo {\n"
                + "  position: absolute;\n"
                + "  left: 0;\n"
                + "  top: 60px;\n"
                + "  width: 250px;\n"
                + "  height: 102px;\n"
                + "  background-image: url(img2.png);\n"
                + "  background-repeat: no-repeat;\n"
                + "  background-size: contain;\n"
                + "  opacity: 0.7;\n"
                + "  z-index: 100;\n"
                + "}\n"
                + "device-frame {\n"
                + "  position: absolute;\n"
                + "  right: -70px;\n"
                + "  top: 0;\n"
                + "  width: 420px;\n"
                + "  height: 500px;\n"
                + "  background-image: url(tilted_fallback.jpg);\n"
                + "  background-size: cover;\n"
                + "  -webkit-user-select: none;\n"
                + "  -moz-user-select: none;\n"
                + "}");

        createFile(resources, "raw/my_js.js", ""
                + "function $(id) {\n"
                + "  return document.getElementById(id);\n"
                + "}\n"
                + "\n"
                + "/* Ignored block comment: \"ignore me\" */\n"
                + "function show(id) {\n"
                + "  $(id).style.display = \"block\";\n"
                + "}\n"
                + "\n"
                + "function hide(id) {\n"
                + "  $(id).style.display = \"none\";\n"
                + "}\n"
                + "// Line comment\n"
                + "function onStatusBoxFocus(elt) {\n"
                + "  elt.value = '';\n"
                + "  elt.style.color = \"#000\";\n"
                + "  show('status_submit');\n"
                + "}\n");

        // Nested resources
        createFile(resources, "drawable/avd_heart_fill.xml", ""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<animated-vector\n"
                + "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    xmlns:aapt=\"http://schemas.android.com/aapt\">\n"
                + "\n"
                + "    <aapt:attr name=\"android:drawable\">\n"
                + "        <vector\n"
                + "            android:width=\"56dp\"\n"
                + "            android:height=\"56dp\"\n"
                + "            android:viewportWidth=\"56\"\n"
                + "            android:viewportHeight=\"56\">\n"
                + "        </vector>\n"
                + "    </aapt:attr>\n"
                + "\n"
                + "    <target android:name=\"clip\">\n"
                + "        <aapt:attr name=\"android:animation\">\n"
                + "            <objectAnimator\n"
                + "                android:propertyName=\"pathData\"\n"
                + "                android:interpolator=\"@android:interpolator/fast_out_slow_in\" />\n"
                + "        </aapt:attr>\n"
                + "    </target>\n"
                + "</animated-vector>");

        return resources;
    }

    private static File createMergedManifest(File dir) throws IOException {
        return createFile(dir, "app/build/manifests/release/AndroidManifest.xml", ""
                    + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" android:versionCode=\"1\" android:versionName=\"1.0\" package=\"com.example.shrinkunittest.app\">\n"
                    + "    <uses-sdk android:minSdkVersion=\"20\" android:targetSdkVersion=\"19\"/>\n"
                    + "\n"
                    + "    <application android:allowBackup=\"true\" android:icon=\"@drawable/ic_launcher\" android:label=\"@string/app_name\">\n"
                    + "        <activity android:label=\"@string/app_name\" android:name=\"com.example.shrinkunittest.app.MainActivity\">\n"
                    + "            <intent-filter>\n"
                    + "                <action android:name=\"android.intent.action.MAIN\"/>\n"
                    + "\n"
                    + "                <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
                    + "            </intent-filter>\n"
                    + "        </activity>\n"
                    + "        <meta-data\n"
                    + "            android:name=\"com.google.android.wearable.beta.app\"\n"
                    + "            android:resource=\"@xml/android_wear_micro_apk\" />"
                    + "    </application>\n"
                    + "\n"
                    + "</manifest>");
    }

    private File createResourceSources(File dir) throws IOException {
        if (rVarient == RVarient.R_JAR) {
            return TestResources.getFile(ResourceUsageAnalyzerTest.class, "R.jar");
        }
        File rDir = new File(dir, "app/build/source/r/release".replace('/', separatorChar));
        rDir.mkdirs();
        if (rVarient == RVarient.R_JAVA) {
            createFile(
                    rDir,
                    "com/example/shrinkunittest/app/R.java",
                    ""
                            + "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n"
                            + " *\n"
                            + " * This class was automatically generated by the\n"
                            + " * aapt tool from the resource data it found.  It\n"
                            + " * should not be modified by hand.\n"
                            + " */\n"
                            + "\n"
                            + "package com.example.shrinkunittest.app;\n"
                            + "\n"
                            + "public final class R {\n"
                            + "    public static final class attr {\n"
                            + "        /** <p>Must be an integer value, such as \"<code>100</code>\".\n"
                            + "<p>This may also be a reference to a resource (in the form\n"
                            + "\"<code>@[<i>package</i>:]<i>type</i>:<i>name</i></code>\") or\n"
                            + "theme attribute (in the form\n"
                            + "\"<code>?[<i>package</i>:][<i>type</i>:]<i>name</i></code>\")\n"
                            + "containing a value of this type.\n"
                            + "         */\n"
                            + "        public static final int myAttr1=0x7f010000;\n"
                            + "        /** <p>Must be a boolean value, either \"<code>true</code>\" or \"<code>false</code>\".\n"
                            + "<p>This may also be a reference to a resource (in the form\n"
                            + "\"<code>@[<i>package</i>:]<i>type</i>:<i>name</i></code>\") or\n"
                            + "theme attribute (in the form\n"
                            + "\"<code>?[<i>package</i>:][<i>type</i>:]<i>name</i></code>\")\n"
                            + "containing a value of this type.\n"
                            + "         */\n"
                            + "        public static final int myAttr2=0x7f010001;\n"
                            + "    }\n"
                            + "    public static final class dimen {\n"
                            + "        public static final int activity_horizontal_margin=0x7f040000;\n"
                            + "        public static final int activity_vertical_margin=0x7f040001;\n"
                            + "    }\n"
                            + "    public static final class drawable {\n"
                            + "        public static final int ic_launcher=0x7f020000;\n"
                            + "        public static final int unused=0x7f020001;\n"
                            + "    }\n"
                            + "    public static final class id {\n"
                            + "        public static final int action_settings=0x7f080000;\n"
                            + "        public static final int action_settings2=0x7f080001;\n"
                            + "    }\n"
                            + "    public static final class layout {\n"
                            + "        public static final int activity_main=0x7f030000;\n"
                            + "    }\n"
                            + "    public static final class menu {\n"
                            + "        public static final int main=0x7f070000;\n"
                            + "    }\n"
                            + "    public static final class raw {\n"
                            + "        public static final int android_wear_micro_apk=0x7f090000;\n"
                            + "        public static final int index1=0x7f090001;\n"
                            + "        public static final int styles2=0x7f090002;\n"
                            + "        public static final int my_js=0x7f090003;\n"
                            + "        public static final int my_used_raw_drawable=0x7f090004;\n"
                            + "    }"
                            + "    public static final class string {\n"
                            + "        public static final int action_settings=0x7f050000;\n"
                            + "        public static final int action_settings2=0x7f050004;\n"
                            + "        public static final int alias=0x7f050001;\n"
                            + "        public static final int app_name=0x7f050002;\n"
                            + "        public static final int hello_world=0x7f050003;\n"
                            + "    }\n"
                            + "    public static final class style {\n"
                            + "        public static final int AppTheme=0x7f060000;\n"
                            + "        public static final int MyStyle=0x7f060001;\n"
                            + "        public static final int MyStyle_Child=0x7f060002;\n"
                            + "    }\n"
                            + "    public static final class xml {\n"
                            + "        public static final int android_wear_micro_apk=0x7f0a0000;\n"
                            + "    }"
                            + "}");
        }
        if (rVarient == RVarient.R_TXT) {
            createFile(
                    rDir,
                    "com/example/shrinkunittest/app/R.txt",
                    "int attr myAttr1 0x7f010000\n"
                            + "int attr myAttr2 0x7f010001\n"
                            + "int dimen activity_horizontal_margin 0x7f040000\n"
                            + "int dimen activity_vertical_margin 0x7f040001\n"
                            + "int drawable ic_launcher 0x7f020000\n"
                            + "int drawable unused 0x7f020001\n"
                            + "int id action_settings 0x7f080000\n"
                            + "int id action_settings2 0x7f080001\n"
                            + "int layout activity_main 0x7f030000\n"
                            + "int menu main 0x7f070000\n"
                            + "int raw android_wear_micro_apk 0x7f090000\n"
                            + "int raw index1 0x7f090001\n"
                            + "int raw styles2 0x7f090002\n"
                            + "int raw my_js 0x7f090003\n"
                            + "int raw my_used_raw_drawable 0x7f090004\n"
                            + "int string action_settings 0x7f050000\n"
                            + "int string action_settings2 0x7f050004\n"
                            + "int string alias 0x7f050001\n"
                            + "int string app_name 0x7f050002\n"
                            + "int string hello_world 0x7f050003\n"
                            + "int style AppTheme 0x7f060000\n"
                            + "int style MyStyle 0x7f060001\n"
                            + "int style MyStyle_Child 0x7f060002\n"
                            + "int xml android_wear_micro_apk 0x7f0a0000\n");
        }
        return rDir;
    }

    private static File createProguardedClasses(File dir) throws IOException {
        byte[] bytecode = new byte[] {
                (byte)80, (byte)75, (byte)3, (byte)4, (byte)20, (byte)0, (byte)8, (byte)0,
                (byte)8, (byte)0, (byte)12, (byte)88, (byte)-62, (byte)68, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)49, (byte)0, (byte)0, (byte)0, (byte)99, (byte)111,
                (byte)109, (byte)47, (byte)101, (byte)120, (byte)97, (byte)109, (byte)112, (byte)108,
                (byte)101, (byte)47, (byte)115, (byte)104, (byte)114, (byte)105, (byte)110, (byte)107,
                (byte)117, (byte)110, (byte)105, (byte)116, (byte)116, (byte)101, (byte)115, (byte)116,
                (byte)47, (byte)97, (byte)112, (byte)112, (byte)47, (byte)77, (byte)97, (byte)105,
                (byte)110, (byte)65, (byte)99, (byte)116, (byte)105, (byte)118, (byte)105, (byte)116,
                (byte)121, (byte)46, (byte)99, (byte)108, (byte)97, (byte)115, (byte)115, (byte)117,
                (byte)-110, (byte)-53, (byte)78, (byte)-37, (byte)64, (byte)20, (byte)-122, (byte)-1,
                (byte)-63, (byte)14, (byte)14, (byte)97, (byte)32, (byte)36, (byte)36, (byte)-31,
                (byte)82, (byte)110, (byte)-31, (byte)-46, (byte)58, (byte)-15, (byte)-62, (byte)82,
                (byte)-73, (byte)-127, (byte)74, (byte)-112, (byte)-107, (byte)-91, (byte)-94, (byte)46,
                (byte)-112, (byte)88, (byte)-80, (byte)-77, (byte)-30, (byte)1, (byte)70, (byte)36,
                (byte)-29, (byte)40, (byte)-98, (byte)-92, (byte)101, (byte)-59, (byte)-85, (byte)-16,
                (byte)0, (byte)108, (byte)-70, (byte)73, (byte)-44, (byte)46, (byte)-6, (byte)0,
                (byte)60, (byte)20, (byte)-22, (byte)-103, (byte)-60, (byte)80, (byte)110, (byte)-99,
                (byte)-111, (byte)-50, (byte)57, (byte)-93, (byte)-1, (byte)59, (byte)23, (byte)-23,
                (byte)-52, (byte)-3, (byte)-61, (byte)-17, (byte)63, (byte)0, (byte)62, (byte)-61,
                (byte)-77, (byte)110, (byte)44, (byte)-64, (byte)-70, (byte)113, (byte)-116, (byte)-55,
                (byte)2, (byte)14, (byte)-74, (byte)28, (byte)84, (byte)29, (byte)108, (byte)59,
                (byte)-40, (byte)-55, (byte)-63, (byte)70, (byte)-34, (byte)-104, (byte)69, (byte)99,
                (byte)74, (byte)57, (byte)100, (byte)80, (byte)-52, (byte)17, (byte)81, (byte)48,
                (byte)-90, (byte)60, (byte)-117, (byte)105, (byte)44, (byte)112, (byte)108, (byte)96,
                (byte)-103, (byte)99, (byte)23, (byte)21, (byte)-114, (byte)61, (byte)44, (byte)113,
                (byte)124, (byte)-60, (byte)42, (byte)-57, (byte)39, (byte)124, (byte)-32, (byte)-88,
                (byte)97, (byte)-99, (byte)-93, (byte)-114, (byte)21, (byte)6, (byte)-53, (byte)-83,
                (byte)5, (byte)12, (byte)-21, (byte)110, (byte)-19, (byte)107, (byte)-88, (byte)-94,
                (byte)94, (byte)44, (byte)35, (byte)127, (byte)32, (byte)-59, (byte)119, (byte)-1,
                (byte)88, (byte)-88, (byte)126, (byte)-96, (byte)-50, (byte)-37, (byte)-95, (byte)22,
                (byte)-67, (byte)-58, (byte)-104, (byte)58, (byte)101, (byte)-80, (byte)-35, (byte)-64,
                (byte)-72, (byte)37, (byte)55, (byte)120, (byte)11, (byte)55, (byte)-116, (byte)82,
                (byte)113, (byte)-97, (byte)-124, (byte)56, (byte)-15, (byte)-113, (byte)-6, (byte)42,
                (byte)106, (byte)-117, (byte)-41, (byte)-62, (byte)-77, (byte)-116, (byte)51, (byte)-122,
                (byte)-43, (byte)119, (byte)-124, (byte)64, (byte)-117, (byte)-50, (byte)88, (byte)-100,
                (byte)-34, (byte)-105, (byte)74, (byte)-22, (byte)47, (byte)-44, (byte)-72, (byte)25,
                (byte)71, (byte)-126, (byte)-95, (byte)-12, (byte)-120, (byte)-122, (byte)-35, (byte)-82,
                (byte)127, (byte)-40, (byte)-46, (byte)114, (byte)32, (byte)-11, (byte)53, (byte)-61,
                (byte)-54, (byte)127, (byte)39, (byte)103, (byte)40, (byte)-65, (byte)91, (byte)-99,
                (byte)-63, (byte)107, (byte)-59, (byte)29, (byte)95, (byte)-4, (byte)8, (byte)59,
                (byte)-35, (byte)-74, (byte)-16, (byte)-109, (byte)-53, (byte)-98, (byte)84, (byte)87,
                (byte)125, (byte)-22, (byte)-91, (byte)69, (byte)-94, (byte)-57, (byte)-43, (byte)-113,
                (byte)67, (byte)-87, (byte)-2, (byte)117, (byte)-104, (byte)-71, (byte)16, (byte)-38,
                (byte)-28, (byte)5, (byte)17, (byte)67, (byte)-98, (byte)-30, (byte)-105, (byte)61,
                (byte)28, (byte)57, (byte)9, (byte)25, (byte)-78, (byte)-79, (byte)106, (byte)-10,
                (byte)-60, (byte)56, (byte)92, (byte)124, (byte)12, (byte)-65, (byte)117, (byte)-75,
                (byte)-116, (byte)85, (byte)98, (byte)82, (byte)104, (byte)-100, (byte)88, (byte)-91,
                (byte)111, (byte)83, (byte)-18, (byte)68, (byte)-76, (byte)69, (byte)75, (byte)11,
                (byte)42, (byte)58, (byte)-97, (byte)8, (byte)-35, (byte)-116, (byte)-107, (byte)22,
                (byte)74, (byte)-97, (byte)-46, (byte)-96, (byte)-88, (byte)-46, (byte)18, (byte)109,
                (byte)-104, (byte)99, (byte)-125, (byte)-103, (byte)53, (byte)-110, (byte)-35, (byte)-92,
                (byte)87, (byte)-127, (byte)60, (byte)35, (byte)-97, (byte)-87, (byte)-113, (byte)-112,
                (byte)-3, (byte)-103, (byte)2, (byte)-76, (byte)-47, (byte)84, (byte)94, (byte)-58,
                (byte)20, (byte)93, (byte)-128, (byte)-41, (byte)-67, (byte)17, (byte)102, (byte)-22,
                (byte)69, (byte)54, (byte)-60, (byte)-36, (byte)-124, (byte)98, (byte)112, (byte)-79,
                (byte)-10, (byte)68, (byte)89, (byte)41, (byte)53, (byte)4, (byte)47, (byte)78,
                (byte)121, (byte)67, (byte)-52, (byte)-38, (byte)119, (byte)41, (byte)69, (byte)31,
                (byte)35, (byte)-91, (byte)-86, (byte)-60, (byte)-48, (byte)-17, (byte)67, (byte)-39,
                (byte)-5, (byte)-123, (byte)121, (byte)-122, (byte)-125, (byte)-75, (byte)-94, (byte)117,
                (byte)-117, (byte)-116, (byte)125, (byte)103, (byte)74, (byte)-25, (byte)38, (byte)56,
                (byte)-2, (byte)2, (byte)80, (byte)75, (byte)7, (byte)8, (byte)39, (byte)-48,
                (byte)-69, (byte)-98, (byte)-101, (byte)1, (byte)0, (byte)0, (byte)-86, (byte)2,
                (byte)0, (byte)0, (byte)80, (byte)75, (byte)1, (byte)2, (byte)20, (byte)0,
                (byte)20, (byte)0, (byte)8, (byte)0, (byte)8, (byte)0, (byte)12, (byte)88,
                (byte)-62, (byte)68, (byte)39, (byte)-48, (byte)-69, (byte)-98, (byte)-101, (byte)1,
                (byte)0, (byte)0, (byte)-86, (byte)2, (byte)0, (byte)0, (byte)49, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)99, (byte)111, (byte)109, (byte)47, (byte)101, (byte)120, (byte)97, (byte)109,
                (byte)112, (byte)108, (byte)101, (byte)47, (byte)115, (byte)104, (byte)114, (byte)105,
                (byte)110, (byte)107, (byte)117, (byte)110, (byte)105, (byte)116, (byte)116, (byte)101,
                (byte)115, (byte)116, (byte)47, (byte)97, (byte)112, (byte)112, (byte)47, (byte)77,
                (byte)97, (byte)105, (byte)110, (byte)65, (byte)99, (byte)116, (byte)105, (byte)118,
                (byte)105, (byte)116, (byte)121, (byte)46, (byte)99, (byte)108, (byte)97, (byte)115,
                (byte)115, (byte)80, (byte)75, (byte)5, (byte)6, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)1, (byte)0, (byte)1, (byte)0, (byte)95, (byte)0, (byte)0,
                (byte)0, (byte)-6, (byte)1, (byte)0, (byte)0, (byte)0, (byte)0
        };
        return createFile(dir, "app/build/classes-proguard/release/classes.jar", bytecode);
    }

    private static File createUnproguardedClasses(File dir) throws IOException {
        byte [] bytecode = new byte[] {
                (byte)80, (byte)75, (byte)3, (byte)4, (byte)20, (byte)0, (byte)8, (byte)0,
                (byte)8, (byte)0, (byte)-11, (byte)-127, (byte)-61, (byte)68, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)9, (byte)0, (byte)4, (byte)0, (byte)77, (byte)69,
                (byte)84, (byte)65, (byte)45, (byte)73, (byte)78, (byte)70, (byte)47, (byte)-2,
                (byte)-54, (byte)0, (byte)0, (byte)3, (byte)0, (byte)80, (byte)75, (byte)7,
                (byte)8, (byte)0, (byte)0, (byte)0, (byte)0, (byte)2, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)80, (byte)75, (byte)3,
                (byte)4, (byte)20, (byte)0, (byte)8, (byte)0, (byte)8, (byte)0, (byte)-11,
                (byte)-127, (byte)-61, (byte)68, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)20,
                (byte)0, (byte)0, (byte)0, (byte)77, (byte)69, (byte)84, (byte)65, (byte)45,
                (byte)73, (byte)78, (byte)70, (byte)47, (byte)77, (byte)65, (byte)78, (byte)73,
                (byte)70, (byte)69, (byte)83, (byte)84, (byte)46, (byte)77, (byte)70, (byte)-13,
                (byte)77, (byte)-52, (byte)-53, (byte)76, (byte)75, (byte)45, (byte)46, (byte)-47,
                (byte)13, (byte)75, (byte)45, (byte)42, (byte)-50, (byte)-52, (byte)-49, (byte)-77,
                (byte)82, (byte)48, (byte)-44, (byte)51, (byte)-32, (byte)-27, (byte)114, (byte)46,
                (byte)74, (byte)77, (byte)44, (byte)73, (byte)77, (byte)-47, (byte)117, (byte)-86,
                (byte)4, (byte)9, (byte)-104, (byte)-23, (byte)25, (byte)-60, (byte)-101, (byte)-103,
                (byte)42, (byte)104, (byte)56, (byte)22, (byte)20, (byte)-28, (byte)-92, (byte)42,
                (byte)120, (byte)-26, (byte)37, (byte)-21, (byte)105, (byte)-14, (byte)114, (byte)-15,
                (byte)114, (byte)1, (byte)0, (byte)80, (byte)75, (byte)7, (byte)8, (byte)127,
                (byte)71, (byte)56, (byte)-57, (byte)60, (byte)0, (byte)0, (byte)0, (byte)60,
                (byte)0, (byte)0, (byte)0, (byte)80, (byte)75, (byte)3, (byte)4, (byte)20,
                (byte)0, (byte)8, (byte)0, (byte)8, (byte)0, (byte)-49, (byte)-127, (byte)-61,
                (byte)68, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)49, (byte)0, (byte)0,
                (byte)0, (byte)99, (byte)111, (byte)109, (byte)47, (byte)101, (byte)120, (byte)97,
                (byte)109, (byte)112, (byte)108, (byte)101, (byte)47, (byte)115, (byte)104, (byte)114,
                (byte)105, (byte)110, (byte)107, (byte)117, (byte)110, (byte)105, (byte)116, (byte)116,
                (byte)101, (byte)115, (byte)116, (byte)47, (byte)97, (byte)112, (byte)112, (byte)47,
                (byte)77, (byte)97, (byte)105, (byte)110, (byte)65, (byte)99, (byte)116, (byte)105,
                (byte)118, (byte)105, (byte)116, (byte)121, (byte)46, (byte)99, (byte)108, (byte)97,
                (byte)115, (byte)115, (byte)-107, (byte)-109, (byte)-51, (byte)82, (byte)19, (byte)65,
                (byte)16, (byte)-57, (byte)-1, (byte)-109, (byte)4, (byte)-106, (byte)-124, (byte)13,
                (byte)-127, (byte)64, (byte)-126, (byte)32, (byte)72, (byte)16, (byte)-112, (byte)124,
                (byte)8, (byte)43, (byte)-8, (byte)45, (byte)-96, (byte)66, (byte)4, (byte)-115,
                (byte)5, (byte)90, (byte)37, (byte)22, (byte)7, (byte)47, (byte)-44, (byte)-110,
                (byte)-116, (byte)48, (byte)-78, (byte)-103, (byte)77, (byte)101, (byte)39, (byte)81,
                (byte)-34, (byte)-58, (byte)7, (byte)-32, (byte)34, (byte)7, (byte)40, (byte)61,
                (byte)-8, (byte)0, (byte)62, (byte)-109, (byte)101, (byte)-39, (byte)-77, (byte)89,
                (byte)80, (byte)-85, (byte)66, (byte)-91, (byte)-36, (byte)-61, (byte)-12, (byte)108,
                (byte)79, (byte)-9, (byte)111, (byte)122, (byte)-2, (byte)-45, (byte)-13, (byte)-29,
                (byte)-41, (byte)-73, (byte)-17, (byte)0, (byte)22, (byte)-15, (byte)50, (byte)6,
                (byte)19, (byte)51, (byte)122, (byte)-72, (byte)17, (byte)-59, (byte)44, (byte)-78,
                (byte)49, (byte)-12, (byte)34, (byte)-89, (byte)-121, (byte)124, (byte)20, (byte)5,
                (byte)-36, (byte)-116, (byte)97, (byte)14, (byte)-13, (byte)-67, (byte)-80, (byte)112,
                (byte)43, (byte)-118, (byte)5, (byte)44, (byte)-22, (byte)-72, (byte)-37, (byte)6,
                (byte)-18, (byte)24, (byte)-72, (byte)-53, (byte)-48, (byte)-67, (byte)44, (byte)-92,
                (byte)80, (byte)-113, (byte)25, (byte)-62, (byte)-39, (byte)-36, (byte)14, (byte)67,
                (byte)-92, (byte)-24, (byte)86, (byte)56, (byte)67, (byte)98, (byte)83, (byte)72,
                (byte)-2, (byte)-86, (byte)81, (byte)-35, (byte)-29, (byte)-11, (byte)-73, (byte)-10,
                (byte)-98, (byte)67, (byte)-98, (byte)-28, (byte)-90, (byte)91, (byte)-74, (byte)-99,
                (byte)29, (byte)-69, (byte)46, (byte)-12, (byte)127, (byte)-32, (byte)-116, (byte)-88,
                (byte)3, (byte)-31, (byte)49, (byte)-52, (byte)109, (byte)-106, (byte)-35, (byte)-86,
                (byte)-59, (byte)63, (byte)-39, (byte)-43, (byte)-102, (byte)-61, (byte)45, (byte)-17,
                (byte)-96, (byte)46, (byte)-28, (byte)97, (byte)-125, (byte)-88, (byte)-118, (byte)123,
                (byte)-54, (byte)-78, (byte)107, (byte)53, (byte)107, (byte)-53, (byte)22, (byte)114,
                (byte)-75, (byte)-84, (byte)68, (byte)83, (byte)-88, (byte)-93, (byte)37, (byte)-122,
                (byte)30, (byte)87, (byte)22, (byte)-21, (byte)-36, (byte)86, (byte)68, (byte)72,
                (byte)103, (byte)55, (byte)109, (byte)89, (byte)-87, (byte)-69, (byte)-94, (byte)98,
                (byte)-71, (byte)-98, (byte)-75, (byte)-42, (byte)-112, (byte)21, (byte)-121, (byte)47,
                (byte)-23, (byte)66, (byte)-110, (byte)-98, (byte)-35, (byte)-28, (byte)-107, (byte)-110,
                (byte)-12, (byte)-108, (byte)45, (byte)-53, (byte)124, (byte)91, (byte)-7, (byte)-47,
                (byte)-125, (byte)109, (byte)-126, (byte)-55, (byte)123, (byte)-114, (byte)123, (byte)93,
                (byte)83, (byte)-62, (byte)-107, (byte)-34, (byte)22, (byte)-105, (byte)-115, (byte)127,
                (byte)-56, (byte)77, (byte)-63, (byte)63, (byte)90, (byte)-38, (byte)-69, (byte)-108,
                (byte)123, (byte)71, (byte)69, (byte)87, (byte)-3, (byte)-11, (byte)-63, (byte)54,
                (byte)-53, (byte)12, (byte)41, (byte)87, (byte)6, (byte)-108, (byte)-110, (byte)-30,
                (byte)-43, (byte)109, (byte)-18, (byte)-16, (byte)-78, (byte)-30, (byte)21, (byte)-122,
                (byte)-47, (byte)54, (byte)52, (byte)29, (byte)-47, (byte)34, (byte)10, (byte)-102,
                (byte)49, (byte)12, (byte)95, (byte)18, (byte)-62, (byte)16, (byte)18, (byte)-124,
                (byte)96, (byte)37, (byte)-122, (byte)56, (byte)29, (byte)-92, (byte)124, (byte)-72,
                (byte)101, (byte)-41, (byte)2, (byte)1, (byte)99, (byte)-37, (byte)110, (byte)-93,
                (byte)94, (byte)-26, (byte)27, (byte)66, (byte)-1, (byte)12, (byte)-4, (byte)45,
                (byte)-45, (byte)-4, (byte)7, (byte)-69, (byte)105, (byte)-101, (byte)-120, (byte)-93,
                (byte)-49, (byte)-60, (byte)16, (byte)82, (byte)6, (byte)-18, (byte)-101, (byte)120,
                (byte)-124, (byte)73, (byte)19, (byte)75, (byte)88, (byte)54, (byte)-79, (byte)-126,
                (byte)-57, (byte)6, (byte)-98, (byte)-104, (byte)120, (byte)-118, (byte)73, (byte)3,
                (byte)-85, (byte)38, (byte)-42, (byte)80, (byte)52, (byte)-16, (byte)-52, (byte)-60,
                (byte)58, (byte)54, (byte)12, (byte)60, (byte)55, (byte)-15, (byte)66, (byte)71,
                (byte)-114, (byte)97, (byte)-100, (byte)-95, (byte)-16, (byte)31, (byte)87, (byte)-61,
                (byte)48, (byte)116, (byte)126, (byte)2, (byte)-67, (byte)116, (byte)-18, (byte)54,
                (byte)64, (byte)-107, (byte)-49, (byte)118, (byte)-32, (byte)-68, (byte)-103, (byte)118,
                (byte)-20, (byte)35, (byte)-73, (byte)-95, (byte)-88, (byte)-93, (byte)-50, (byte)39,
                (byte)102, (byte)73, (byte)74, (byte)94, (byte)47, (byte)58, (byte)-74, (byte)-25,
                (byte)113, (byte)-22, (byte)-110, (byte)-72, (byte)29, (byte)-16, (byte)118, (byte)-85,
                (byte)-76, (byte)39, (byte)67, (byte)-97, (byte)-57, (byte)85, (byte)-47, (byte)-107,
                (byte)-118, (byte)75, (byte)-75, (byte)67, (byte)122, (byte)-111, (byte)-116, (byte)-39,
                (byte)-110, (byte)-66, (byte)-7, (byte)-60, (byte)62, (byte)87, (byte)-66, (byte)118,
                (byte)-14, (byte)-67, (byte)67, (byte)-105, (byte)90, (byte)103, (byte)24, (byte)-49,
                (byte)-26, (byte)-38, (byte)72, (byte)27, (byte)44, (byte)-109, (byte)-68, (byte)51,
                (byte)29, (byte)107, (byte)107, (byte)93, (byte)121, (byte)-92, (byte)-75, (byte)-15,
                (byte)-56, (byte)-91, (byte)44, (byte)6, (byte)67, (byte)-76, (byte)-90, (byte)116,
                (byte)-101, (byte)-39, (byte)82, (byte)-69, (byte)6, (byte)-94, (byte)2, (byte)83,
                (byte)109, (byte)-81, (byte)-103, (byte)33, (byte)74, (byte)-123, (byte)-21, (byte)89,
                (byte)-87, (byte)-30, (byte)-65, (byte)38, (byte)18, (byte)109, (byte)-86, (byte)99,
                (byte)97, (byte)-70, (byte)49, (byte)18, (byte)90, (byte)24, (byte)87, (byte)-18,
                (byte)-110, (byte)30, (byte)74, (byte)-56, (byte)125, (byte)-110, (byte)42, (byte)-45,
                (byte)41, (byte)15, (byte)-109, (byte)-12, (byte)-72, (byte)77, (byte)-24, (byte)47,
                (byte)2, (byte)-90, (byte)-69, (byte)-124, (byte)-58, (byte)4, (byte)-3, (byte)89,
                (byte)100, (byte)25, (byte)-39, (byte)-82, (byte)-4, (byte)25, (byte)-40, (byte)23,
                (byte)-102, (byte)-124, (byte)-48, (byte)79, (byte)99, (byte)-73, (byte)-17, (byte)-116,
                (byte)98, (byte)-128, (byte)70, (byte)-77, (byte)21, (byte)-128, (byte)36, (byte)6,
                (byte)-3, (byte)116, (byte)-22, (byte)-82, (byte)32, (byte)-71, (byte)68, (byte)-47,
                (byte)33, (byte)-78, (byte)-15, (byte)124, (byte)-31, (byte)12, (byte)-95, (byte)-4,
                (byte)9, (byte)-62, (byte)-89, (byte)-120, (byte)-4, (byte)-127, (byte)-12, (byte)33,
                (byte)-84, (byte)23, (byte)41, (byte)-75, (byte)-113, (byte)32, (byte)9, (byte)31,
                (byte)-106, (byte)110, (byte)37, (byte)4, (byte)48, (byte)61, (byte)75, (byte)99,
                (byte)-40, (byte)-81, (byte)-31, (byte)10, (byte)70, (byte)2, (byte)-20, (byte)58,
                (byte)-27, (byte)-75, (byte)-80, (byte)-89, (byte)-24, (byte)58, (byte)65, (byte)119,
                (byte)-31, (byte)20, (byte)70, (byte)-28, (byte)-8, (byte)2, (byte)27, (byte)-13,
                (byte)23, (byte)83, (byte)116, (byte)-96, (byte)-12, (byte)37, (byte)-56, (byte)81,
                (byte)92, (byte)-11, (byte)-111, (byte)-44, (byte)-48, (byte)1, (byte)-46, (byte)-95,
                (byte)24, (byte)93, (byte)76, (byte)-70, (byte)-16, (byte)21, (byte)61, (byte)12,
                (byte)43, (byte)99, (byte)39, (byte)-120, (byte)126, (byte)70, (byte)87, (byte)-28,
                (byte)88, (byte)87, (byte)30, (byte)-45, (byte)-20, (byte)-80, (byte)-49, (byte)78,
                (byte)-46, (byte)-7, (byte)-128, (byte)107, (byte)48, (byte)48, (byte)65, (byte)69,
                (byte)103, (byte)-56, (byte)119, (byte)-35, (byte)-33, (byte)35, (byte)-45, (byte)-54,
                (byte)-66, (byte)-40, (byte)35, (byte)77, (byte)49, (byte)19, (byte)-60, (byte)54,
                (byte)-120, (byte)-98, (byte)33, (byte)113, (byte)67, (byte)20, (byte)-25, (byte)-85,
                (byte)-10, (byte)19, (byte)-3, (byte)-12, (byte)124, (byte)49, (byte)-27, (byte)87,
                (byte)59, (byte)-115, (byte)-121, (byte)100, (byte)71, (byte)41, (byte)119, (byte)22,
                (byte)-9, (byte)-16, (byte)-128, (byte)14, (byte)88, (byte)32, (byte)59, (byte)74,
                (byte)118, (byte)-127, (byte)108, (byte)6, (byte)35, (byte)-65, (byte)1, (byte)80,
                (byte)75, (byte)7, (byte)8, (byte)-67, (byte)119, (byte)-82, (byte)40, (byte)-35,
                (byte)2, (byte)0, (byte)0, (byte)-111, (byte)5, (byte)0, (byte)0, (byte)80,
                (byte)75, (byte)3, (byte)4, (byte)20, (byte)0, (byte)8, (byte)0, (byte)8,
                (byte)0, (byte)-49, (byte)-127, (byte)-61, (byte)68, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)48, (byte)0, (byte)0, (byte)0, (byte)99, (byte)111, (byte)109,
                (byte)47, (byte)101, (byte)120, (byte)97, (byte)109, (byte)112, (byte)108, (byte)101,
                (byte)47, (byte)115, (byte)104, (byte)114, (byte)105, (byte)110, (byte)107, (byte)117,
                (byte)110, (byte)105, (byte)116, (byte)116, (byte)101, (byte)115, (byte)116, (byte)47,
                (byte)97, (byte)112, (byte)112, (byte)47, (byte)66, (byte)117, (byte)105, (byte)108,
                (byte)100, (byte)67, (byte)111, (byte)110, (byte)102, (byte)105, (byte)103, (byte)46,
                (byte)99, (byte)108, (byte)97, (byte)115, (byte)115, (byte)-115, (byte)81, (byte)-37,
                (byte)110, (byte)-45, (byte)64, (byte)16, (byte)61, (byte)-45, (byte)92, (byte)-20,
                (byte)4, (byte)-105, (byte)-74, (byte)-127, (byte)2, (byte)-31, (byte)82, (byte)40,
                (byte)-41, (byte)36, (byte)20, (byte)-101, (byte)-10, (byte)-107, (byte)10, (byte)41,
                (byte)23, (byte)-73, (byte)-118, (byte)72, (byte)-109, (byte)-86, (byte)105, (byte)45,
                (byte)-47, (byte)-105, (byte)104, (byte)-29, (byte)44, (byte)-83, (byte)-117, (byte)99,
                (byte)71, (byte)-66, (byte)32, (byte)126, (byte)11, (byte)-15, (byte)0, (byte)-22,
                (byte)3, (byte)31, (byte)-64, (byte)71, (byte)33, (byte)-58, (byte)38, (byte)74,
                (byte)-115, (byte)120, (byte)-31, (byte)97, (byte)103, (byte)-9, (byte)-52, (byte)-50,
                (byte)57, (byte)51, (byte)123, (byte)-10, (byte)-25, (byte)-81, (byte)-53, (byte)31,
                (byte)0, (byte)118, (byte)-96, (byte)-105, (byte)81, (byte)-60, (byte)35, (byte)21,
                (byte)-101, (byte)101, (byte)60, (byte)-58, (byte)-109, (byte)18, (byte)10, (byte)120,
                (byte)-86, (byte)-32, (byte)-103, (byte)-126, (byte)-25, (byte)-124, (byte)66, (byte)-57,
                (byte)108, (byte)-99, (byte)-20, (byte)19, (byte)-24, (byte)-108, (byte)-96, (byte)29,
                (byte)54, (byte)-37, (byte)-17, (byte)-102, (byte)-5, (byte)-26, (byte)-88, (byte)-33,
                (byte)60, (byte)48, (byte)9, (byte)-107, (byte)-34, (byte)-123, (byte)-8, (byte)36,
                (byte)12, (byte)87, (byte)120, (byte)103, (byte)-58, (byte)48, (byte)10, (byte)28,
                (byte)-17, (byte)-20, (byte)13, (byte)97, (byte)-71, (byte)-19, (byte)123, (byte)97,
                (byte)36, (byte)-68, (byte)-56, (byte)18, (byte)110, (byte)44, (byte)85, (byte)-68,
                (byte)32, (byte)-108, (byte)91, (byte)39, (byte)-35, (byte)94, (byte)103, (byte)116,
                (byte)-4, (byte)-2, (byte)-48, (byte)84, (byte)81, (byte)35, (byte)20, (byte)-9,
                (byte)122, (byte)77, (byte)107, (byte)112, (byte)-92, (byte)-94, (byte)-50, (byte)-110,
                (byte)-106, (byte)121, (byte)52, (byte)-20, (byte)14, (byte)-6, (byte)-93, (byte)-10,
                (byte)-96, (byte)-61, (byte)-110, (byte)-44, (byte)-51, (byte)-15, (byte)64, (byte)-108,
                (byte)-55, (byte)39, (byte)-83, (byte)84, (byte)52, (byte)-104, (byte)-75, (byte)-21,
                (byte)120, (byte)78, (byte)-12, (byte)-106, (byte)-112, (byte)-85, (byte)-43, (byte)45,
                (byte)66, (byte)-66, (byte)-19, (byte)79, (byte)36, (byte)97, (byte)-91, (byte)-25,
                (byte)120, (byte)-78, (byte)31, (byte)79, (byte)-57, (byte)50, (byte)56, (byte)22,
                (byte)99, (byte)87, (byte)38, (byte)83, (byte)-7, (byte)-74, (byte)112, (byte)45,
                (byte)17, (byte)56, (byte)9, (byte)-98, (byte)39, (byte)-13, (byte)-47, (byte)-71,
                (byte)19, (byte)18, (byte)-74, (byte)122, (byte)-74, (byte)63, (byte)53, (byte)-28,
                (byte)103, (byte)49, (byte)-99, (byte)-71, (byte)-46, (byte)8, (byte)-49, (byte)121,
                (byte)-26, (byte)-113, (byte)49, (byte)-85, (byte)70, (byte)50, (byte)-116, (byte)12,
                (byte)49, (byte)-101, (byte)25, (byte)-83, (byte)-40, (byte)113, (byte)39, (byte)-4,
                (byte)-126, (byte)15, (byte)78, (byte)-14, (byte)22, (byte)117, (byte)-41, (byte)118,
                (byte)-25, (byte)77, (byte)-53, (byte)67, (byte)63, (byte)14, (byte)108, (byte)-71,
                (byte)-25, (byte)36, (byte)106, (byte)-85, (byte)-103, (byte)50, (byte)61, (byte)-15,
                (byte)64, (byte)-61, (byte)45, (byte)-36, (byte)78, (byte)-70, (byte)4, (byte)-79,
                (byte)84, (byte)-16, (byte)82, (byte)-61, (byte)22, (byte)94, (byte)105, (byte)80,
                (byte)-96, (byte)18, (byte)26, (byte)-1, (byte)-33, (byte)-111, (byte)-123, (byte)-81,
                (byte)12, (byte)29, (byte)-116, (byte)47, (byte)-92, (byte)29, (byte)17, (byte)54,
                (byte)-104, (byte)-81, (byte)-49, (byte)-7, (byte)-6, (byte)-33, (byte)124, (byte)-99,
                (byte)-7, (byte)-4, (byte)65, (byte)19, (byte)57, (byte)-114, (byte)-103, (byte)11,
                (byte)118, (byte)102, (byte)91, (byte)127, (byte)77, (byte)88, (byte)-69, (byte)18,
                (byte)105, (byte)-7, (byte)-66, (byte)43, (byte)-123, (byte)-57, (byte)118, (byte)-50,
                (byte)68, (byte)16, (byte)-54, (byte)5, (byte)92, (byte)-81, (byte)-3, (byte)-5,
                (byte)117, (byte)-11, (byte)83, (byte)108, (byte)-13, (byte)-57, (byte)23, (byte)-39,
                (byte)-1, (byte)34, (byte)-86, (byte)-55, (byte)-16, (byte)124, (byte)-86, (byte)-94,
                (byte)-124, (byte)50, (byte)43, (byte)95, (byte)-29, (byte)-13, (byte)18, (byte)52,
                (byte)-58, (byte)-53, (byte)25, (byte)124, (byte)-99, (byte)-15, (byte)74, (byte)6,
                (byte)-81, (byte)50, (byte)94, (byte)67, (byte)101, (byte)-127, (byte)111, (byte)48,
                (byte)-66, (byte)-103, (byte)-71, (byte)95, (byte)-25, (byte)69, (byte)-119, (byte)85,
                (byte)28, (byte)-17, (byte)112, (byte)-58, (byte)-32, (byte)-99, (byte)7, (byte)71,
                (byte)-95, (byte)-15, (byte)13, (byte)-12, (byte)37, (byte)45, (byte)-87, (byte)-90,
                (byte)-19, (byte)41, (byte)-115, (byte)119, (byte)57, (byte)106, (byte)127, (byte)10,
                (byte)112, (byte)15, (byte)-9, (byte)121, (byte)87, (byte)-15, (byte)96, (byte)65,
                (byte)-34, (byte)76, (byte)111, (byte)-128, (byte)82, (byte)101, (byte)-23, (byte)59,
                (byte)114, (byte)95, (byte)-111, (byte)79, (byte)4, (byte)40, (byte)35, (byte)-96,
                (byte)112, (byte)-36, (byte)72, (byte)69, (byte)31, (byte)-2, (byte)6, (byte)80,
                (byte)75, (byte)7, (byte)8, (byte)-81, (byte)-102, (byte)-119, (byte)99, (byte)-46,
                (byte)1, (byte)0, (byte)0, (byte)-23, (byte)2, (byte)0, (byte)0, (byte)80,
                (byte)75, (byte)1, (byte)2, (byte)20, (byte)0, (byte)20, (byte)0, (byte)8,
                (byte)0, (byte)8, (byte)0, (byte)-11, (byte)-127, (byte)-61, (byte)68, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)2, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)9, (byte)0, (byte)4, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)77, (byte)69, (byte)84,
                (byte)65, (byte)45, (byte)73, (byte)78, (byte)70, (byte)47, (byte)-2, (byte)-54,
                (byte)0, (byte)0, (byte)80, (byte)75, (byte)1, (byte)2, (byte)20, (byte)0,
                (byte)20, (byte)0, (byte)8, (byte)0, (byte)8, (byte)0, (byte)-11, (byte)-127,
                (byte)-61, (byte)68, (byte)127, (byte)71, (byte)56, (byte)-57, (byte)60, (byte)0,
                (byte)0, (byte)0, (byte)60, (byte)0, (byte)0, (byte)0, (byte)20, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)61, (byte)0, (byte)0, (byte)0,
                (byte)77, (byte)69, (byte)84, (byte)65, (byte)45, (byte)73, (byte)78, (byte)70,
                (byte)47, (byte)77, (byte)65, (byte)78, (byte)73, (byte)70, (byte)69, (byte)83,
                (byte)84, (byte)46, (byte)77, (byte)70, (byte)80, (byte)75, (byte)1, (byte)2,
                (byte)20, (byte)0, (byte)20, (byte)0, (byte)8, (byte)0, (byte)8, (byte)0,
                (byte)-49, (byte)-127, (byte)-61, (byte)68, (byte)-67, (byte)119, (byte)-82, (byte)40,
                (byte)-35, (byte)2, (byte)0, (byte)0, (byte)-111, (byte)5, (byte)0, (byte)0,
                (byte)49, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)-69, (byte)0,
                (byte)0, (byte)0, (byte)99, (byte)111, (byte)109, (byte)47, (byte)101, (byte)120,
                (byte)97, (byte)109, (byte)112, (byte)108, (byte)101, (byte)47, (byte)115, (byte)104,
                (byte)114, (byte)105, (byte)110, (byte)107, (byte)117, (byte)110, (byte)105, (byte)116,
                (byte)116, (byte)101, (byte)115, (byte)116, (byte)47, (byte)97, (byte)112, (byte)112,
                (byte)47, (byte)77, (byte)97, (byte)105, (byte)110, (byte)65, (byte)99, (byte)116,
                (byte)105, (byte)118, (byte)105, (byte)116, (byte)121, (byte)46, (byte)99, (byte)108,
                (byte)97, (byte)115, (byte)115, (byte)80, (byte)75, (byte)1, (byte)2, (byte)20,
                (byte)0, (byte)20, (byte)0, (byte)8, (byte)0, (byte)8, (byte)0, (byte)-49,
                (byte)-127, (byte)-61, (byte)68, (byte)-81, (byte)-102, (byte)-119, (byte)99, (byte)-46,
                (byte)1, (byte)0, (byte)0, (byte)-23, (byte)2, (byte)0, (byte)0, (byte)48,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)0, (byte)0, (byte)0, (byte)0, (byte)-9, (byte)3, (byte)0,
                (byte)0, (byte)99, (byte)111, (byte)109, (byte)47, (byte)101, (byte)120, (byte)97,
                (byte)109, (byte)112, (byte)108, (byte)101, (byte)47, (byte)115, (byte)104, (byte)114,
                (byte)105, (byte)110, (byte)107, (byte)117, (byte)110, (byte)105, (byte)116, (byte)116,
                (byte)101, (byte)115, (byte)116, (byte)47, (byte)97, (byte)112, (byte)112, (byte)47,
                (byte)66, (byte)117, (byte)105, (byte)108, (byte)100, (byte)67, (byte)111, (byte)110,
                (byte)102, (byte)105, (byte)103, (byte)46, (byte)99, (byte)108, (byte)97, (byte)115,
                (byte)115, (byte)80, (byte)75, (byte)5, (byte)6, (byte)0, (byte)0, (byte)0,
                (byte)0, (byte)4, (byte)0, (byte)4, (byte)0, (byte)58, (byte)1, (byte)0,
                (byte)0, (byte)39, (byte)6, (byte)0, (byte)0, (byte)0, (byte)0,
        };
        return createFile(dir, "app/build/intermediates/classes/debug/classes.jar", bytecode);
    }

    private static File createR8Dex(File dir) throws IOException {
        /*
         Dex file contain the activity below, it has been produced with R8 with minSdkVersion 25.

         package com.example.shrinkunittest.app;
         import android.app.Activity;
         import android.os.Bundle;
         import android.view.Menu;
         import android.view.MenuItem;
         public class MainActivity extends Activity {
           public MainActivity() {
           }
           protected void onCreate(Bundle var1) {
             super.onCreate(var1);
             this.setContentView(2130903040);
           }
           public boolean onCreateOptionsMenu(Menu var1) {
             this.getMenuInflater().inflate(2131165184, var1);
             return true;
           }
           public boolean onOptionsItemSelected(MenuItem var1) {
             int var2 = var1.getItemId();
             return var2 == 2131230720 ? true : super.onOptionsItemSelected(var1);
           }
         }
        */
        byte[] dexContent =
                Resources.toByteArray(Resources.getResource("resourceShrinker/classes.dex"));
        return createFile(
                dir, "app/build/intermediates/transforms/r8/debug/0/classes.dex", dexContent);
    }

    private static File createMappingFile(File dir) throws IOException {
        return createFile(dir, "app/build/proguard/release/mapping.txt", ""
                + "com.example.shrinkunittest.app.MainActivity -> com.example.shrinkunittest.app.MainActivity:\n"
                + "    void onCreate(android.os.Bundle) -> onCreate\n"
                + "    boolean onCreateOptionsMenu(android.view.Menu) -> onCreateOptionsMenu\n"
                + "    boolean onOptionsItemSelected(android.view.MenuItem) -> onOptionsItemSelected\n"
                + "com.foo.bar.R$layout -> com.foo.bar.t:\n"
                + "    int checkable_option_view_layout -> a\n"
                + "    int error_layout -> b\n"
                + "    int glyph_button_icon_only -> c\n"
                + "    int glyph_button_icon_with_text_below -> d\n"
                + "    int glyph_button_icon_with_text_right -> e\n"
                + "    int structure_status_view -> f\n"
                + "android.support.annotation.FloatRange -> android.support.annotation.FloatRange:\n"
                + "    double from() -> from\n"
                + "    double to() -> to\n"
                + "    boolean fromInclusive() -> fromInclusive\n"
                + "    boolean toInclusive() -> toInclusive\n");
    }

    @Test
    public void testFormatStringRegexp() {
        assertEquals(NO_MATCH, convertFormatStringToRegexp(""));
        assertEquals("\\Qfoo_\\E", convertFormatStringToRegexp("foo_"));
        assertEquals("\\Qfoo\\E.*\\Q_\\E.*\\Qend\\E", convertFormatStringToRegexp("foo%s_%1$send"));
        assertEquals("\\Qescape!.()\\E", convertFormatStringToRegexp("escape!.()"));

        assertEquals(NO_MATCH, convertFormatStringToRegexp("%c%c%c%d"));
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%d%s"));
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%s%s"));
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%d_%d"));
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%s%s%s%s"));
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%s_%s_%s"));
        assertEquals(NO_MATCH, convertFormatStringToRegexp("%.0f%s"));

        assertEquals(".*\\Qabc\\E", convertFormatStringToRegexp("%sabc"));
        assertTrue("abc".matches(convertFormatStringToRegexp("%sabc")));
        assertTrue("somethingabc".matches(convertFormatStringToRegexp("%sabc")));

        assertEquals("\\Qa\\E\\p{Digit}+.*", convertFormatStringToRegexp("a%d%s"));
        assertTrue("a52hello".matches(convertFormatStringToRegexp("a%d%s")));

        assertEquals("\\Qprefix\\E0*\\p{Digit}+\\Qsuffix\\E", convertFormatStringToRegexp("prefix%05dsuffix"));
        assertTrue("prefix012345suffix".matches(convertFormatStringToRegexp("prefix%05dsuffix")));

        assertEquals("\\p{Digit}+\\Qk\\E", convertFormatStringToRegexp("%dk"));
        assertTrue("1234k".matches(convertFormatStringToRegexp("%dk")));
        assertFalse("ic_shield_dark".matches(convertFormatStringToRegexp("%dk")));

        assertTrue("foo_".matches(convertFormatStringToRegexp("foo_")));
        assertTrue("fooA_BBend".matches(convertFormatStringToRegexp("foo%s_%1$send")));
        assertFalse("A_BBend".matches(convertFormatStringToRegexp("foo%s_%1$send")));

        // Comprehensive test
        String p = "prefix%s%%%n%c%x%d%o%b%h%f%e%a%g%C%X%5B%E%A%G";
        String s = String.format(p, "", 'c', 1, 1, 1, true, 1, 1f, 1f, 1f, 1f, 'c', 1, 1, 1f, 1f, 1f);
        s = s.replaceAll("\r\n", "\n");
        assertTrue(s.matches(convertFormatStringToRegexp(p)));
    }

    /** Utility method to generate byte array literal dump (used by classesJarBytecode above) */
    @SuppressWarnings("UnusedDeclaration") // Utility for future .class/.jar additions
    public static void dumpBytes(File file) throws IOException {
        byte[] bytes = Files.toByteArray(file);
        int count = 0;
        for (byte b : bytes) {
            System.out.print("(byte)" + Byte.toString(b) + ", ");
            count++;
            if (count == 8) {
                count = 0;
                System.out.println();
            }
        }

        System.out.println();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected static void deleteDir(File root) {
        if (root.exists()) {
            File[] files = root.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDir(file);
                    } else {
                        file.delete();
                    }
                }
            }
            root.delete();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NonNull
    private static File createFile(File dir, String relative) throws IOException {
        File file = new File(dir, relative.replace('/', separatorChar));
        file.getParentFile().mkdirs();
        return file;
    }

    @NonNull
    private static File createFile(File dir, String relative, String contents) throws IOException {
        File file = createFile(dir, relative);
        Files.asCharSink(file, Charsets.UTF_8).write(contents);
        return file;
    }

    @NonNull
    private static File createFile(File dir, String relative, byte[] contents) throws IOException {
        File file = createFile(dir, relative);
        Files.write(contents, file);
        return file;
    }

    private static void checkState(ResourceUsageAnalyzer analyzer) {
        List<Resource> resources = Lists.newArrayList(analyzer.getModel().getResources());
        Collections.sort(resources, new Comparator<Resource>() {
            @Override
            public int compare(Resource resource1, Resource resource2) {
                int delta = resource1.type.compareTo(resource2.type);
                if (delta != 0) {
                    return delta;
                }
                return resource1.name.compareTo(resource2.name);
            }
        });

        // Ensure unique
        Resource prev = null;
        for (Resource resource : resources) {
            assertTrue(resource + " and " + prev, prev == null
                    || resource.type != prev.type
                    || !resource.name.equals(prev.name));
            prev = resource;
        }

        checkSerialization(analyzer);
    }

    private static String checkSerialization(ResourceUsageAnalyzer analyzer) {
        // Deserialize and serialize again to make sure the two models again
        ResourceUsageModel model = analyzer.getModel();
        String serialized = model.serialize(true);
        ResourceUsageModel deserialized = ResourceUsageModel.deserialize(serialized);

        assertEquals(serialized, deserialized.serialize(true));
        assertThat(deserialized.dumpConfig()).isEqualTo(model.dumpConfig());
        assertThat(deserialized.dumpKeepResources()).isEqualTo(model.dumpKeepResources());
        assertThat(deserialized.dumpReferences()).isEqualTo(model.dumpReferences());
        assertThat(deserialized.dumpResourceModel()).isEqualTo(model.dumpResourceModel());
        return serialized;
    }

    @Test
    public void testIsResourceClass() throws Exception {
        File dummy = new File("dummy");
        File mappingFile = createMappingFile(Files.createTempDir());
        ResourceUsageAnalyzer analyzer =
                new ResourceUsageAnalyzer(
                        dummy,
                        Collections.singleton(dummy),
                        dummy,
                        mappingFile,
                        dummy,
                        null);
        analyzer.getModel().addDeclaredResource(ResourceType.LAYOUT, "structure_status_view", null, true);
        analyzer.recordMapping(mappingFile);
        assertTrue(analyzer.isResourceClass("android/support/v7/appcompat/R$attr.class"));
        assertTrue(analyzer.isResourceClass("android/support/v7/appcompat/R$attr.class"));
        assertTrue(analyzer.isResourceClass("android/support/v7/appcompat/R$bool.class"));
        assertFalse(analyzer.isResourceClass("android/support/v7/appcompat/R.class"));
        assertFalse(analyzer.isResourceClass("com/google/samples/apps/iosched/ui/BrowseSessionsActivity.class"));
        assertFalse(analyzer.isResourceClass("android/support/annotation/FloatRange.class"));
        assertTrue(analyzer.isResourceClass("com/foo/bar/t.class"));
        assertTrue(analyzer.isResourceClass("com/foo/bar/t"));
        Resource resource = analyzer.getResourceFromCode("com/foo/bar/t", "f");
        assertNotNull(resource);
        assertEquals("structure_status_view", resource.name);
        assertEquals(ResourceType.LAYOUT, resource.type);
        assertNull(analyzer.getResourceFromCode("android/support/annotation/FloatRange",
                "fromInclusive"));
        checkState(analyzer);
    }
}
