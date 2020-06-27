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
package com.android.ide.common.repository;

import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.ide.common.gradle.model.level2.IdeLibrary;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.testutils.TestUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;

public class ResourceVisibilityLookupTest extends TestCase {
    public void test() throws IOException {
        IdeLibrary library =
                createMockLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        ""
                                + ""
                                + "dimen activity_vertical\n"
                                + "id action_settings\n"
                                + "layout activity_main\n");

        ResourceVisibilityLookup visibility = ResourceVisibilityLookup.create(library);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertFalse(visibility.isPrivate(ResourceType.ID, "action_settings"));
        assertFalse(visibility.isPrivate(ResourceType.LAYOUT, "activity_main"));
        //noinspection ConstantConditions
        assertTrue(visibility.isPrivate(ResourceUrl.parse("@dimen/activity_horizontal_margin")));

        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "activity_vertical")); // public
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "unknown")); // not in this library
    }

    public void testModelVersions() throws IOException {
        IdeLibrary library =
                createMockLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        ""
                                + ""
                                + "dimen activity_vertical\n"
                                + "id action_settings\n"
                                + "layout activity_main\n");

        IdeAndroidArtifact mockArtifact = createMockArtifact(Collections.singletonList(library));
        IdeVariant variant = createMockVariant(mockArtifact);

        IdeAndroidProject project;

        project = createMockProject("1.0.1", 0);
        assertTrue(new ResourceVisibilityLookup.Provider().get(project, variant).isEmpty());


        project = createMockProject("1.1", 0);
        assertTrue(new ResourceVisibilityLookup.Provider().get(project, variant).isEmpty());

        project = createMockProject("1.2", 2);
        assertTrue(new ResourceVisibilityLookup.Provider().get(project, variant).isEmpty());

        project = createMockProject("1.3.0", 3);
        assertFalse(new ResourceVisibilityLookup.Provider().get(project, variant).isEmpty());

        project = createMockProject("2.5", 45);
        assertFalse(new ResourceVisibilityLookup.Provider().get(project, variant).isEmpty());

        ResourceVisibilityLookup visibility =new ResourceVisibilityLookup.Provider().get(project,
                variant);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertFalse(visibility.isPrivate(ResourceType.ID, "action_settings"));
    }

    public void testAllPrivate() throws IOException {
        IdeLibrary library =
                createMockLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        "");

        ResourceVisibilityLookup visibility = ResourceVisibilityLookup.create(library);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertTrue(visibility.isPrivate(ResourceType.ID, "action_settings"));
        assertTrue(visibility.isPrivate(ResourceType.LAYOUT, "activity_main"));
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_vertical_margin"));

        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "unknown")); // not in this library
    }

    public void testNotDeclared() throws IOException {
        IdeLibrary library = createMockLibrary("com.android.tools:test-library:1.0.0", "", null);

        ResourceVisibilityLookup visibility = ResourceVisibilityLookup.create(library);
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertFalse(visibility.isPrivate(ResourceType.ID, "action_settings"));
        assertFalse(visibility.isPrivate(ResourceType.LAYOUT, "activity_main"));
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "activity_vertical"));

        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "unknown")); // not in this library
    }

    public void testCombined() throws IOException {
        IdeLibrary library1 =
                createMockLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        "string hello_world");
        IdeLibrary library2 =
                createMockLibrary(
                        "com.android.tools:test-library2:1.0.0",
                        ""
                                + "int layout foo 0x7f030001\n"
                                + "int layout bar 0x7f060000\n"
                                // Used public, but not explicitly declared: should remain public
                                // even though from the perspective of this library it looks private
                                // since this is a usage/override, not a declaration
                                + "int string hello_world 0x7f040003",
                        "" + "layout foo\n");

        List<IdeLibrary> androidLibraries = Arrays.asList(library1, library2);
        ResourceVisibilityLookup visibility =
                ResourceVisibilityLookup.create(androidLibraries, null);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertTrue(visibility.isPrivate(ResourceType.ID, "action_settings"));
        assertTrue(visibility.isPrivate(ResourceType.LAYOUT, "activity_main"));
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_vertical_margin"));
        assertFalse(visibility.isPrivate(ResourceType.LAYOUT, "foo"));
        assertTrue(visibility.isPrivate(ResourceType.LAYOUT, "bar"));
        assertFalse(visibility.isPrivate(ResourceType.STRING, "hello_world"));

        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "unknown")); // not in this library
    }

    public void testDependency() throws IOException {
        IdeLibrary library1 =
                createMockLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        "");
        IdeLibrary library2 =
                createMockLibrary(
                        "com.android.tools:test-library2:1.0.0",
                        "" + "int layout foo 0x7f030001\n" + "int layout bar 0x7f060000\n",
                        ""
                                + "layout foo\n" /*,
                                                 Collections.singletonList(library1)*/); // TODO(b/158836360): Review when the dependency hierarchy is available.

        List<IdeLibrary> androidLibraries = Arrays.asList(library1, library2);
        ResourceVisibilityLookup visibility =
                ResourceVisibilityLookup.create(androidLibraries, null);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_horizontal_margin"));
        assertTrue(visibility.isPrivate(ResourceType.ID, "action_settings"));
        assertTrue(visibility.isPrivate(ResourceType.LAYOUT, "activity_main"));
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "activity_vertical_margin"));
        assertFalse(visibility.isPrivate(ResourceType.LAYOUT, "foo"));
        assertTrue(visibility.isPrivate(ResourceType.LAYOUT, "bar"));

        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "unknown")); // not in this library
    }

    public void testManager() throws IOException {
        IdeLibrary library =
                createMockLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen activity_horizontal_margin 0x7f030000\n"
                                + "int dimen activity_vertical_margin 0x7f030001\n"
                                + "int id action_settings 0x7f060000\n"
                                + "int layout activity_main 0x7f020000\n"
                                + "int menu menu_main 0x7f050000\n"
                                + "int string action_settings 0x7f040000\n"
                                + "int string app_name 0x7f040001\n"
                                + "int string hello_world 0x7f040002",
                        "");
        ResourceVisibilityLookup.Provider provider = new ResourceVisibilityLookup.Provider();
        assertSame(provider.get(library), provider.get(library));
        assertTrue(provider.get(library).isPrivate(ResourceType.DIMEN,
                "activity_horizontal_margin"));

        IdeAndroidArtifact artifact = createMockArtifact(Collections.singletonList(library));
        assertSame(provider.get(artifact), provider.get(artifact));
        assertTrue(provider.get(artifact).isPrivate(ResourceType.DIMEN,
                "activity_horizontal_margin"));
    }

    public void testImportedResources() throws IOException {
        // Regression test for https://code.google.com/p/android/issues/detail?id=183120 :
        // When a library depends on another library, all the resources from the dependency
        // are imported and exposed in R.txt from the downstream library too. When both
        // libraries expose public resources, we have to be careful such that we don't
        // take the presence of a resource (imported) and the absence of a public.txt declaration
        // (for the imported symbol in the dependent library) as evidence that this is a private
        // resource.
        IdeLibrary library1 =
                createMockLibrary(
                        "com.android.tools:test-library:1.0.0",
                        ""
                                + "int dimen public_library1_resource1 0x7f030000\n"
                                + "int dimen public_library1_resource2 0x7f030001\n"
                                + "int dimen private_library1_resource 0x7f030002\n",
                        ""
                                + "dimen public_library1_resource1\n"
                                + "dimen public_library1_resource2\n");

        IdeLibrary library2 =
                createMockLibrary(
                        "com.android.tools:test-library2:1.0.0",
                        ""
                                + "int dimen public_library2_resource1 0x7f030000\n"
                                + "int dimen public_library2_resource2 0x7f030001\n",
                        null // nothing marked as private: everything exposed
                        );
        IdeLibrary library3 =
                createMockLibrary(
                        "com.android.tools:test-library3:1.0.0",
                        ""
                                + "int dimen public_library1_resource1 0x7f030000\n" // merged from
                                // library1
                                + "int dimen public_library1_resource2 0x7f030001\n"
                                + "int dimen private_library1_resource 0x7f030002\n"
                                + "int dimen public_library2_resource1 0x7f030003\n" // merged from
                                // library2
                                + "int dimen public_library2_resource2 0x7f030004\n"
                                + "int dimen public_library3_resource1 0x7f030005\n" // unique to
                                // library3
                                + "int dimen private_library3_resource 0x7f030006\n",
                        ""
                                + "dimen public_library2_resource1\n" /*,
                                                                      Arrays.asList(library1, library2)*/); // TODO(b/158836360): Review when the dependency hierarchy is available.

        List<IdeLibrary> androidLibraries = Arrays.asList(library1, library2, library3);
        ResourceVisibilityLookup visibility =
                ResourceVisibilityLookup.create(androidLibraries, null);
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "private_library1_resource"));
        assertTrue(visibility.isPrivate(ResourceType.DIMEN, "private_library3_resource"));
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "public_library1_resource1"));
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "public_library1_resource2"));
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "public_library2_resource1"));
        assertTrue(
                visibility.isPrivate(
                        ResourceType.DIMEN, "public_library2_resource2")); // private in one library
        assertFalse(visibility.isPrivate(ResourceType.DIMEN, "public_library3_resource"));
    }

    // TODO(b/158836360): Review when the dependency hierarchy is available.
    // public void testSymbolProvider() throws Exception {
    //    IdeLibrary library1 = createMockLibrary(
    //      "com.android.tools:test-library:1.0.0",
    //      ""
    //      + "int dimen public_library1_resource1 0x7f030000\n"
    //      + "int dimen public_library1_resource2 0x7f030001\n"
    //      + "int dimen private_library1_resource 0x7f030002\n",
    //      ""
    //      + "dimen public_library1_resource1\n"
    //      + "dimen public_library1_resource2\n"
    //    );
    //
    //    IdeLibrary library3 = createMockLibrary(
    //      "com.android.tools:test-library3:1.0.0",
    //      ""
    //      + "int dimen public_library1_resource1 0x7f030000\n" // merged from library1
    //      + "int dimen public_library1_resource2 0x7f030001\n"
    //      + "int dimen private_library1_resource 0x7f030002\n"
    //      + "int dimen public_library2_resource1 0x7f030003\n" // merged from library2
    //      + "int dimen public_library2_resource2 0x7f030004\n"
    //      + "int dimen public_library3_resource1 0x7f030005\n" // unique to library3
    //      + "int dimen private_library3_resource 0x7f030006\n",
    //      ""
    //        + "dimen public_library2_resource1\n",
    //      Collections.singletonList(library1)
    //    );
    //
    //    SymbolProvider provider = new SymbolProvider();
    //    Multimap<String, ResourceType> symbols = provider.getSymbols(library3);
    //
    //    // Exclude imported symbols
    //    assertFalse(symbols.get("public_library1_resource1").iterator().hasNext());
    //
    //    // Make sure non-imported symbols are there
    //    assertSame(ResourceType.DIMEN,
    // symbols.get("public_library3_resource1").iterator().next());
    //
    //    // Make sure we're actually caching results
    //    Multimap<String, ResourceType> symbols2 = provider.getSymbols(library3);
    //    assertSame(symbols, symbols2);
    // }

    public static IdeAndroidProject createMockProject(String modelVersion, int apiVersion) {
        IdeAndroidProject project = createNiceMock(IdeAndroidProject.class);
        expect(project.getApiVersion()).andReturn(apiVersion).anyTimes();
        expect(project.getModelVersion()).andReturn(modelVersion).anyTimes();
        replay(project);

        return project;
    }

    public static IdeVariant createMockVariant(IdeAndroidArtifact artifact) {
        IdeVariant variant = createNiceMock(IdeVariant.class);
        expect(variant.getMainArtifact()).andReturn(artifact).anyTimes();
        replay(variant);
        return variant;
    }

    public static IdeAndroidArtifact createMockArtifact(List<IdeLibrary> libraries) {
        IdeDependencies dependencies = createNiceMock(IdeDependencies.class);
        expect(dependencies.getAndroidLibraries()).andReturn(libraries).anyTimes();
        expect(dependencies.getModuleDependencies()).andReturn(Collections.emptyList()).anyTimes();
        replay(dependencies);

        IdeAndroidArtifact artifact = createNiceMock(IdeAndroidArtifact.class);
        expect(artifact.getLevel2Dependencies()).andReturn(dependencies).anyTimes();
        replay(artifact);

        return artifact;
    }

    public static IdeLibrary createMockLibrary(
            String name, String allResources, String publicResources) throws IOException {
        // Identical to PrivateResourceDetectorTest, but these are in test modules that
        // can't access each other
        final File tempDir = TestUtils.createTempDirDeletedOnExit();

        File rFile = new File(tempDir, FN_RESOURCE_TEXT);
        Files.asCharSink(rFile, Charsets.UTF_8).write(allResources);
        File publicTxtFile = new File(tempDir, FN_PUBLIC_TXT);
        if (publicResources != null) {
            Files.asCharSink(publicTxtFile, Charsets.UTF_8).write(publicResources);
        }
        IdeLibrary library = mock(IdeLibrary.class);
        when(library.getType()).thenReturn(IdeLibrary.LIBRARY_ANDROID);
        when(library.getPublicResources()).thenReturn(publicTxtFile.getPath());
        when(library.getSymbolFile()).thenReturn(rFile.getPath());

        GradleCoordinate c = GradleCoordinate.parseCoordinateString(name);
        assertNotNull(c);
        when(library.getArtifactAddress())
                .thenReturn(
                        c.getGroupId() + ":" + c.getArtifactId() + ":" + c.getRevision() + "@aar");
        when(library.getArtifact())
                .thenReturn(
                        new File(
                                "intermediates"
                                        + File.separator
                                        + "exploded-aar"
                                        + File.separator
                                        + name));

        return library;
    }
}
