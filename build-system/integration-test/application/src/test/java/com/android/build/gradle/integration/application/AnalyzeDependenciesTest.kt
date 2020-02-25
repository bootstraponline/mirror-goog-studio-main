package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.tasks.DependenciesUsageReport
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.generateAarWithContent
import com.android.utils.usLocaleCapitalize
import com.google.common.io.Resources
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [AnalyzeDependenciesTask]
 */
class AnalyzeDependenciesTest {

    private val emptyAar = generateAarWithContent("com.analyzedependenciesTest.emptyAar")
    private val usedClassAar = generateAarWithContent("com.analyzedependenciesTest.usedClassAar",
            Resources.toByteArray(
                    Resources.getResource(
                            AnalyzeDependenciesTest::class.java,
                            "AnalyzeDependenciesTest/used-jar.jar")
            )
    )

    private val app = MinimalSubProject.app("com.example.app")
            .appendToBuild(
                    """
                dependencies {
                implementation project(path: ':usedLocalLib')
                implementation project(path: ':unUsedLocalLib')
                implementation 'com.analyzedependenciesTest:emptyAar:1'
                implementation 'com.analyzedependenciesTest:usedClassAar:1'
                 }
            """.trimIndent()
            )
            .withFile("src/main/java/com/example/app/MyClass.java",
                    """
                package com.example.app;
                
                import com.android.build.gradle.integration.application.AnalyzeDependenciesTest.UsedClass;
                import com.example.usedlocallib.UsedLocalLib;
                
                public class MyClass {
                    void testUsedAarClass() {
                        UsedClass usedClass = new UsedClass();
                        usedClass.getTrue();
                    }
                    void testUsedLocalLibClass() {
                        UsedLocalLib usedLocalLib = new UsedLocalLib();
                        usedLocalLib.getFoo();
                    }
                }
            """.trimIndent())

    private val usedLocalLib = MinimalSubProject.lib("com.example.usedlocallib")
            .withFile("src/main/java/com/example/usedlocallib/UsedLocalLib.java",
                    """
                package com.example.usedlocallib;

                public class UsedLocalLib {
                    public String getFoo() {
                        return "Foo";
                    }
                }
            """.trimIndent())
            .withFile(
                    "src/main/res/layout/layout_random_name.xml",
                    """<?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <TextView
                        android:id="@+id/text_box"
                        android:text="test"
                        android:layout_x="10px"
                        android:layout_y="110px"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
                        """.trimIndent()
            )

    private val unUsedLocalLib = MinimalSubProject.lib("com.example.unusedlocallib")
            .withFile("src/main/java/com/example/usedlocallib/UnUsedLocalLib.java",
                    """
                package com.example.unusedlocallib;

                public class UnUsedLocalLib {
                    public String getBar() {
                        return "Bar";
                    }
                }
            """.trimIndent())

    private val mavenRepo = MavenRepoGenerator(
            listOf(
                    MavenRepoGenerator.Library(
                            "com.analyzedependenciesTest:emptyAar:1", "aar", emptyAar),
                    MavenRepoGenerator.Library(
                            "com.analyzedependenciesTest:usedClassAar:1", "aar", usedClassAar)
            )
    )

    private val testApp =
            MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":usedLocalLib", usedLocalLib)
                    .subproject(":unUsedLocalLib", unUsedLocalLib)
                    .dependency(app, usedLocalLib)
                    .dependency(app, unUsedLocalLib)
                    .build()

    @get:Rule
    val project = GradleTestProject.builder()
            .withAdditionalMavenRepo(mavenRepo)
            .fromTestApp(testApp)
            .create()

    @Test
    fun `Verify correct dependencies report is produced, only considering class and resource references`() {
        val buildType = "debug"
        project.execute(
                ":app:assemble${buildType.usLocaleCapitalize()}",
                ":app:analyze${buildType.usLocaleCapitalize()}Dependencies"
        )

        val dependencyAnalysisReport = project.getSubproject(":app").getIntermediateFile(
                "analyze_dependencies_report",
                buildType,
                "analyzeDependencies",
                "dependenciesReport.json"
        )
        assertThat(dependencyAnalysisReport.exists())

        val dependencyReportJson = dependencyAnalysisReport.readText()
        val parsedJson =
                Gson().fromJson(dependencyReportJson, DependenciesUsageReport::class.java)

        assertThat(parsedJson.remove).containsExactly("com.analyzedependenciesTest:emptyAar:1")
        assertThat(parsedJson.add.size).isEqualTo(0)
    }
}