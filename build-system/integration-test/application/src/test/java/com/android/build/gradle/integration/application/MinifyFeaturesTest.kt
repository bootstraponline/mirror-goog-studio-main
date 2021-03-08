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

package com.android.build.gradle.integration.application

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.AabSubject.Companion.assertThat
import com.android.build.gradle.integration.common.truth.ModelContainerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.common.utils.getOutputByName
import com.android.builder.model.CodeShrinker
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.builder.internal.packaging.ApkCreatorType
import com.android.builder.internal.packaging.ApkCreatorType.APK_FLINGER
import com.android.builder.internal.packaging.ApkCreatorType.APK_Z_FILE_CREATOR
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.builder.model.SyncIssue
import com.android.testutils.AssumeUtil
import com.android.testutils.TestInputsGenerator
import com.android.testutils.apk.Aab
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.android.utils.Pair
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Files
import kotlin.test.fail

/**
 * Tests using Proguard/R8 to shrink and obfuscate code in a project with features.
 *
 * Project roughly structured as follows (see implementation below for exact structure) :
 *
 * <pre>
 *                  --->  library2  ------>
 *   otherFeature1  --->  library3           library1
 *                  --->  baseModule  ---->
 *   otherFeature2
 *
 * More explicitly,
 * otherFeature1  depends on  library2, library3, baseModule
 * otherFeature2  depends on  baseModule
 *    baseModule  depends on  library1
 *      library2  depends on  library1
 * </pre>
 */
@RunWith(FilterableParameterized::class)
class MinifyFeaturesTest(
    val codeShrinker: CodeShrinker,
    val apkCreatorType: ApkCreatorType) {

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}, {1}")
        fun getConfigurations(): Collection<Array<Enum<*>>> =
            listOf(
                arrayOf(CodeShrinker.PROGUARD, APK_Z_FILE_CREATOR),
                arrayOf(CodeShrinker.R8, APK_Z_FILE_CREATOR),
                arrayOf(CodeShrinker.PROGUARD, APK_FLINGER),
                arrayOf(CodeShrinker.R8, APK_FLINGER)
            )
    }

    private val otherFeature2GradlePath = ":otherFeature2"

    private val lib1 =
        MinimalSubProject.lib("com.example.lib1")
            .appendToBuild("""
                android {
                    buildTypes {
                        minified.initWith(buildTypes.debug)
                        minified {
                            consumerProguardFiles "proguard-rules.pro"
                        }
                    }
                }
                """)
            .withFile("src/main/resources/lib1_java_res.txt", "lib1")
            .withFile(
                "src/main/java/com/example/lib1/Lib1Class.java",
                """package com.example.lib1;
                    import java.io.InputStream;
                    public class Lib1Class {
                        public String getJavaRes() {
                            InputStream inputStream =
                                    Lib1Class.class
                                            .getClassLoader()
                                            .getResourceAsStream("lib1_java_res.txt");
                            if (inputStream == null) {
                                return "can't find lib1_java_res";
                            }
                            byte[] line = new byte[1024];
                            try {
                                inputStream.read(line);
                                return new String(line, "UTF-8").trim();
                            } catch (Exception ignore) {
                            }
                            return "something went wrong";
                        }
                    }""")
            .withFile(
                "src/main/java/com/example/lib1/EmptyClassToKeep.java",
                """package com.example.lib1;
                    public class EmptyClassToKeep {
                    }""")
            .withFile(
                "src/main/java/com/example/lib1/EmptyClassToRemove.java",
                """package com.example.lib1;
                    public class EmptyClassToRemove {
                    }""")
            .withFile(
                "proguard-rules.pro",
                """-keep public class com.example.lib1.EmptyClassToKeep
                   -keeppackagenames com.example.lib1**""")

    private val lib2 =
        MinimalSubProject.lib("com.example.lib2")
            .appendToBuild("""
                android {
                    buildTypes {
                        minified.initWith(buildTypes.debug)
                        minified {
                            consumerProguardFiles "proguard-rules.pro"
                        }
                    }
                }
                """)
            // include foo_view.xml and FooView.java below to generate aapt proguard rules to be
            // merged in the base.
            .withFile(
                "src/main/res/layout/foo_view.xml",
                """<?xml version="1.0" encoding="utf-8"?>
                    <view
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        class="com.example.lib2.FooView"
                        android:id="@+id/foo_view" />"""
            )
            .withFile(
                "src/main/java/com/example/lib2/FooView.java",
                """package com.example.lib2;
                    import android.content.Context;
                    import android.view.View;
                    public class FooView extends View {
                        public FooView(Context context) {
                            super(context);
                        }
                    }""")
            .withFile("src/main/resources/lib2_java_res.txt", "lib2")
            .withFile(
                "src/main/java/com/example/lib2/Lib2Class.java",
                """package com.example.lib2;
                    import java.io.InputStream;
                    public class Lib2Class {
                        public String getJavaRes() {
                            InputStream inputStream =
                                    Lib2Class.class
                                            .getClassLoader()
                                            .getResourceAsStream("lib2_java_res.txt");
                            if (inputStream == null) {
                                return "can't find lib2_java_res";
                            }
                            byte[] line = new byte[1024];
                            try {
                                inputStream.read(line);
                                return new String(line, "UTF-8").trim();
                            } catch (Exception ignore) {
                            }
                            return "something went wrong";
                        }
                    }""")
            .withFile(
                "src/main/java/com/example/lib2/EmptyClassToKeep.java",
                """package com.example.lib2;
                    public class EmptyClassToKeep {
                    }""")
            .withFile(
                "src/main/java/com/example/lib2/EmptyClassToRemove.java",
                """package com.example.lib2;
                    public class EmptyClassToRemove {
                    }""")
            .withFile(
                "proguard-rules.pro",
                """-keep public class com.example.lib2.EmptyClassToKeep
                   -keeppackagenames com.example.lib2**""")

    private val lib3 =
        MinimalSubProject.lib("com.example.lib3")
            .appendToBuild("android { buildTypes { minified { initWith(buildTypes.debug) }}}")

    private val baseModule = MinimalSubProject.app("com.example.baseModule")
        .appendToBuild(
        """
                        android {
                            dynamicFeatures = [':foo:otherFeature1', '$otherFeature2GradlePath']
                            buildTypes {
                                minified.initWith(buildTypes.debug)
                                minified {
                                    minifyEnabled true
                                    useProguard ${codeShrinker == CodeShrinker.PROGUARD}
                                    proguardFiles getDefaultProguardFile('proguard-android.txt'),
                                            "proguard-rules.pro"
                                }
                            }
                        }
                        """)
        .withFile(
            "src/main/AndroidManifest.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                      package="com.example.baseModule">
                    <application android:label="app_name">
                        <activity android:name=".Main"
                                  android:label="app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>""")
        .withFile(
            "src/main/res/layout/base_main.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        android:orientation="vertical"
                        android:layout_width="fill_parent"
                        android:layout_height="fill_parent" >
                    <TextView
                            android:layout_width="fill_parent"
                            android:layout_height="wrap_content"
                            android:text="Base"
                            android:id="@+id/text" />
                    <TextView
                            android:layout_width="fill_parent"
                            android:layout_height="wrap_content"
                            android:text=""
                            android:id="@+id/extraText" />
                </LinearLayout>""")
        .withFile(
            "src/main/res/values/string.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="otherFeature1">otherFeature1</string>
                    <string name="otherFeature2">otherFeature2</string>
                </resources>""")
        .withFile("src/main/resources/base_java_res.txt", "base")
        .withFile(
            "src/main/java/com/example/baseModule/Main.java",
            """package com.example.baseModule;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.TextView;

                import java.lang.Exception;
                import java.lang.RuntimeException;

                import com.example.lib1.Lib1Class;

                public class Main extends Activity {

                    private int foo = 1234;

                    private final StringProvider stringProvider = new StringProvider();

                    private final Lib1Class lib1Class = new Lib1Class();

                    /** Called when the activity is first created. */
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.base_main);

                        TextView tv = (TextView) findViewById(R.id.extraText);
                        tv.setText(
                                ""
                                        + getLib1Class().getJavaRes()
                                        + " "
                                        + getStringProvider().getString(foo));
                    }

                    public StringProvider getStringProvider() {
                        return stringProvider;
                    }

                    public Lib1Class getLib1Class() {
                        return lib1Class;
                    }

                    public void handleOnClick(android.view.View view) {
                        // This method should be kept by the default ProGuard rules.
                    }
                }""")
        .withFile(
            "src/main/java/com/example/baseModule/StringProvider.java",
            """package com.example.baseModule;

                public class StringProvider {

                    public String getString(int foo) {
                        return Integer.toString(foo);
                    }
                }""")
        .withFile(
            "src/main/java/com/example/baseModule/EmptyClassToKeep.java",
            """package com.example.baseModule;
                public class EmptyClassToKeep {
                }""")
        .withFile(
            "src/main/java/com/example/baseModule/EmptyClassToRemove.java",
            """package com.example.baseModule;
                public class EmptyClassToRemove {
                }""")
        .withFile(
            "proguard-rules.pro",
            """-keep public class com.example.baseModule.EmptyClassToKeep
               -keeppackagenames com.example.baseModule**""")

    private val otherFeature1 = MinimalSubProject.dynamicFeature("com.example.otherFeature1")
        .appendToBuild(
            """
                android {
                    buildTypes {
                        minified.initWith(buildTypes.debug)
                        minified {
                            proguardFiles "proguard-rules.pro"
                        }
                    }
                }
                """)
        .withFile(
            "src/main/AndroidManifest.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:dist="http://schemas.android.com/apk/distribution"
                    package="com.example.otherFeature1">

                    <dist:module dist:onDemand="true"
                                 dist:title="@string/otherFeature1">
                        <dist:fusing dist:include="true"/>
                    </dist:module>

                    <application android:label="app_name">
                        <activity android:name=".Main"
                                  android:label="app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>""")
        .withFile(
            "src/main/res/layout/other_main_1.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        android:orientation="vertical"
                        android:layout_width="fill_parent"
                        android:layout_height="fill_parent" >
                    <TextView
                            android:layout_width="fill_parent"
                            android:layout_height="wrap_content"
                            android:text="Other Feature 1"
                            android:id="@+id/text" />
                    <TextView
                            android:layout_width="fill_parent"
                            android:layout_height="wrap_content"
                            android:text=""
                            android:id="@+id/extraText" />
                </LinearLayout>""")
        .withFile("src/main/resources/other_java_res_1.txt", "other")
        .withFile(
            "src/main/java/com/example/otherFeature1/Main.java",
            """package com.example.otherFeature1;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.TextView;

                import java.lang.Exception;
                import java.lang.RuntimeException;

                import com.example.baseModule.StringProvider;
                import com.example.lib2.Lib2Class;

                public class Main extends Activity {

                    private int foo = 1234;

                    private final StringProvider stringProvider = new StringProvider();

                    private final Lib2Class lib2Class = new Lib2Class();

                    /** Called when the activity is first created. */
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.other_main_1);

                        TextView tv = (TextView) findViewById(R.id.extraText);
                        tv.setText(
                                ""
                                        + getLib2Class().getJavaRes()
                                        + " "
                                        + getStringProvider().getString(foo));
                    }

                    public StringProvider getStringProvider() {
                        return stringProvider;
                    }

                    public Lib2Class getLib2Class() {
                        return lib2Class;
                    }

                    public void handleOnClick(android.view.View view) {
                        // This method should be kept by the default ProGuard rules.
                    }
                }""")
        .withFile(
            "src/main/java/com/example/otherFeature1/EmptyClassToKeep.java",
            """package com.example.otherFeature1;
                public class EmptyClassToKeep {
                }""")
        .withFile(
            "src/main/java/com/example/otherFeature1/EmptyClassToRemove.java",
            """package com.example.otherFeature1;
                public class EmptyClassToRemove {
                }""")
        .withFile(
        "proguard-rules.pro",
        """-keep public class com.example.otherFeature1.EmptyClassToKeep
           -keeppackagenames com.example.otherFeature1**""")

    private val otherFeature2 = MinimalSubProject.dynamicFeature("com.example.otherFeature2")

        .appendToBuild("""
        android {
            buildTypes {
                minified.initWith(buildTypes.debug)
            }
        }
        """)
        .withFile(
            "src/main/AndroidManifest.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:dist="http://schemas.android.com/apk/distribution"
                    package="com.example.otherFeature2">

                    <dist:module dist:onDemand="true"
                                 dist:title="@string/otherFeature2">
                        <dist:fusing dist:include="true"/>
                    </dist:module>

                    <application android:label="app_name">
                        <activity android:name=".Main"
                                  android:label="app_name">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>""")
        .withFile(
            "src/main/res/layout/other_main_2.xml",
            """<?xml version="1.0" encoding="utf-8"?>
                <LinearLayout
                        xmlns:android="http://schemas.android.com/apk/res/android"
                        android:orientation="vertical"
                        android:layout_width="fill_parent"
                        android:layout_height="fill_parent" >
                    <TextView
                            android:layout_width="fill_parent"
                            android:layout_height="wrap_content"
                            android:text="Other Feature 2"
                            android:id="@+id/text" />
                    <TextView
                            android:layout_width="fill_parent"
                            android:layout_height="wrap_content"
                            android:text=""
                            android:id="@+id/extraText" />
                </LinearLayout>""")
        .withFile("src/main/resources/other_java_res_2.txt", "other")
        .withFile(
            "src/main/java/com/example/otherFeature2/Main.java",
            """package com.example.otherFeature2;

                import android.app.Activity;
                import android.os.Bundle;
                import android.widget.TextView;

                import java.lang.Exception;
                import java.lang.RuntimeException;

                import com.example.baseModule.StringProvider;

                public class Main extends Activity {

                    private int foo = 1234;

                    private final StringProvider stringProvider = new StringProvider();

                    /** Called when the activity is first created. */
                    @Override
                    public void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.other_main_2);

                        TextView tv = (TextView) findViewById(R.id.extraText);
                        tv.setText(getStringProvider().getString(foo));
                    }

                    public StringProvider getStringProvider() {
                        return stringProvider;
                    }

                    public void handleOnClick(android.view.View view) {
                        // This method should be kept by the default ProGuard rules.
                    }
                }""")

    private val testApp =
        MultiModuleTestProject.builder()
            .subproject(":lib1", lib1)
            .subproject(":lib2", lib2)
            .subproject(":lib3", lib3)
            .subproject(":baseModule", baseModule)
            .subproject(":foo:otherFeature1", otherFeature1)
            .subproject(otherFeature2GradlePath, otherFeature2)
            .dependency(otherFeature1, lib2)
            // otherFeature1 depends on lib3 to test having multiple library module dependencies.
            .dependency(otherFeature1, lib3)
            .dependency(otherFeature1, baseModule)
            .dependency(otherFeature2, baseModule)
            .dependency("api", lib2, lib1)
            .dependency(baseModule, lib1)
            .build()

    @get:Rule
    val project = GradleTestProject.builder().fromTestApp(testApp)
        .create()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun updateGradleProperties() {
        TestFileUtils.appendToFile(project.gradlePropertiesFile, getGradleProperties())
    }

    @Test
    fun testApksAreMinified() {

        val apkType = object : GradleTestProject.ApkType {
            override val buildType = "minified"
            override val testName: String? = null
            override val isSigned: Boolean = true
        }

        val executor = project.executor()
            .with(OptionalBooleanOption.INTERNAL_ONLY_ENABLE_R8, codeShrinker == CodeShrinker.R8)

        executor.run("assembleMinified")

        // check aapt_rules.txt merging
        val aaptProguardFile =
            FileUtils.join(
                project.getSubproject("baseModule").intermediatesDir,
                "aapt_proguard_file",
                "minified",
                SdkConstants.FN_AAPT_RULES)
        assertThat(aaptProguardFile).exists()
        assertThat(aaptProguardFile)
            .doesNotContain("-keep class com.example.lib2.FooView")
        val mergedAaptProguardFile =
            FileUtils.join(
                project.getSubproject("baseModule").intermediatesDir,
                "merged_aapt_proguard_file",
                "minified",
                SdkConstants.FN_MERGED_AAPT_RULES)
        assertThat(mergedAaptProguardFile).exists()
        assertThat(mergedAaptProguardFile)
            .contains("-keep class com.example.lib2.FooView")

        project.getSubproject("baseModule").getApk(apkType).use { apk ->
            assertThat(apk.file).exists()
            assertThat(apk).containsClass("Lcom/example/baseModule/Main;")
            assertThat(apk).containsClass("Lcom/example/baseModule/a;")
            assertThat(apk).containsClass("Lcom/example/baseModule/EmptyClassToKeep;")
            assertThat(apk).containsClass("Lcom/example/lib1/EmptyClassToKeep;")
            assertThat(apk).containsClass("Lcom/example/lib1/a;")
            assertThat(apk).containsJavaResource("base_java_res.txt")
            assertThat(apk).containsJavaResource("lib1_java_res.txt")
            assertThat(apk).doesNotContainClass("Lcom/example/baseFeature/EmptyClassToRemove;")
            assertThat(apk).doesNotContainClass("Lcom/example/lib1/EmptyClassToRemove;")
            assertThat(apk).doesNotContainClass("Lcom/example/lib2/EmptyClassKeep;")
            assertThat(apk).doesNotContainClass("Lcom/example/lib2/Lib2Class;")
            assertThat(apk).doesNotContainClass("Lcom/example/lib2/a;")
            assertThat(apk).doesNotContainClass("Lcom/example/otherFeature1/Main;")
            assertThat(apk).doesNotContainClass("Lcom/example/otherFeature2/Main;")
            // we split java resources back to features if using R8, but not if using proguard
            when (codeShrinker) {
                CodeShrinker.R8 -> {
                    assertThat(apk).doesNotContainJavaResource("other_java_res_1.txt")
                    assertThat(apk).doesNotContainJavaResource("other_java_res_2.txt")
                    assertThat(apk).doesNotContainJavaResource("lib2_java_res.txt")
                }
                CodeShrinker.PROGUARD -> {
                    assertThat(apk).containsJavaResource("other_java_res_1.txt")
                    assertThat(apk).containsJavaResource("other_java_res_2.txt")
                    assertThat(apk).containsJavaResource("lib2_java_res.txt")
                }
            }
        }

        project.getSubproject(":foo:otherFeature1").getApk(apkType).use { apk ->
            assertThat(apk.file).exists()
            assertThat(apk).containsClass("Lcom/example/otherFeature1/Main;")
            assertThat(apk).containsClass(
                "Lcom/example/otherFeature1/EmptyClassToKeep;"
            )
            assertThat(apk).containsClass("Lcom/example/lib2/EmptyClassToKeep;")
            assertThat(apk).containsClass("Lcom/example/lib2/FooView;")
            assertThat(apk).containsClass("Lcom/example/lib2/a;")
            assertThat(apk).doesNotContainClass(
                "Lcom/example/otherFeature1/EmptyClassToRemove;"
            )
            assertThat(apk).doesNotContainClass("Lcom/example/lib2/EmptyClassToRemove;")
            assertThat(apk).doesNotContainClass("Lcom/example/lib1/EmptyClassToKeep;")
            assertThat(apk).doesNotContainClass("Lcom/example/lib1/Lib1Class;")
            assertThat(apk).doesNotContainClass("Lcom/example/lib1/a;")
            assertThat(apk).doesNotContainClass("Lcom/example/baseModule/Main;")
            assertThat(apk).doesNotContainClass("Lcom/example/otherFeature2/Main;")
            // we split java resources back to features if using R8, but not if using proguard
            when (codeShrinker) {
                CodeShrinker.R8 -> {
                    assertThat(apk).containsJavaResource("other_java_res_1.txt")
                    assertThat(apk).containsJavaResource("lib2_java_res.txt")
                }
                CodeShrinker.PROGUARD -> {
                    assertThat(apk).doesNotContainJavaResource("other_java_res_1.txt")
                    assertThat(apk).doesNotContainJavaResource("lib2_java_res.txt")
                }
            }
        }

        project.getSubproject(otherFeature2GradlePath).getApk(apkType).use { apk ->
            assertThat(apk.file).exists()
            assertThat(apk).containsClass("Lcom/example/otherFeature2/Main;")
            assertThat(apk).doesNotContainClass("Lcom/example/lib1/EmptyClassToKeep;")
            assertThat(apk).doesNotContainClass("Lcom/example/lib2/EmptyClassToKeep;")
            assertThat(apk).doesNotContainClass("Lcom/example/baseModule/Main;")
            assertThat(apk).doesNotContainClass("Lcom/example/otherFeature1/Main;")
            // we split java resources back to features if using R8, but not if using proguard
            when (codeShrinker) {
                CodeShrinker.R8 -> {
                    assertThat(apk).containsJavaResource("other_java_res_2.txt")
                }
                CodeShrinker.PROGUARD -> {
                    assertThat(apk).doesNotContainJavaResource("other_java_res_2.txt")
                }
            }
        }
    }

    @Test
    fun testBundleIsMinified() {
        val executor = project.executor()
            .with(OptionalBooleanOption.INTERNAL_ONLY_ENABLE_R8, codeShrinker == CodeShrinker.R8)
        executor.run("bundleMinified")

        val bundleFile = getApkFolderOutput("minified", ":baseModule").bundleFile
        assertThat(bundleFile).exists()

        Aab(bundleFile).use {
            // Check that java resources are packaged as expected. We split java resources back to
            // features if using R8, but not if using proguard
            val expectedJavaRes = when (codeShrinker) {
                CodeShrinker.R8 -> listOf(
                    "/base/root/base_java_res.txt",
                    "/base/root/lib1_java_res.txt",
                    "/otherFeature1/root/lib2_java_res.txt",
                    "/otherFeature1/root/other_java_res_1.txt",
                    "/otherFeature2/root/other_java_res_2.txt"
                )
                CodeShrinker.PROGUARD -> listOf(
                    "/base/root/base_java_res.txt",
                    "/base/root/lib1_java_res.txt",
                    "/base/root/lib2_java_res.txt",
                    "/base/root/other_java_res_1.txt",
                    "/base/root/other_java_res_2.txt"
                )
            }
            Truth.assertThat(it.entries.map { entry -> entry.toString() })
                .containsAtLeastElementsIn(expectedJavaRes)
            // check base classes
            val expectedBaseClasses = listOf(
                "Lcom/example/baseModule/Main;",
                "Lcom/example/baseModule/a;",
                "Lcom/example/baseModule/EmptyClassToKeep;",
                "Lcom/example/lib1/EmptyClassToKeep;",
                "Lcom/example/lib1/a;"
            )
            expectedBaseClasses.forEach {
                    className -> assertThat(it).containsClass("base", className)
            }
            val unexpectedBaseClasses = listOf(
                "Lcom/example/baseFeature/EmptyClassToRemove;",
                "Lcom/example/lib1/EmptyClassToRemove;",
                "Lcom/example/lib2/EmptyClassKeep;",
                "Lcom/example/lib2/Lib2Class;",
                "Lcom/example/lib2/a;",
                "Lcom/example/otherFeature1/Main;",
                "Lcom/example/otherFeature2/Main;"
            )
            unexpectedBaseClasses.forEach {
                    className -> assertThat(it).doesNotContainClass("base", className)
            }
            // check otherFeature1 classes
            val expectedOtherFeature1Classes = listOf(
                "Lcom/example/otherFeature1/Main;",
                "Lcom/example/otherFeature1/EmptyClassToKeep;",
                "Lcom/example/lib2/EmptyClassToKeep;",
                "Lcom/example/lib2/FooView;",
                "Lcom/example/lib2/a;"
            )
            expectedOtherFeature1Classes.forEach {
                    className -> assertThat(it).containsClass("otherFeature1", className)
            }
            val unexpectedOtherFeature1Classes = listOf(
                "Lcom/example/otherFeature1/EmptyClassToRemove;",
                "Lcom/example/lib2/EmptyClassToRemove;",
                "Lcom/example/lib1/EmptyClassToKeep;",
                "Lcom/example/lib1/Lib1Class;",
                "Lcom/example/lib1/a;",
                "Lcom/example/baseModule/Main;",
                "Lcom/example/otherFeature2/Main;"
            )
            unexpectedOtherFeature1Classes.forEach {
                    className -> assertThat(it).doesNotContainClass("otherFeature1", className)
            }
            // check otherFeature2 classes
            val expectedOtherFeature2Classes = listOf("Lcom/example/otherFeature2/Main;")
            expectedOtherFeature2Classes.forEach {
                    className -> assertThat(it).containsClass("otherFeature2", className)
            }
            val unexpectedOtherFeature2Classes = listOf(
                "Lcom/example/lib1/EmptyClassToKeep;",
                "Lcom/example/lib2/EmptyClassToKeep;",
                "Lcom/example/baseModule/Main;",
                "Lcom/example/otherFeature1/Main;"
            )
            unexpectedOtherFeature2Classes.forEach {
                    className -> assertThat(it).doesNotContainClass("otherFeature2", className)
            }
        }
    }

    @Test
    fun testMinifyEnabledSyncError() {
        Assume.assumeTrue(codeShrinker == CodeShrinker.R8)
        project.getSubproject(":foo:otherFeature1")
            .buildFile
            .appendText("android.buildTypes.minified.minifyEnabled true")
        val container = project.model().ignoreSyncIssues().fetchAndroidProjects()
        ModelContainerSubject.assertThat(container).rootBuild().project(":foo:otherFeature1")
            .hasSingleError(SyncIssue.TYPE_GENERIC)
            .that()
            .hasMessageThatContains("cannot set minifyEnabled to true.")
    }

    @Test
    fun testDefaultProguardFilesSyncError() {
        Assume.assumeTrue(codeShrinker == CodeShrinker.R8)
        project.getSubproject(otherFeature2GradlePath)
            .buildFile
            .appendText(
                """
                    android {
                        buildTypes {
                            minified {
                                proguardFiles getDefaultProguardFile('proguard-android.txt')
                            }
                        }
                    }
                    """
            )
        val container = project.model().ignoreSyncIssues().fetchAndroidProjects()
        ModelContainerSubject.assertThat(container).rootBuild().project(otherFeature2GradlePath)
            .hasSingleError(SyncIssue.TYPE_GENERIC)
            .that()
            .hasMessageThatContains("should not be specified in this module.")
    }

    // Tests new shrinker rules filtering done by FilterShrinkerRulesTransform to select only rules
    // targeted to specific R8 or Proguard versions.
    @Test
    fun appTestExtractedJarKeepRules() {
        AssumeUtil.assumeNotWindows()  // b/146571219

        val executor = project.executor()
            .with(OptionalBooleanOption.INTERNAL_ONLY_ENABLE_R8, codeShrinker == CodeShrinker.R8)

        executor.run("assembleMinified")

        val classContent = "package example;\n" + "public class ToBeKept { }"
        val toBeKept = project.getSubproject("baseModule").mainSrcDir.toPath().resolve("example/ToBeKept.java")
        Files.createDirectories(toBeKept.parent)
        Files.write(toBeKept, classContent.toByteArray())

        val classContent2 = "package example;\n" + "public class ToBeRemoved { }"
        val toBeRemoved = project.getSubproject("baseModule").mainSrcDir.toPath().resolve("example/ToBeRemoved.java")
        Files.createDirectories(toBeRemoved.parent)
        Files.write(toBeRemoved, classContent2.toByteArray())

        val jarFile = temporaryFolder.newFile("libkeeprules.jar")
        val keepRule = "-keep class example.ToBeKept"
        val keepRuleToBeIgnored = "-keep class example.ToBeRemoved"

        TestInputsGenerator.writeJarWithTextEntries(
            jarFile.toPath(),
            Pair.of("META-INF/com.android.tools/r8/rules.pro", keepRule),
            Pair.of("META-INF/com.android.tools/proguard/rules.pro", keepRule),
            Pair.of("META-INF/proguard/rules.pro", keepRuleToBeIgnored)
        )

        TestFileUtils.appendToFile(
            project.getSubproject(":foo:otherFeature1").buildFile,
            ""
                    + "dependencies {\n"
                    + "    implementation files ('"
                    + FileUtils.escapeSystemDependentCharsIfNecessary(jarFile.absolutePath)
                    + "')\n"
                    + "}"
        )

        project.executor()
            .with(OptionalBooleanOption.INTERNAL_ONLY_ENABLE_R8, codeShrinker == CodeShrinker.R8)
            .run("assembleMinified")

        val apkType = GradleTestProject.ApkType.of("minified", true)

        project.getSubproject("baseModule").getApk(apkType).use { minified ->
            assertThat(minified).containsClass("Lexample/ToBeKept;")
            assertThat(minified).doesNotContainClass("Lexample/ToBeRemoved;")
        }
    }

    /** Regression test for https://issuetracker.google.com/79090176 */
    @Test
    fun testMinifyEnabledToggling() {
        val executor = project.executor()
            .with(OptionalBooleanOption.INTERNAL_ONLY_ENABLE_R8, codeShrinker == CodeShrinker.R8)

        // first run with minifyEnabled true
        executor.run("assembleMinified")

        // then run with minifyEnabled false
        TestFileUtils.searchAndReplace(
            project.getSubproject(":baseModule").buildFile,
            "minifyEnabled true",
            "minifyEnabled false"
        )
        executor.run("assembleMinified")
    }

    private fun getApkFolderOutput(
        variantName: String,
        baseGradlePath: String
    ): AppBundleVariantBuildOutput {
        val outputModels = project.model().fetchContainer(AppBundleProjectBuildOutput::class.java)

        val outputAppModel =
            outputModels.rootBuildModelMap[baseGradlePath]
                ?: fail("Failed to get output model for $baseGradlePath module")

        return outputAppModel.getOutputByName(variantName)
    }

    private fun getGradleProperties() = when (apkCreatorType) {
        APK_Z_FILE_CREATOR -> "${BooleanOption.USE_NEW_APK_CREATOR.propertyName}=false"
        APK_FLINGER -> "${BooleanOption.USE_NEW_APK_CREATOR.propertyName}=true"
    }
}
