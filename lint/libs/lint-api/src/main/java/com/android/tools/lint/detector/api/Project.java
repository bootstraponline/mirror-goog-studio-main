/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

import static com.android.SdkConstants.ANDROIDX_APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.ANDROIDX_SUPPORT_LIB_ARTIFACT;
import static com.android.SdkConstants.ANDROID_LIBRARY;
import static com.android.SdkConstants.ANDROID_LIBRARY_REFERENCE_FORMAT;
import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_MIN_SDK_VERSION;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.ATTR_TARGET_SDK_VERSION;
import static com.android.SdkConstants.FD_GRADLE_WRAPPER;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES;
import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.android.SdkConstants.FN_PROJECT_PROGUARD_FILE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.SdkConstants.OLD_PROGUARD_FILE;
import static com.android.SdkConstants.PROGUARD_CONFIG;
import static com.android.SdkConstants.PROJECT_PROPERTIES;
import static com.android.SdkConstants.RES_FOLDER;
import static com.android.SdkConstants.TAG_USES_SDK;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API;
import static com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API;
import static java.io.File.separator;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.projectmodel.ProjectType;
import com.android.repository.Revision;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.support.AndroidxNameUtils;
import com.android.tools.lint.client.api.CircularDependencyException;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.SdkInfo;
import com.android.tools.lint.model.LintModelAndroidLibrary;
import com.android.tools.lint.model.LintModelLibrary;
import com.android.tools.lint.model.LintModelMavenName;
import com.android.tools.lint.model.LintModelModule;
import com.android.tools.lint.model.LintModelModuleType;
import com.android.tools.lint.model.LintModelNamespacingMode;
import com.android.tools.lint.model.LintModelVariant;
import com.google.common.annotations.Beta;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.intellij.pom.java.LanguageLevel;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlin.io.FilesKt;
import kotlin.text.Charsets;
import org.jetbrains.kotlin.config.LanguageVersionSettings;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A project contains information about an Android project being scanned for Lint errors.
 *
 * <p><b>NOTE: This is not a public or final API; if you rely on this be prepared to adjust your
 * code for the next tools release.</b>
 */
@Beta
public class Project {
    protected final LintClient client;
    protected final File dir;
    protected final File referenceDir;
    protected Configuration configuration;
    protected String pkg;
    protected int buildSdk = -1;
    protected String buildTargetHash;
    protected IAndroidTarget target;

    protected AndroidVersion manifestMinSdk = AndroidVersion.DEFAULT;
    protected AndroidVersion manifestTargetSdk = AndroidVersion.DEFAULT;
    protected LanguageLevel javaLanguageLevel;
    protected LanguageVersionSettings kotlinLanguageLevel;

    protected boolean library;
    protected boolean externalLibrary;
    protected String name;
    protected String proguardPath;
    protected boolean mergeManifests = true;

    /** The SDK info, if any */
    protected SdkInfo sdkInfo;

    /**
     * If non null, specifies a non-empty list of specific files under this project which should be
     * checked.
     */
    protected List<File> files;

    protected List<File> proguardFiles;
    protected List<File> gradleFiles;
    protected List<File> manifestFiles;
    protected List<File> javaSourceFolders;
    protected List<File> generatedSourceFolders;
    protected List<File> javaClassFolders;
    protected List<File> nonProvidedJavaLibraries;
    protected List<File> javaLibraries;
    protected List<File> testSourceFolders;
    protected List<File> testLibraries;
    protected List<File> resourceFolders;
    protected List<File> generatedResourceFolders;
    protected List<File> assetFolders;
    protected List<Project> directLibraries;
    protected List<Project> allLibraries;
    protected boolean reportIssues = true;
    public Boolean gradleProject;
    protected Boolean supportLib;
    protected Boolean appCompat;
    protected Boolean leanback;
    protected Set<Desugaring> desugaring;
    private Map<String, String> superClassMap;
    private ResourceVisibilityLookup resourceVisibility;
    private Revision buildToolsRevision;
    private Document mergedManifest;
    private com.intellij.openapi.project.Project ideaProject;

    /**
     * Creates a new {@link Project} for the given directory.
     *
     * @param client the tool running the lint check
     * @param dir the root directory of the project
     * @param referenceDir See {@link #getReferenceDir()}.
     * @return a new {@link Project}
     */
    @NonNull
    public static Project create(
            @NonNull LintClient client, @NonNull File dir, @NonNull File referenceDir) {
        return new Project(client, dir, referenceDir);
    }

    /**
     * Returns true if this project is a Gradle-based Android project
     *
     * @return true if this is a Gradle-based project
     */
    public boolean isGradleProject() {
        if (gradleProject == null) {
            gradleProject = client.isGradleProject(this);
        }

        return gradleProject;
    }

    /**
     * Returns true if this project is an Android project.
     *
     * @return true if this project is an Android project.
     */
    public boolean isAndroidProject() {
        return true;
    }

    /**
     * Returns the project model for this project if supported by the build system.
     *
     * @return the project model, or null
     */
    @Nullable
    public LintModelModule getBuildModule() {
        LintModelVariant variant = getBuildVariant();
        if (variant != null) {
            return variant.getModule();
        }
        return null;
    }

    /**
     * Returns the current selected variant, if any.
     *
     * <p>This can be used by incremental lint rules to warn about problems in the current context.
     * Lint rules should however strive to perform cross variant analysis and warn about problems in
     * any configuration.
     *
     * @return the selected variant, or null
     */
    @Nullable
    public LintModelVariant getBuildVariant() {
        return null;
    }

    /**
     * Returns the project model for this project if it corresponds to a library with a lint model.
     *
     * @return the library model, or null
     */
    public LintModelAndroidLibrary getBuildLibraryModel() {
        return null;
    }

    /** Returns the set of desugaring operations in effect for this project. */
    public Set<Desugaring> getDesugaring() {
        if (desugaring == null) {
            desugaring = client.getDesugaring(this);
        }
        return desugaring;
    }

    /** Returns true if the given desugaring operation is in effect for this project. */
    public boolean isDesugaring(Desugaring type) {
        return getDesugaring().contains(type);
    }

    public boolean isCoreLibraryDesugaringEnabled() {
        LintModelVariant variant = getBuildVariant();
        return variant != null && variant.getBuildFeatures().getCoreLibraryDesugaringEnabled();
    }

    /** Returns the corresponding IDE project. */
    @Nullable
    public com.intellij.openapi.project.Project getIdeaProject() {
        return ideaProject;
    }

    public void setIdeaProject(@Nullable com.intellij.openapi.project.Project ideaProject) {
        this.ideaProject = ideaProject;
    }

    /**
     * If this is a Gradle project with a valid Gradle model, return the version of the
     * model/plugin.
     *
     * @return the Gradle plugin version, or null if invalid or not a Gradle project
     */
    @Nullable
    public GradleVersion getGradleModelVersion() {
        LintModelModule gradleProjectModel = getBuildModule();
        if (gradleProjectModel != null) {
            return gradleProjectModel.getGradleVersion();
        } else {
            return null;
        }
    }

    /**
     * Returns the merged manifest of this project. This may return null if not called on the main
     * project. Note that the file reference in the merged manifest isn't accurate; the merged
     * manifest accumulates information from a wide variety of locations.
     *
     * @return The merged manifest, if available.
     */
    @Nullable
    public Document getMergedManifest() {
        if (mergedManifest == null) {
            mergedManifest = client.getMergedManifest(this);
        }

        return mergedManifest;
    }

    /** Creates a new Project. Use one of the factory methods to create. */
    protected Project(@NonNull LintClient client, @NonNull File dir, @NonNull File referenceDir) {
        this.client = client;
        this.dir = dir;
        this.referenceDir = referenceDir;
        initialize();
    }

    protected void initialize() {
        // Default initialization: Use ADT/ant style project.properties file
        try {
            // Read properties file and initialize library state
            Properties properties = new Properties();
            File propFile = new File(dir, PROJECT_PROPERTIES);
            if (propFile.exists()) {
                BufferedInputStream is = new BufferedInputStream(new FileInputStream(propFile));
                try {
                    properties.load(is);
                    String value = properties.getProperty(ANDROID_LIBRARY);
                    library = VALUE_TRUE.equals(value);
                    String proguardPath = properties.getProperty(PROGUARD_CONFIG);
                    if (proguardPath != null) {
                        this.proguardPath = proguardPath;
                    }
                    mergeManifests =
                            !VALUE_FALSE.equals(properties.getProperty("manifestmerger.enabled"));
                    String target = properties.getProperty("target");
                    if (target != null) {
                        setBuildTargetHash(target);
                    }

                    if (VALUE_TRUE.equals(properties.getProperty("android_java8_libs"))) {
                        desugaring = Desugaring.FULL;
                    }

                    for (int i = 1; i < 1000; i++) {
                        String key = String.format(Locale.US, ANDROID_LIBRARY_REFERENCE_FORMAT, i);
                        String library = properties.getProperty(key);
                        if (library == null || library.isEmpty()) {
                            // No holes in the numbering sequence is allowed
                            break;
                        }

                        File libraryDir = new File(dir, library).getCanonicalFile();

                        if (directLibraries == null) {
                            directLibraries = new ArrayList<>();
                        }

                        // Adjust the reference dir to be a proper prefix path of the
                        // library dir
                        File libraryReferenceDir = referenceDir;
                        if (!libraryDir.getPath().startsWith(referenceDir.getPath())) {
                            // Symlinks etc might have been resolved, so do those to
                            // the reference dir as well
                            libraryReferenceDir = libraryReferenceDir.getCanonicalFile();
                            if (!libraryDir.getPath().startsWith(referenceDir.getPath())) {
                                File file = libraryReferenceDir;
                                while (file != null && !file.getPath().isEmpty()) {
                                    if (libraryDir.getPath().startsWith(file.getPath())) {
                                        libraryReferenceDir = file;
                                        break;
                                    }
                                    file = file.getParentFile();
                                }
                            }
                        }

                        try {
                            Project libraryPrj = client.getProject(libraryDir, libraryReferenceDir);
                            directLibraries.add(libraryPrj);
                            // By default, we don't report issues in inferred library projects.
                            // The driver will set report = true for those library explicitly
                            // requested.
                            libraryPrj.setReportIssues(false);
                        } catch (CircularDependencyException e) {
                            e.setProject(this);
                            e.setLocation(Location.create(propFile));
                            throw e;
                        }
                    }
                } finally {
                    try {
                        Closeables.close(is, true /* swallowIOException */);
                    } catch (IOException e) {
                        // cannot happen
                    }
                }
            }
        } catch (IOException ioe) {
            client.log(ioe, "Initializing project state");
        }

        if (directLibraries != null) {
            directLibraries = Collections.unmodifiableList(directLibraries);
        } else {
            directLibraries = Collections.emptyList();
        }

        if (isAospBuildEnvironment()) {
            if (isAospFrameworksRelatedProject(dir)) {
                // No manifest file for this project: just init the manifest values here
                manifestMinSdk = manifestTargetSdk = new AndroidVersion(HIGHEST_KNOWN_API, null);
            } else if (buildSdk == -1) {
                // only set BuildSdk for projects other than frameworks and
                // the ones that don't have one set in project.properties.
                buildSdk = getClient().getHighestKnownApiLevel();
                buildTargetHash =
                        AndroidTargetHash.getPlatformHashString(
                                new AndroidVersion(HIGHEST_KNOWN_API, null));
            }
        }
    }

    /** Gets the namespacing mode used for this project */
    @NonNull
    private LintModelNamespacingMode getNamespacingMode() {
        LintModelVariant variant = getBuildVariant();
        if (variant != null) {
            return variant.getBuildFeatures().getNamespacingMode();
        } else {
            return LintModelNamespacingMode.DISABLED;
        }
    }

    private ResourceNamespace namespace;

    /** Returns the namespace for resources in this module/project */
    @NonNull
    public ResourceNamespace getResourceNamespace() {
        if (namespace == null) {
            String packageName = getPackage();
            if (packageName == null || getNamespacingMode() == LintModelNamespacingMode.DISABLED) {
                namespace = ResourceNamespace.RES_AUTO;
            } else {
                namespace = ResourceNamespace.fromPackageName(packageName);
            }
        }

        return namespace;
    }

    @Override
    public String toString() {
        return "Project [dir=" + dir + ']';
    }

    @Override
    public int hashCode() {
        return dir.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Project other = (Project) obj;
        //noinspection FileComparisons
        return dir.equals(other.dir);
    }

    /**
     * Adds the given file to the list of files which should be checked in this project. If no files
     * are added, the whole project will be checked.
     *
     * @param file the file to be checked
     */
    public void addFile(@NonNull File file) {
        if (files == null) {
            files = new ArrayList<>();
        }
        files.add(file);
    }

    /**
     * The list of files to be checked in this project. If null, the whole project should be
     * checked.
     *
     * @return the subset of files to be checked, or null for the whole project
     */
    @Nullable
    public List<File> getSubset() {
        return files;
    }

    /**
     * Returns the list of source folders for Java and Kotlin source files
     *
     * @return a list of source folders to search for .java and .kt files
     */
    @NonNull
    public List<File> getJavaSourceFolders() {
        if (javaSourceFolders == null) {
            if (isAospFrameworksRelatedProject(dir)) {
                return Collections.singletonList(new File(dir, "java"));
            }
            if (isAospBuildEnvironment()) {
                String top = getAospTop();
                if (dir.getAbsolutePath().startsWith(top)) {
                    javaSourceFolders = getAospJavaSourcePath();
                    return javaSourceFolders;
                }
            }

            javaSourceFolders = client.getJavaSourceFolders(this);
        }

        return javaSourceFolders;
    }

    @NonNull
    public List<File> getGeneratedSourceFolders() {
        if (generatedSourceFolders == null) {
            generatedSourceFolders = client.getGeneratedSourceFolders(this);
        }

        return generatedSourceFolders;
    }

    /**
     * Returns the list of output folders or jars for class files.
     *
     * @return a list of output folders or jars to search for .class files
     */
    @NonNull
    public List<File> getJavaClassFolders() {
        if (javaClassFolders == null) {
            if (isAospFrameworksProject(dir)) {
                String top = getAospTop();
                if (top != null) {
                    File out = new File(top, "out");
                    if (out.exists()) {
                        String relative =
                                "target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes.jar";
                        File jar = new File(out, relative.replace('/', File.separatorChar));
                        if (jar.exists()) {
                            javaClassFolders = Collections.singletonList(jar);
                            return javaClassFolders;
                        }
                    }
                }
            }
            if (isAospBuildEnvironment()) {
                String top = getAospTop();
                if (dir.getAbsolutePath().startsWith(top)) {
                    javaClassFolders = getAospJavaClassPath();
                    return javaClassFolders;
                }
            }

            javaClassFolders = client.getJavaClassFolders(this);
        }
        return javaClassFolders;
    }

    /**
     * Returns the list of Java libraries (typically .jar files) that this project depends on. Note
     * that this refers to jar libraries, not Android library projects which are processed in a
     * separate pass with their own source and class folders.
     *
     * @param includeProvided If true, included provided libraries too (libraries that are not
     *     packaged with the app, but are provided for compilation purposes and are assumed to be
     *     present in the running environment)
     * @return a list of .jar files (or class folders) that this project depends on.
     */
    @NonNull
    public List<File> getJavaLibraries(boolean includeProvided) {
        if (includeProvided) {
            if (javaLibraries == null) {
                // AOSP builds already merge libraries and class folders into
                // the single classes.jar file, so these have already been processed
                // in getJavaClassFolders.
                javaLibraries = client.getJavaLibraries(this, true);
                if (isAospBuildEnvironment()) {
                    // We still need to add the support-annotations library in the case of AOSP
                    File out = new File(getAospTop(), "out");
                    String relative =
                            "target/common/obj/JAVA_LIBRARIES/"
                                    + "android-support-annotations_intermediates/classes";
                    File annotationsDir = new File(out, relative.replace('/', File.separatorChar));
                    if (annotationsDir.exists()) {
                        javaLibraries.add(annotationsDir);
                    }
                }
            }
            return javaLibraries;
        } else {
            if (nonProvidedJavaLibraries == null) {
                nonProvidedJavaLibraries = client.getJavaLibraries(this, false);
            }
            return nonProvidedJavaLibraries;
        }
    }

    /**
     * Returns the list of source folders for Java test source files
     *
     * @return a list of source folders to search for .java files
     */
    @NonNull
    public List<File> getTestSourceFolders() {
        if (testSourceFolders == null) {
            testSourceFolders = client.getTestSourceFolders(this);
        }

        return testSourceFolders;
    }

    /**
     * Returns the list of source folders for Java test source files
     *
     * @return a list of source folders to search for .java files
     */
    @NonNull
    public List<File> getTestLibraries() {
        if (testLibraries == null) {
            testLibraries = client.getTestLibraries(this);
        }

        return testLibraries;
    }

    /**
     * Returns the resource folders.
     *
     * @return a list of files pointing to the resource folders, which might be empty if the project
     *     does not provide any resources.
     */
    @NonNull
    public List<File> getResourceFolders() {
        if (resourceFolders == null) {
            List<File> folders = client.getResourceFolders(this);

            if (folders.size() == 1 && isAospFrameworksRelatedProject(dir)) {
                // No manifest file for this project: just init the manifest values here
                manifestMinSdk = manifestTargetSdk = new AndroidVersion(HIGHEST_KNOWN_API, null);
                File folder = new File(folders.get(0), RES_FOLDER);
                if (!folder.exists()) {
                    folders = Collections.emptyList();
                }
            }

            resourceFolders = folders;
        }

        return resourceFolders;
    }

    @NonNull
    public List<File> getGeneratedResourceFolders() {
        if (generatedResourceFolders == null) {
            generatedResourceFolders = client.getGeneratedResourceFolders(this);
        }

        return generatedResourceFolders;
    }

    /**
     * Returns the asset folders.
     *
     * @return a list of files pointing to the asset folders, which might be empty if the project
     *     does not provide any resources.
     */
    @NonNull
    public List<File> getAssetFolders() {
        if (assetFolders == null) {
            assetFolders = client.getAssetFolders(this);
        }

        return assetFolders;
    }

    /**
     * Returns the relative path of a given file relative to the user specified directory (which is
     * often the project directory but sometimes a higher up directory when a directory tree is
     * being scanned
     *
     * @param file the file under this project to check
     * @return the path relative to the reference directory (often the project directory)
     */
    @NonNull
    public String getDisplayPath(@NonNull File file) {
        return client.getDisplayPath(file, this, TextFormat.TEXT);
    }

    /**
     * Returns the relative path of a given file within the current project.
     *
     * @param file the file under this project to check
     * @return the path relative to the project
     */
    @NonNull
    public String getRelativePath(@NonNull File file) {
        String path = file.getPath();
        String referencePath = dir.getPath();
        if (path.startsWith(referencePath)) {
            int length = referencePath.length();
            if (path.length() > length && path.charAt(length) == File.separatorChar) {
                length++;
            }

            return path.substring(length);
        }

        return path;
    }

    /**
     * Returns the project root directory
     *
     * @return the dir
     */
    @NonNull
    public File getDir() {
        return dir;
    }

    /**
     * Returns the original user supplied directory where the lint search started. For example, if
     * you run lint against {@code /tmp/foo}, and it finds a project to lint in {@code
     * /tmp/foo/dev/src/project1}, then the {@code dir} is {@code /tmp/foo/dev/src/project1} and the
     * {@code referenceDir} is {@code /tmp/foo/}.
     *
     * @return the reference directory, never null
     */
    @NonNull
    public File getReferenceDir() {
        return referenceDir;
    }

    /**
     * Gets the configuration associated with this project
     *
     * @param driver the current driver, if any
     * @return the configuration associated with this project
     */
    @NonNull
    public Configuration getConfiguration(@Nullable LintDriver driver) {
        if (configuration == null) {
            configuration = client.getConfiguration(this, driver);
        }
        return configuration;
    }

    /**
     * Returns the application package specified by the manifest
     *
     * @return the application package, or null if unknown
     */
    @Nullable
    public String getPackage() {
        return pkg;
    }

    /**
     * Returns the application id, if known
     *
     * @return the application id, if known
     */
    @Nullable
    public String getApplicationId() {
        LintModelVariant variant = getBuildVariant();
        if (variant != null) {
            return variant.getMainArtifact().getApplicationId();
        }

        return getPackage();
    }

    /**
     * Returns the minimum API level for the project
     *
     * @return the minimum API level or {@link AndroidVersion#DEFAULT} if unknown
     */
    @NonNull
    public AndroidVersion getMinSdkVersion() {
        return manifestMinSdk == null ? AndroidVersion.DEFAULT : manifestMinSdk;
    }

    /**
     * Returns the minimum API <b>level</b> requested by the manifest, or -1 if not specified. Use
     * {@link #getMinSdkVersion()} to get a full version if you need to check if the platform is a
     * preview platform etc.
     *
     * @return the minimum API level or -1 if unknown
     */
    public int getMinSdk() {
        AndroidVersion version = getMinSdkVersion();
        return version == AndroidVersion.DEFAULT ? -1 : version.getApiLevel();
    }

    /**
     * Returns the target API level for the project
     *
     * @return the target API level or {@link AndroidVersion#DEFAULT} if unknown
     */
    @NonNull
    public AndroidVersion getTargetSdkVersion() {
        return manifestTargetSdk == AndroidVersion.DEFAULT ? getMinSdkVersion() : manifestTargetSdk;
    }

    /** Returns the expected language level for Java source files in this project */
    @NonNull
    public LanguageLevel getJavaLanguageLevel() {
        if (javaLanguageLevel == null) {
            javaLanguageLevel = client.getJavaLanguageLevel(this);
        }

        return javaLanguageLevel;
    }

    /** Returns the expected language level for Kotlin source files in this project */
    @NonNull
    public LanguageVersionSettings getKotlinLanguageLevel() {
        if (kotlinLanguageLevel == null) {
            kotlinLanguageLevel = client.getKotlinLanguageLevel(this);
        }

        return kotlinLanguageLevel;
    }

    /**
     * Returns the target API <b>level</b> specified by the manifest, or -1 if not specified. Use
     * {@link #getTargetSdkVersion()} to get a full version if you need to check if the platform is
     * a preview platform etc.
     *
     * @return the target API level or -1 if unknown
     */
    public int getTargetSdk() {
        AndroidVersion version = getTargetSdkVersion();
        return version == AndroidVersion.DEFAULT ? -1 : version.getApiLevel();
    }

    /**
     * Returns the compile SDK version used to build the project, or null if not known. This is the
     * string name of the compileSdkVersion. If you want the numeric API level, use {@link
     * #getBuildTargetHash()} instead, or to get the actual
     *
     * @return the compileSdkVersion or -1 if unknown
     */
    public int getBuildSdk() {
        return buildSdk;
    }

    /**
     * Returns the target API used to build the project, or null if not known. Note that this is
     * returning a String rather than a {@link AndroidVersion} since it may refer to either a {@link
     * AndroidTargetHash} for a platform or for an add-on, and {@link AndroidVersion} can only
     * express platform versions.
     *
     * @return the build target API or -1 if unknown
     */
    @Nullable
    public String getBuildTargetHash() {
        return buildTargetHash;
    }

    /**
     * Sets the build target hash to be used for this project. This is only intended for lint
     * internal usage.
     *
     * @param buildTargetHash the target hash
     */
    public void setBuildTargetHash(String buildTargetHash) {
        this.buildTargetHash = buildTargetHash;

        AndroidVersion version = AndroidTargetHash.getPlatformVersion(buildTargetHash);
        if (version != null) {
            buildSdk = version.getFeatureLevel();
        } else {
            client.log(Severity.WARNING, null, "Unexpected build target format: %1$s", target);
        }
    }

    /**
     * Returns the specific version of the build tools being used, if known
     *
     * @return the build tools version in use, or null if not known
     */
    @Nullable
    public Revision getBuildToolsRevision() {
        if (buildToolsRevision == null) {
            buildToolsRevision = client.getBuildToolsRevision(this);
        }

        return buildToolsRevision;
    }

    /**
     * Returns the target used to build the project, or null if not known
     *
     * @return the build target, or null
     */
    @Nullable
    public IAndroidTarget getBuildTarget() {
        if (target == null) {
            target = client.getCompileTarget(this);
        }

        return target;
    }

    /**
     * Initialized the manifest state from the given manifest model
     *
     * @param document the DOM document for the manifest XML document
     */
    public void readManifest(@NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        pkg = root.getAttribute(ATTR_PACKAGE);

        // Initialize minSdk and targetSdk
        NodeList usesSdks = root.getElementsByTagName(TAG_USES_SDK);
        if (usesSdks.getLength() > 0) {
            Element element = (Element) usesSdks.item(0);

            String minSdk = null;
            if (element.hasAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)) {
                minSdk = element.getAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION);
            }
            if (minSdk != null && !minSdk.isEmpty()) {
                if (Character.isDigit(minSdk.charAt(0))) {
                    // If the minSdk version is a number we don't need to look up
                    // SDK targets to resolve code names (and computing the target list
                    // is expensive)
                    manifestMinSdk = SdkVersionInfo.getVersion(minSdk, null);
                } else {
                    IAndroidTarget[] targets = client.getTargets();
                    manifestMinSdk = SdkVersionInfo.getVersion(minSdk, targets);
                }
            }

            if (element.hasAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)) {
                String targetSdk = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
                if (targetSdk != null && !targetSdk.isEmpty()) {
                    if (Character.isDigit(targetSdk.charAt(0))) {
                        // See minSdk comment above
                        manifestTargetSdk = SdkVersionInfo.getVersion(targetSdk, null);
                    } else {
                        IAndroidTarget[] targets = client.getTargets();
                        manifestTargetSdk = SdkVersionInfo.getVersion(targetSdk, targets);
                    }
                }
            } else {
                manifestTargetSdk = manifestMinSdk;
            }
        } else if (isAospBuildEnvironment()) {
            extractAospMinSdkVersion();
            manifestTargetSdk = manifestMinSdk;
        }
    }

    /**
     * Returns true if this project is an Android library project
     *
     * @return true if this project is an Android library project
     */
    public boolean isLibrary() {
        return library;
    }

    /**
     * Returns true if this project is an external library (typically an AAR library), as opposed to
     * a local library we have source for
     *
     * @return true if this is an external library
     */
    public boolean isExternalLibrary() {
        return externalLibrary;
    }

    protected LintModelMavenName mavenCoordinates = null;

    /**
     * Returns the Maven coordinate of this project, if known.
     *
     * @return the maven coordinate, or null
     */
    @Nullable
    public LintModelMavenName getMavenCoordinate() {
        if (mavenCoordinates == null) {
            LintModelModule model = getBuildModule();
            if (model != null) {
                mavenCoordinates = model.getMavenName();
            }
        }
        return mavenCoordinates;
    }

    /**
     * Returns the list of library projects referenced by this project
     *
     * @return the list of library projects referenced by this project, never null
     */
    @NonNull
    public List<Project> getDirectLibraries() {
        return directLibraries != null ? directLibraries : Collections.emptyList();
    }

    /**
     * Sets the list of library projects referenced by this project. This should only be set during
     * project initialization.
     *
     * @param libraries the new library list.
     */
    public void setDirectLibraries(@NonNull List<Project> libraries) {
        directLibraries = libraries;
    }

    /**
     * Returns the transitive closure of the library projects for this project
     *
     * @return the transitive closure of the library projects for this project
     */
    @NonNull
    public List<Project> getAllLibraries() {
        if (allLibraries == null) {
            if (directLibraries.isEmpty()) {
                allLibraries = Collections.emptyList();
                return allLibraries;
            }

            List<Project> all = new ArrayList<>();
            Set<Project> seen = Sets.newHashSet();
            Set<Project> path = Sets.newHashSet();
            seen.add(this);
            path.add(this);
            addLibraryProjects(all, seen, path);
            allLibraries = all;
        }

        return allLibraries;
    }

    /**
     * Adds this project's library project and their library projects recursively into the given
     * collection of projects
     *
     * @param collection the collection to add the projects into
     * @param seen full set of projects we've processed
     * @param path the current path of library dependencies followed
     */
    private void addLibraryProjects(
            @NonNull Collection<Project> collection,
            @NonNull Set<Project> seen,
            @NonNull Set<Project> path) {
        for (Project library : directLibraries) {
            if (seen.contains(library)) {
                if (path.contains(library)) {
                    client.log(
                            Severity.WARNING,
                            null,
                            "Internal lint error: cyclic library dependency for %1$s",
                            library);
                }
                continue;
            }
            collection.add(library);
            seen.add(library);
            path.add(library);
            // Recurse
            library.addLibraryProjects(collection, seen, path);
            path.remove(library);
        }
    }

    /**
     * The type of artifact produced by this Android project.
     *
     * @deprecated Use {@link #getType()} instead!
     */
    @NonNull
    @Deprecated
    public final ProjectType getProjectType() {
        LintModelModuleType type = getType();
        switch (type) {
            case APP:
                //noinspection DuplicateBranchesInSwitch
                return ProjectType.APP;
            case LIBRARY:
                return ProjectType.LIBRARY;
            case TEST:
                return ProjectType.TEST;
            case INSTANT_APP:
                return ProjectType.INSTANT_APP;
            case FEATURE:
                return ProjectType.FEATURE;
            case DYNAMIC_FEATURE:
                return ProjectType.DYNAMIC_FEATURE;
            default:
                return ProjectType.APP;
        }
    }

    /** The type of artifact produced by this Android project. */
    @NonNull
    public LintModelModuleType getType() {
        LintModelModule buildModule = getBuildModule();
        if (buildModule != null) {
            return buildModule.getType();
        }
        if (isLibrary()) {
            return LintModelModuleType.LIBRARY;
        } else {
            return LintModelModuleType.APP;
        }
    }

    /**
     * Whether this project is a base application with dynamic features.
     *
     * @return true if this is an application project that has any dynamic features, false in all
     *     other cases.
     */
    public boolean hasDynamicFeatures() {
        return false;
    }

    /**
     * Gets the SDK info for the current project.
     *
     * @return the SDK info for the current project, never null
     */
    @NonNull
    public SdkInfo getSdkInfo() {
        if (sdkInfo == null) {
            sdkInfo = client.getSdkInfo(this);
        }

        return sdkInfo;
    }

    /**
     * Gets the paths to the manifest files in this project, if any exists. The manifests should be
     * provided such that the main manifest comes first, then any flavor versions, then any build
     * types.
     *
     * @return the path to the manifest file, or null if it does not exist
     */
    @NonNull
    public List<File> getManifestFiles() {
        if (manifestFiles == null) {
            File manifestFile = new File(dir, ANDROID_MANIFEST_XML);
            if (manifestFile.exists()) {
                manifestFiles = Collections.singletonList(manifestFile);
            } else {
                manifestFiles = Collections.emptyList();
            }
        }

        return manifestFiles;
    }

    /**
     * Returns the proguard files configured for this project, if any
     *
     * @return the proguard files, if any
     */
    @NonNull
    public List<File> getProguardFiles() {
        if (proguardFiles == null) {
            List<File> files = new ArrayList<>();
            if (proguardPath != null) {
                Splitter splitter = Splitter.on(CharMatcher.anyOf(":;"));
                for (String path : splitter.split(proguardPath)) {
                    if (path.contains("${")) {
                        // Don't analyze the global/user proguard files
                        continue;
                    }
                    File file = new File(path);
                    if (!file.isAbsolute()) {
                        file = new File(getDir(), path);
                    }
                    if (file.exists()) {
                        files.add(file);
                    }
                }
            }
            if (files.isEmpty()) {
                File file = new File(getDir(), OLD_PROGUARD_FILE);
                if (file.exists()) {
                    files.add(file);
                }
                file = new File(getDir(), FN_PROJECT_PROGUARD_FILE);
                if (file.exists()) {
                    files.add(file);
                }
            }
            proguardFiles = files;
        }
        return proguardFiles;
    }

    /** Returns the .properties files to be analyzed in the project */
    @NonNull
    public List<File> getPropertyFiles() {
        List<File> propertyFiles = new ArrayList<>(2);
        File local = new File(dir, FN_LOCAL_PROPERTIES);
        if (local.isFile()) {
            propertyFiles.add(local);
        }
        File gradle = new File(dir, FN_GRADLE_PROPERTIES);
        if (gradle.isFile()) {
            propertyFiles.add(gradle);
        }
        File wrapper = new File(dir, FD_GRADLE_WRAPPER + separator + FN_GRADLE_WRAPPER_PROPERTIES);
        if (wrapper.isFile()) {
            propertyFiles.add(wrapper);
        }
        return propertyFiles;
    }

    /**
     * Returns the Gradle build script files configured for this project, if any
     *
     * @return the Gradle files, if any
     */
    @NonNull
    public List<File> getGradleBuildScripts() {
        if (gradleFiles == null) {
            if (isGradleProject()) {
                gradleFiles = Lists.newArrayListWithExpectedSize(2);
                File build = new File(dir, FN_BUILD_GRADLE);
                if (build.exists()) {
                    gradleFiles.add(build);
                }
                build = new File(dir, FN_BUILD_GRADLE_KTS);
                if (build.exists()) {
                    gradleFiles.add(build);
                }
                File settings = new File(dir, FN_SETTINGS_GRADLE);
                if (settings.exists()) {
                    gradleFiles.add(settings);
                }
                settings = new File(dir, FN_SETTINGS_GRADLE_KTS);
                if (settings.exists()) {
                    gradleFiles.add(settings);
                }
            } else {
                gradleFiles = Collections.emptyList();
            }
        }

        return gradleFiles;
    }

    /**
     * Returns the name of the project
     *
     * @return the name of the project, never null
     */
    @NonNull
    public String getName() {
        if (name == null) {
            name = client.getProjectName(this);
        }

        return name;
    }

    /**
     * Sets the name of the project
     *
     * @param name the name of the project, never null
     */
    public void setName(@NonNull String name) {
        assert !name.isEmpty();
        this.name = name;
    }

    /**
     * Sets whether lint should report issues in this project. See {@link #getReportIssues()} for a
     * full description of what that means.
     *
     * @param reportIssues whether lint should report issues in this project
     */
    public void setReportIssues(boolean reportIssues) {
        this.reportIssues = reportIssues;
    }

    /**
     * Returns whether lint should report issues in this project.
     *
     * <p>If a user specifies a project and its library projects for analysis, then those library
     * projects are all "included", and all errors found in all the projects are reported. But if
     * the user is only running lint on the main project, we shouldn't report errors in any of the
     * library projects. We still need to <b>consider</b> them for certain types of checks, such as
     * determining whether resources found in the main project are unused, so the detectors must
     * still get a chance to look at these projects. The {@code #getReportIssues()} attribute is
     * used for this purpose.
     *
     * @return whether lint should report issues in this project
     */
    public boolean getReportIssues() {
        return reportIssues;
    }

    /**
     * Returns whether manifest merging is in effect
     *
     * @return true if manifests in library projects should be merged into main projects
     */
    public boolean isMergingManifests() {
        return mergeManifests;
    }

    // ---------------------------------------------------------------------------
    // Support for running lint on the AOSP source tree itself

    private static Boolean sAospBuild;

    /** Is lint running in an AOSP build environment */
    public static boolean isAospBuildEnvironment() {
        if (sAospBuild == null) {
            sAospBuild = getAospTop() != null;
        }

        return sAospBuild;
    }

    /**
     * Is this the frameworks or related AOSP project? Needs some hardcoded support since it doesn't
     * have a manifest file, etc.
     *
     * <p>A frameworks AOSP projects can be any directory under "frameworks" that 1. Is not the
     * "support" directory (which uses the public support annotations) 2. Doesn't have an
     * AndroidManifest.xml (it's an app instead)
     *
     * @param dir the project directory to check
     * @return true if this looks like the frameworks/dir project and does not have an
     *     AndroidManifest.xml
     */
    public static boolean isAospFrameworksRelatedProject(@NonNull File dir) {
        if (isAospBuildEnvironment()) {
            File frameworks = new File(getAospTop(), "frameworks");
            String frameworksDir = frameworks.getAbsolutePath();
            String supportDir = new File(frameworks, "support").getAbsolutePath();
            if (dir.exists()
                    && !dir.getAbsolutePath().startsWith(supportDir)
                    && dir.getAbsolutePath().startsWith(frameworksDir)
                    && !(new File(dir, FN_ANDROID_MANIFEST_XML).exists())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Is this the actual frameworks project.
     *
     * @param dir the project directory to check.
     * @return true if this is the frameworks project.
     */
    @SuppressWarnings("FileComparisons")
    public static boolean isAospFrameworksProject(@NonNull File dir) {
        String top = getAospTop();
        if (top != null) {
            File toCompare = new File(top, "frameworks" + separator + "base" + separator + "core");
            try {
                return dir.getCanonicalFile().equals(toCompare) && dir.exists();
            } catch (IOException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    /** Get the root AOSP dir, if any */
    private static String getAospTop() {
        return System.getenv("ANDROID_BUILD_TOP");
    }

    /** Get the host out directory in AOSP, if any */
    private static String getAospHostOut() {
        return System.getenv("ANDROID_HOST_OUT");
    }

    /** Get the product out directory in AOSP, if any */
    private static String getAospProductOut() {
        return System.getenv("ANDROID_PRODUCT_OUT");
    }

    private List<File> getAospJavaSourcePath() {
        List<File> sources = new ArrayList<>(2);
        // Normal sources
        File src = new File(dir, "src");
        if (src.exists()) {
            sources.add(src);
        }

        // Generates sources
        for (File dir : getIntermediateDirs()) {
            File classes = new File(dir, "src");
            if (classes.exists()) {
                sources.add(classes);
            }
        }

        if (sources.isEmpty()) {
            client.log(
                    null,
                    "Warning: Could not find sources or generated sources for project %1$s",
                    getName());
        }

        return sources;
    }

    private List<File> getAospJavaClassPath() {
        List<File> classDirs = new ArrayList<>(1);

        for (File dir : getIntermediateDirs()) {
            File classes = new File(dir, "classes");
            if (classes.exists()) {
                classDirs.add(classes);
            } else {
                classes = new File(dir, "classes.jar");
                if (classes.exists()) {
                    classDirs.add(classes);
                }
            }
        }

        if (classDirs.isEmpty()) {
            client.log(null, "No bytecode found: Has the project been built? (%1$s)", getName());
        }

        return classDirs;
    }

    /** Find the _intermediates directories for a given module name */
    private List<File> getIntermediateDirs() {
        // See build/core/definitions.mk and in particular the "intermediates-dir-for" definition
        List<File> intermediates = new ArrayList<>();

        // TODO: Look up the module name, e.g. LOCAL_MODULE. However,
        // some Android.mk files do some complicated things with it - and most
        // projects use the same module name as the directory name.
        String moduleName = dir.getName();
        try {
            // Get the actual directory name instead of '.' that's possible
            // when using this via CLI.
            moduleName = dir.getCanonicalFile().getName();
        } catch (IOException ioe) {
            // pass
        }

        String top = getAospTop();
        final String[] outFolders =
                new String[] {
                    top + "/out/host/common/obj",
                    top + "/out/target/common/obj",
                    getAospHostOut() + "/obj",
                    getAospProductOut() + "/obj"
                };
        final String[] moduleClasses =
                new String[] {
                    "APPS", "JAVA_LIBRARIES",
                };

        for (String out : outFolders) {
            assert new File(out.replace('/', File.separatorChar)).exists() : out;
            for (String moduleClass : moduleClasses) {
                String path = out + '/' + moduleClass + '/' + moduleName + "_intermediates";
                File file = new File(path.replace('/', File.separatorChar));
                if (file.exists()) {
                    intermediates.add(file);
                }
            }
        }

        return intermediates;
    }

    private void extractAospMinSdkVersion() {
        // Is the SDK level specified by a Makefile?
        boolean found = false;
        File makefile = new File(dir, "Android.mk");
        if (makefile.exists()) {
            List<String> lines = FilesKt.readLines(makefile, Charsets.UTF_8);
            Pattern p = Pattern.compile("LOCAL_SDK_VERSION\\s*:=\\s*(.*)");
            for (String line : lines) {
                line = line.trim();
                Matcher matcher = p.matcher(line);
                if (matcher.matches()) {
                    found = true;
                    String version = matcher.group(1);
                    if (version.equals("current")) {
                        manifestMinSdk = findCurrentAospVersion();
                    } else {
                        manifestMinSdk = SdkVersionInfo.getVersion(version, client.getTargets());
                    }
                    break;
                }
            }
        }

        if (!found) {
            manifestMinSdk = findCurrentAospVersion();
        }
    }

    /** Cache for {@link #findCurrentAospVersion()} */
    private static AndroidVersion sCurrentVersion;

    /** In an AOSP build environment, identify the currently built image version, if available */
    private static AndroidVersion findCurrentAospVersion() {
        if (sCurrentVersion == null) {
            File versionMk =
                    new File(
                            getAospTop(),
                            "build/core/version_defaults.mk".replace('/', File.separatorChar));

            if (!versionMk.exists()) {
                sCurrentVersion = AndroidVersion.DEFAULT;
                return sCurrentVersion;
            }
            int sdkVersion = LOWEST_ACTIVE_API;
            try {
                Pattern p = Pattern.compile("PLATFORM_SDK_VERSION\\s*:=\\s*(.*)");
                List<String> lines = Files.readLines(versionMk, Charsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    Matcher matcher = p.matcher(line);
                    if (matcher.matches()) {
                        String version = matcher.group(1);
                        try {
                            sdkVersion = Integer.parseInt(version);
                        } catch (NumberFormatException nfe) {
                            // pass
                        }
                        break;
                    }
                }
            } catch (IOException io) {
                // pass
            }
            sCurrentVersion = new AndroidVersion(sdkVersion, null);
        }

        return sCurrentVersion;
    }

    /**
     * Returns true if this project depends on the given artifact. Note that the project doesn't
     * have to be a Gradle project; the artifact is just an identifier for name a specific library,
     * such as com.android.support:support-v4 to identify the support library
     *
     * @param artifact the Gradle/Maven name of a library
     * @return true if the library is installed, false if it is not, and null if we're not sure
     */
    @Nullable
    public Boolean dependsOn(@NonNull String artifact) {
        artifact = AndroidxNameUtils.getCoordinateMapping(artifact);

        if (ANDROIDX_SUPPORT_LIB_ARTIFACT.equals(artifact)) {
            if (supportLib == null) {
                for (File file : getJavaLibraries(true)) {
                    String name = file.getName();
                    if (name.equals("android-support-v4.jar")
                            || name.startsWith("support-v4-")
                            || name.startsWith("legacy-support-")) {
                        supportLib = true;
                        break;
                    }
                }
                if (supportLib == null) {
                    for (Project dependency : getDirectLibraries()) {
                        Boolean b = dependency.dependsOn(artifact);
                        if (b != null && b) {
                            supportLib = true;
                            break;
                        }
                    }
                }
                if (supportLib == null) {
                    supportLib = false;
                }
            }

            return supportLib;
        } else if (ANDROIDX_APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
            if (appCompat == null) {
                for (File file : getJavaLibraries(true)) {
                    String name = file.getName();
                    if (name.startsWith("appcompat")) {
                        appCompat = true;
                        break;
                    }
                }
                if (appCompat == null) {
                    for (Project dependency : getDirectLibraries()) {
                        Boolean b = dependency.dependsOn(artifact);
                        if (b != null && b) {
                            appCompat = true;
                            break;
                        }
                    }
                }
                if (appCompat == null) {
                    appCompat = false;
                }
            }

            return appCompat;
        }

        return null;
    }

    private List<String> mCachedApplicableDensities;

    /**
     * Returns the set of applicable densities for this project. If null, there are no density
     * restrictions and all densities apply.
     *
     * @return the list of specific densities that apply in this project, or null if all densities
     *     apply
     */
    @Nullable
    public List<String> getApplicableDensities() {
        if (mCachedApplicableDensities == null) {
            // Use the gradle API to set up relevant densities. For example, if the
            // build.gradle file contains this:
            // android {
            //     defaultConfig {
            //         resConfigs "nodpi", "hdpi"
            //     }
            // }
            // ...then we should only enforce hdpi densities, not all these others!
            LintModelVariant variant = getBuildVariant();
            if (variant != null) {
                Set<String> relevantDensities = Sets.newHashSet();
                for (String densityName : variant.getResourceConfigurations()) {
                    Density density = Density.getEnum(densityName);
                    if (density != null
                            && density.isRecommended()
                            && density != Density.NODPI
                            && density != Density.ANYDPI) {
                        relevantDensities.add(densityName);
                    }
                }
                if (!relevantDensities.isEmpty()) {
                    mCachedApplicableDensities =
                            Lists.newArrayListWithExpectedSize(relevantDensities.size());
                    for (String density : relevantDensities) {
                        String folder = ResourceFolderType.DRAWABLE.getName() + '-' + density;
                        mCachedApplicableDensities.add(folder);
                    }
                    Collections.sort(mCachedApplicableDensities);
                } else {
                    mCachedApplicableDensities = Collections.emptyList();
                }
            } else {
                mCachedApplicableDensities = Collections.emptyList();
            }
        }

        return mCachedApplicableDensities.isEmpty() ? null : mCachedApplicableDensities;
    }

    /**
     * Returns a super class map for this project. The keys and values are internal class names
     * (e.g. java/lang/Integer, not java.lang.Integer).
     *
     * @return a map, possibly empty but never null
     */
    @NonNull
    public Map<String, String> getSuperClassMap() {
        if (superClassMap == null) {
            superClassMap = client.createSuperClassMap(this);
        }

        return superClassMap;
    }

    /**
     * Returns a shared {@link ResourceVisibilityLookup}
     *
     * @return a shared provider for looking up resource visibility
     */
    @NonNull
    public ResourceVisibilityLookup getResourceVisibility() {
        if (resourceVisibility == null) {
            LintModelVariant variant = getBuildVariant();
            if (variant != null) {
                Collection<LintModelLibrary> libraries =
                        variant.getMainArtifact().getDependencies().getAll();
                List<ResourceVisibilityLookup> list = new ArrayList<>(libraries.size());
                for (LintModelLibrary library : libraries) {
                    if (library instanceof LintModelAndroidLibrary) {
                        LintModelAndroidLibrary l = (LintModelAndroidLibrary) library;
                        ResourceVisibilityLookup lookup = createLibraryVisibilityLookup(l);
                        list.add(lookup);
                    }
                }
                resourceVisibility = ResourceVisibilityLookup.create(list);
            } else if (getBuildLibraryModel() != null) {
                LintModelAndroidLibrary library = getBuildLibraryModel();
                resourceVisibility = createLibraryVisibilityLookup(library);
            } else {
                resourceVisibility = ResourceVisibilityLookup.NONE;
            }
        }

        return resourceVisibility;
    }

    @NonNull
    private ResourceVisibilityLookup createLibraryVisibilityLookup(
            LintModelAndroidLibrary androidLibrary) {
        File publicResources = androidLibrary.getPublicResources();
        File symbolFile = androidLibrary.getSymbolFile();
        LintModelMavenName c = androidLibrary.getResolvedCoordinates();
        String key = c.getGroupId() + ":" + c.getArtifactId() + ":" + c.getVersion();
        return ResourceVisibilityLookup.create(publicResources, symbolFile, key);
    }

    /**
     * Returns the associated client
     *
     * @return the client
     */
    @NonNull
    public LintClient getClient() {
        return client;
    }

    /**
     * Merge in source folders from the given project. This is only intended to be used by the lint
     * infrastructure.
     */
    public void mergeFolders(@NonNull Project p) {
        javaSourceFolders = merge(getJavaSourceFolders(), p.getJavaSourceFolders());
        resourceFolders = merge(getResourceFolders(), p.getResourceFolders());
        generatedResourceFolders =
                merge(getGeneratedResourceFolders(), p.getGeneratedResourceFolders());
        testSourceFolders = merge(getTestSourceFolders(), p.getTestSourceFolders());
        testLibraries = merge(getTestLibraries(), p.getTestLibraries());
        nonProvidedJavaLibraries = merge(getJavaLibraries(false), p.getJavaLibraries(false));
        javaLibraries = merge(getJavaLibraries(true), p.getJavaLibraries(true));
    }

    private static List<File> merge(List<File> existing, List<File> additional) {
        if (additional.isEmpty()) {
            return existing;
        } else if (existing.isEmpty()) {
            return additional;
        } else {
            List<File> newList = new ArrayList<File>(existing.size() + additional.size());
            newList.addAll(existing);
            newList.addAll(additional);
            return newList;
        }
    }
}
