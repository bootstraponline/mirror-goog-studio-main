package com.android.build.gradle.tasks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.annotations.ApkFile;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.dsl.AbiSplitOptions;
import com.android.build.gradle.internal.dsl.CoreSigningConfig;
import com.android.build.gradle.internal.dsl.PackagingOptions;
import com.android.build.gradle.internal.incremental.InstantRunBuildContext;
import com.android.build.gradle.internal.packaging.ApkCreatorFactories;
import com.android.build.gradle.internal.pipeline.ExtendedContentType;
import com.android.build.gradle.internal.pipeline.FilterableStreamCollection;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantOutputScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.FileSupplier;
import com.android.build.gradle.internal.tasks.IncrementalTask;
import com.android.build.gradle.internal.tasks.ValidateSigningTask;
import com.android.build.gradle.internal.transforms.InstantRunSlicer;
import com.android.build.gradle.internal.variant.ApkVariantData;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.files.FileCacheByPath;
import com.android.builder.files.IncrementalRelativeFileSets;
import com.android.builder.files.RelativeFile;
import com.android.builder.internal.packaging.IncrementalPackager;
import com.android.builder.internal.utils.CachedFileContents;
import com.android.builder.internal.utils.IOExceptionWrapper;
import com.android.builder.model.ApiVersion;
import com.android.builder.packaging.ApkCreatorFactory;
import com.android.builder.packaging.DuplicateFileException;
import com.android.ide.common.res2.FileStatus;
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper;
import com.android.utils.FileUtils;
import com.android.utils.StringHelper;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import com.google.common.io.Files;

import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.tooling.BuildException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@ParallelizableTask
public class PackageAndroidArtifact extends IncrementalTask implements FileSupplier {

    /**
     * If {@code true}, the tasks works with the old code.
     */
    private boolean inOldMode;

    public enum DexPackagingPolicy {
        /**
         * Standard Dex packaging policy, all dex files will be packaged at the root of the APK.
         */
        STANDARD,

        /**
         * InstantRun specific Dex packaging policy, all dex files with a name containing
         * {@link InstantRunSlicer#MAIN_SLICE_NAME} will be packaged at the root of the APK while
         * all other dex files will be packaged in a instant-run.zip itself packaged at the root
         * of the APK.
         */
        INSTANT_RUN
    }

    public static final String INSTANT_RUN_PACKAGES_PREFIX = "instant-run";

    public static final FilterableStreamCollection.StreamFilter sDexFilter =
            new TransformManager.StreamFilter() {
                @Override
                public boolean accept(@NonNull Set<ContentType> types, @NonNull Set<Scope> scopes) {
                    return types.contains(ExtendedContentType.DEX);
                }
            };

    public static final FilterableStreamCollection.StreamFilter sResFilter =
            new TransformManager.StreamFilter() {
                @Override
                public boolean accept(@NonNull Set<ContentType> types, @NonNull Set<Scope> scopes) {
                    return types.contains(QualifiedContent.DefaultContentType.RESOURCES) &&
                            !scopes.contains(Scope.PROVIDED_ONLY) &&
                            !scopes.contains(Scope.TESTED_CODE);
                }
            };

    public static final FilterableStreamCollection.StreamFilter sNativeLibsFilter =
            new TransformManager.StreamFilter() {
                @Override
                public boolean accept(@NonNull Set<ContentType> types, @NonNull Set<Scope> scopes) {
                    return types.contains(ExtendedContentType.NATIVE_LIBS) &&
                            !scopes.contains(Scope.PROVIDED_ONLY) &&
                            !scopes.contains(Scope.TESTED_CODE);
                }
            };

    // ----- PUBLIC TASK API -----

    @InputFile
    public File getResourceFile() {
        return resourceFile;
    }

    public void setResourceFile(File resourceFile) {
        this.resourceFile = resourceFile;
    }

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @Input
    public Set<String> getAbiFilters() {
        return abiFilters;
    }

    public void setAbiFilters(Set<String> abiFilters) {
        this.abiFilters = abiFilters;
    }

    // ----- PRIVATE TASK API -----

    @InputFiles
    @Optional
    public Collection<File> getJavaResourceFiles() {
        return javaResourceFiles;
    }
    @InputFiles
    @Optional
    public Collection<File> getJniFolders() {
        return jniFolders;
    }
    private File resourceFile;

    private Set<File> dexFolders;
    @InputFiles
    public Set<File> getDexFolders() {
        return dexFolders;
    }

    /** list of folders and/or jars that contain the merged java resources. */
    private Set<File> javaResourceFiles;
    private Set<File> jniFolders;

    @ApkFile
    private File outputFile;

    private Set<String> abiFilters;

    private boolean jniDebugBuild;

    private CoreSigningConfig signingConfig;

    private PackagingOptions packagingOptions;

    private ApiVersion minSdkVersion;

    private InstantRunBuildContext instantRunContext;

    private File instantRunSupportDir;

    private VariantScope scope;

    /**
     * Name of directory, inside the intermediate directory, where zip caches are kept.
     */
    private static final String ZIP_DIFF_CACHE_DIR = "zip-cache";

    /**
     * Zip caches to allow incremental updates.
     */
    private FileCacheByPath cacheByPath;

    @Input
    public boolean getJniDebugBuild() {
        return jniDebugBuild;
    }

    public boolean isJniDebugBuild() {
        return jniDebugBuild;
    }

    public void setJniDebugBuild(boolean jniDebugBuild) {
        this.jniDebugBuild = jniDebugBuild;
    }

    @Nested
    @Optional
    public CoreSigningConfig getSigningConfig() {
        return signingConfig;
    }

    public void setSigningConfig(CoreSigningConfig signingConfig) {
        this.signingConfig = signingConfig;
    }

    @Nested
    public PackagingOptions getPackagingOptions() {
        return packagingOptions;
    }

    public void setPackagingOptions(PackagingOptions packagingOptions) {
        this.packagingOptions = packagingOptions;
    }

    @Input
    public int getMinSdkVersion() {
        return this.minSdkVersion.getApiLevel();
    }

    public void setMinSdkVersion(ApiVersion version) {
        this.minSdkVersion = version;
    }

    @InputFile
    public File getMarkerFile() {
        return markerFile;
    }

    private File markerFile;

    DexPackagingPolicy dexPackagingPolicy;

    @Input
    String getDexPackagingPolicy() {
        return dexPackagingPolicy.toString();
    }

    @Override
    protected void doFullTaskAction() throws IOException {
        if (inOldMode) {
            doOldTask();
            return;
        }

        /*
         * Clear the cache to make sure we have do not do an incremental build.
         */
        cacheByPath.clear();

        /*
         * Also clear the intermediate build directory. We don't know if anything is in there and
         * since this is a full build, we don't want to get any interference from previous state.
         */
        FileUtils.deleteDirectoryContents(getIncrementalFolder());

        Set<File> androidResources = new HashSet<>();
        File androidResourceFile = getResourceFile();
        if (androidResourceFile != null) {
            androidResources.add(androidResourceFile);
        }

        /*
         * Additionally, make sure we have no previous package, if it exists.
         */
        getOutputFile().delete();

        ImmutableMap<RelativeFile, FileStatus> updatedDex =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getDexFolders());
        ImmutableMap<RelativeFile, FileStatus> updatedJavaResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJavaResourceFiles());
        ImmutableMap<RelativeFile, FileStatus> updatedAndroidResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
        ImmutableMap<RelativeFile, FileStatus> updatedJniResources=
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJniFolders());

        doTask(updatedDex, updatedJavaResources, updatedAndroidResources, updatedJniResources);

        /*
         * Update the known files.
         */
        KnownFilesSaveData saveData = KnownFilesSaveData.make(getIncrementalFolder());
        saveData.setInputSet(updatedDex.keySet(), InputSet.DEX);
        saveData.setInputSet(updatedJavaResources.keySet(), InputSet.JAVA_RESOURCE);
        saveData.setInputSet(updatedAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
        saveData.setInputSet(updatedJniResources.keySet(), InputSet.NATIVE_RESOURCE);
        saveData.saveCurrentData();
    }

    /**
     * Old packaging code.
     */
    private void doOldTask() {
        // if the blocker file is there, do not run.
        if (getMarkerFile().exists()) {
            try {
                if (MarkerFile.readMarkerFile(getMarkerFile()) == MarkerFile.Command.BLOCK) {
                    return;
                }
            } catch (IOException e) {
                getLogger().warn("Cannot read marker file, proceed with execution", e);
            }
        }

        try {

            ImmutableSet.Builder<File> dexFoldersForApk = ImmutableSet.builder();
            ImmutableList.Builder<File> javaResourcesForApk = ImmutableList.builder();

            Collection<File> javaResourceFiles = getJavaResourceFiles();
            if (javaResourceFiles != null) {
                javaResourcesForApk.addAll(javaResourceFiles);
            }
            switch(dexPackagingPolicy) {
                case INSTANT_RUN:
                    File zippedDexes = zipDexesForInstantRun(getDexFolders(), dexFoldersForApk);
                    javaResourcesForApk.add(zippedDexes);
                    break;
                case STANDARD:
                    dexFoldersForApk.addAll(getDexFolders());
                    break;
                default:
                    throw new RuntimeException(
                            "Unhandled DexPackagingPolicy : " + getDexPackagingPolicy());
            }

            getBuilder().oldPackageApk(
                    getResourceFile().getAbsolutePath(),
                    dexFoldersForApk.build(),
                    javaResourcesForApk.build(),
                    getJniFolders(),
                    getAbiFilters(),
                    getJniDebugBuild(),
                    getSigningConfig(),
                    getOutputFile(),
                    getMinSdkVersion(),
                    getIncrementalFolder());
        } catch (DuplicateFileException e) {
            Logger logger = getLogger();
            logger.error("Error: duplicate files during packaging of APK " + getOutputFile()
                    .getAbsolutePath());
            logger.error("\tPath in archive: " + e.getArchivePath());
            int index = 1;
            for (File file : e.getSourceFiles()) {
                logger.error("\tOrigin " + (index++) + ": " + file);
            }
            logger.error("You can ignore those files in your build.gradle:");
            logger.error("\tandroid {");
            logger.error("\t  packagingOptions {");
            logger.error("\t    exclude \'" + e.getArchivePath() + "\'");
            logger.error("\t  }");
            logger.error("\t}");
            throw new BuildException(e.getMessage(), e);
        } catch (Exception e) {
            //noinspection ThrowableResultOfMethodCallIgnored
            Throwable rootCause = Throwables.getRootCause(e);
            if (rootCause instanceof NoSuchAlgorithmException) {
                throw new BuildException(
                        rootCause.getMessage() + ": try using a newer JVM to build your application.",
                        rootCause);
            }
            throw new BuildException(e.getMessage(), e);
        }
        // mark this APK production, this will eventually be saved when instant-run is enabled.
        // this might get overriden if the apk is signed/aligned.
        try {
            instantRunContext.addChangedFile(InstantRunBuildContext.FileType.MAIN,
                    getOutputFile());
        } catch (IOException e) {
            throw new BuildException(e.getMessage(), e);
        }
    }

    private File zipDexesForInstantRun(Iterable<File> dexFolders,
            ImmutableSet.Builder<File> dexFoldersForApk)
            throws IOException {

        File tmpZipFile = new File(instantRunSupportDir, "classes.zip");
        Files.createParentDirs(tmpZipFile);
        ZipOutputStream zipFile = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmpZipFile)));
        // no need to compress a zip, the APK itself gets compressed.
        zipFile.setLevel(0);

        try {
            for (File dexFolder : dexFolders) {
                if (dexFolder.getName().contains(INSTANT_RUN_PACKAGES_PREFIX)) {
                    dexFoldersForApk.add(dexFolder);
                } else {
                    for (File file : Files.fileTreeTraverser().breadthFirstTraversal(dexFolder)) {
                        if (file.isFile() && file.getName().endsWith(SdkConstants.DOT_DEX)) {
                            // There are several pieces of code in the runtime library which depends on
                            // this exact pattern, so it should not be changed without thorough testing
                            // (it's basically part of the contract).
                            String entryName = file.getParentFile().getName() + "-" + file.getName();
                            zipFile.putNextEntry(new ZipEntry(entryName));
                            try {
                                Files.copy(file, zipFile);
                            } finally {
                                zipFile.closeEntry();
                            }
                        }

                    }
                }
            }
        } finally {
            zipFile.close();
        }

        // now package that zip file as a zip since this is what the packager is expecting !
        File finalResourceFile = new File(instantRunSupportDir, "resources.zip");
        zipFile = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(finalResourceFile)));
        try {
            zipFile.putNextEntry(new ZipEntry("instant-run.zip"));
            try {
                Files.copy(tmpZipFile, zipFile);
            } finally {
                zipFile.closeEntry();
            }
        } finally {
            zipFile.close();
        }

        return finalResourceFile;
    }

    /**
     * Packages the application incrementally. In case of instant run packaging, this is not a
     * perfectly incremental task as some files are always rewritten even if no change has
     * occurred.
     *
     * @param changedDex incremental dex packaging data
     * @param changedJavaResources incremental java resources
     * @param changedAndroidResources incremental Android resource
     * @param changedNLibs incremental native libraries changed
     * @throws IOException failed to package the APK
     */
    private void doTask(@NonNull ImmutableMap<RelativeFile, FileStatus> changedDex,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedJavaResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedAndroidResources,
            @NonNull ImmutableMap<RelativeFile, FileStatus> changedNLibs)
            throws IOException {
        // if the blocker file is there, do not run.
        if (getMarkerFile().exists()) {
            try {
                if (MarkerFile.readMarkerFile(getMarkerFile()) == MarkerFile.Command.BLOCK) {
                    return;
                }
            } catch (IOException e) {
                getLogger().warn("Cannot read marker file, proceed with execution.", e);
            }
        }

        ImmutableMap.Builder<RelativeFile, FileStatus> javaResourcesForApk =
                ImmutableMap.builder();
        javaResourcesForApk.putAll(changedJavaResources);

        switch(dexPackagingPolicy) {
            case INSTANT_RUN:
                /*
                 * If we're doing instant run, then we don't want to treat all dex archives
                 * as dex archives for packaging. We will package some of the dex files as
                 * resources.
                 *
                 * All dex files in directories whose name contains INSTANT_RUN_PACKAGES_PREFIX
                 * are kept in the apk as dex files. All other dex files are placed as
                 * resources as defined by makeInstantRunResourcesFromDex.
                 */
                ;
                Collection<File> instantRunDexBaseFiles = getDexFolders()
                        .stream()
                        .filter(input -> input.getName().contains(INSTANT_RUN_PACKAGES_PREFIX))
                        .collect(Collectors.toSet());
                Iterable<File> nonInstantRunDexBaseFiles = getDexFolders()
                        .stream()
                        .filter(f -> !instantRunDexBaseFiles.contains(f))
                        .collect(Collectors.toSet());


                ImmutableMap<RelativeFile, FileStatus> newInstantRunResources =
                        makeInstantRunResourcesFromDex(nonInstantRunDexBaseFiles);

                @SuppressWarnings("unchecked")
                ImmutableMap<RelativeFile, FileStatus> updatedChangedResources =
                        IncrementalRelativeFileSets.union(
                                Sets.newHashSet(changedJavaResources, newInstantRunResources));
                changedJavaResources = updatedChangedResources;

                changedDex = ImmutableMap.copyOf(
                        Maps.filterKeys(
                                changedDex,
                                Predicates.compose(
                                        Predicates.in(instantRunDexBaseFiles),
                                        RelativeFile.EXTRACT_BASE
                                )));

                break;
            case STANDARD:
                break;
            default:
                throw new RuntimeException(
                        "Unhandled DexPackagingPolicy : " + getDexPackagingPolicy());
        }

        PrivateKey key;
        X509Certificate certificate;


        Closer closer = Closer.create();
        try {
            if (signingConfig != null && signingConfig.isSigningReady()) {
                CertificateInfo certificateInfo = KeystoreHelper.getCertificateInfo(
                        signingConfig.getStoreType(),
                        Preconditions.checkNotNull(signingConfig.getStoreFile()),
                        Preconditions.checkNotNull(signingConfig.getStorePassword()),
                        Preconditions.checkNotNull(signingConfig.getKeyPassword()),
                        Preconditions.checkNotNull(signingConfig.getKeyAlias()));
                key = certificateInfo.getKey();
                certificate = certificateInfo.getCertificate();
            } else {
                key = null;
                certificate = null;
            }

            ApkCreatorFactory.CreationData creationData =
                    new ApkCreatorFactory.CreationData(
                            getOutputFile(),
                            key,
                            certificate,
                            null,   // BuiltBy
                            getBuilder().getCreatedBy(),
                            getMinSdkVersion());

            IncrementalPackager packager = closer.register(new IncrementalPackager(creationData,
                    getIncrementalFolder(), ApkCreatorFactories.fromProjectProperties(scope),
                    getAbiFilters(), getJniDebugBuild()));

            packager.updateDex(changedDex);
            packager.updateJavaResources(changedJavaResources);
            packager.updateAndroidResources(changedAndroidResources);
            packager.updateNativeLibraries(changedNLibs);
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }

        /*
         * Save all used zips in the cache.
         */
        Stream.concat(
            changedDex.keySet().stream(),
            Stream.concat(
                    changedJavaResources.keySet().stream(),
                    Stream.concat(
                            changedAndroidResources.keySet().stream(),
                            changedNLibs.keySet().stream())))
            .map(RelativeFile::getBase)
            .filter(File::isFile)
            .distinct()
            .forEach((File f) -> {
                try {
                    cacheByPath.add(f);
                } catch (IOException e) {
                    throw new IOExceptionWrapper(e);
                }
            });

        // mark this APK production, this will eventually be saved when instant-run is enabled.
        // this might get overriden if the apk is signed/aligned.
        try {
            instantRunContext.addChangedFile(InstantRunBuildContext.FileType.MAIN,
                    getOutputFile());
        } catch (IOException e) {
            throw new BuildException(e.getMessage(), e);
        }
    }

    @Override
    protected boolean isIncremental() {
        if (inOldMode) {
            return false;
        }

        return true;
    }

    @Override
    protected void doIncrementalTaskAction(Map<File, FileStatus> changedInputs) throws IOException {
        if (inOldMode) {
            doFullTaskAction();
            return;
        }

        Preconditions.checkNotNull(changedInputs, "changedInputs == null");

        super.doIncrementalTaskAction(changedInputs);

        Set<File> androidResources = new HashSet<>();
        File androidResourceFile = getResourceFile();
        if (androidResourceFile != null) {
            androidResources.add(androidResourceFile);
        }

        /*
         * Gradle tells us that files have changed, but it doesn't tell us which inputs the files
         * are associated with. This means there are files in changedInputs that are dex files,
         * some are java resources, etc. This is only relevant in the case of deleted files,
         * because those no longer show up in the original input collections.
         *
         * What we do is remove the deleted changed inputs from the map and split them in the
         * various inputs according to save data.
         *
         * We can split the non-deleted inputs based on the input sets and create the relative
         * files based on the input sets. For the deleted inputs, we have to resort to a
         * KnownFilesSaveData to build the RelativeFiles and split the deleted inputs in the
         * various sets (dex, java resources, etc.)
         */
        Map<File, FileStatus> nonDeletedChangedInputs =
                Maps.filterValues(
                        changedInputs,
                        Predicates.not(Predicates.equalTo(FileStatus.REMOVED)));
        Set<File> deletedChangedFiles =
                Maps.filterValues(changedInputs, Predicates.equalTo(FileStatus.REMOVED)).keySet();

        KnownFilesSaveData saveData = KnownFilesSaveData.make(getIncrementalFolder());
        ImmutableMap<RelativeFile, FileStatus> dexDeletedInputs =
                ImmutableMap.copyOf(
                        Maps.asMap(
                                saveData.find(deletedChangedFiles, InputSet.DEX),
                                Functions.constant(FileStatus.REMOVED)));
        ImmutableMap<RelativeFile, FileStatus> javaResourcesDeletedInputs =
                ImmutableMap.copyOf(
                        Maps.asMap(
                                saveData.find(deletedChangedFiles, InputSet.JAVA_RESOURCE),
                                Functions.constant(FileStatus.REMOVED)));
        ImmutableMap<RelativeFile, FileStatus> androidResourcesDeletedInputs =
                ImmutableMap.copyOf(
                        Maps.asMap(
                                saveData.find(deletedChangedFiles, InputSet.ANDROID_RESOURCE),
                                Functions.constant(FileStatus.REMOVED)));
        ImmutableMap<RelativeFile, FileStatus> nativeResourcesDeletedInputs =
                ImmutableMap.copyOf(
                        Maps.asMap(
                                saveData.find(deletedChangedFiles, InputSet.NATIVE_RESOURCE),
                                Functions.constant(FileStatus.REMOVED)));

        ImmutableMap<RelativeFile, FileStatus> changedDexFiles =
                ImmutableMap.<RelativeFile, FileStatus>builder()
                        .putAll(
                                IncrementalRelativeFileSets.makeFromBaseFiles(
                                        getDexFolders(),
                                        nonDeletedChangedInputs,
                                        cacheByPath))
                        .putAll(dexDeletedInputs)
                        .build();

        ImmutableMap<RelativeFile, FileStatus> changedJavaResources =
                ImmutableMap.<RelativeFile, FileStatus>builder()
                        .putAll(
                                IncrementalRelativeFileSets.makeFromBaseFiles(
                                        getJavaResourceFiles(),
                                        nonDeletedChangedInputs,
                                        cacheByPath))
                        .putAll(javaResourcesDeletedInputs)
                        .build();
        ImmutableMap<RelativeFile, FileStatus> changedNLibs =
                ImmutableMap.<RelativeFile, FileStatus>builder()
                        .putAll(
                                IncrementalRelativeFileSets.makeFromBaseFiles(
                                        getJniFolders(),
                                        nonDeletedChangedInputs,
                                        cacheByPath))
                        .putAll(nativeResourcesDeletedInputs)
                        .build();
        ImmutableMap<RelativeFile, FileStatus> changedAndroidResources =
                ImmutableMap.<RelativeFile, FileStatus>builder()
                        .putAll(
                                IncrementalRelativeFileSets.makeFromBaseFiles(
                                        androidResources,
                                        nonDeletedChangedInputs,
                                        cacheByPath))
                        .putAll(androidResourcesDeletedInputs)
                        .build();


        doTask(changedDexFiles, changedJavaResources, changedAndroidResources, changedNLibs);

        /*
         * Removed cached versions of deleted zip files because we no longer need to compute diffs.
         */
        changedInputs.keySet().stream()
                .filter(f -> !f.exists())
                .forEach(f -> {
                    try {
                        cacheByPath.remove(f);
                    } catch (IOException e) {
                        throw new IOExceptionWrapper(e);
                    }
                });

        /*
         * Update the save data keep files.
         */
        ImmutableMap<RelativeFile, FileStatus> allDex =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getDexFolders());
        ImmutableMap<RelativeFile, FileStatus> allJavaResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJavaResourceFiles());
        ImmutableMap<RelativeFile, FileStatus> allAndroidResources =
                IncrementalRelativeFileSets.fromZipsAndDirectories(androidResources);
        ImmutableMap<RelativeFile, FileStatus> allJniResources=
                IncrementalRelativeFileSets.fromZipsAndDirectories(getJniFolders());

        saveData.setInputSet(allDex.keySet(), InputSet.DEX);
        saveData.setInputSet(allJavaResources.keySet(), InputSet.JAVA_RESOURCE);
        saveData.setInputSet(allAndroidResources.keySet(), InputSet.ANDROID_RESOURCE);
        saveData.setInputSet(allJniResources.keySet(), InputSet.NATIVE_RESOURCE);
        saveData.saveCurrentData();
    }

    /**
     * Creates the new instant run resources from the dex files. This method is not
     * incremental. It will ignore updates and look at all dex files and always rebuild the
     * instant run resources.
     *
     * <p>The instant run resources are resources that package dex files.
     *
     * @param dexBaseFiles the base files to dex
     * @return the instant run resources
     * @throws IOException failed to create the instant run resources
     */
    @NonNull
    private ImmutableMap<RelativeFile, FileStatus> makeInstantRunResourcesFromDex(
            @NonNull Iterable<File> dexBaseFiles) throws IOException {

        File tmpZipFile = new File(instantRunSupportDir, "instant-run.zip");
        boolean existedBefore = tmpZipFile.exists();

        Files.createParentDirs(tmpZipFile);
        ZipOutputStream zipFile = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(tmpZipFile)));
        // no need to compress a zip, the APK itself gets compressed.
        zipFile.setLevel(0);

        try {
            for (File dexFolder : dexBaseFiles) {
                for (File file : Files.fileTreeTraverser().breadthFirstTraversal(dexFolder)) {
                    if (file.isFile() && file.getName().endsWith(SdkConstants.DOT_DEX)) {
                        // There are several pieces of code in the runtime library that depend
                        // on this exact pattern, so it should not be changed without thorough
                        // testing (it's basically part of the contract).
                        String entryName =
                                file.getParentFile().getName() + "-" + file.getName();
                        zipFile.putNextEntry(new ZipEntry(entryName));
                        try {
                            Files.copy(file, zipFile);
                        } finally {
                            zipFile.closeEntry();
                        }
                    }
                }
            }
        } finally {
            zipFile.close();
        }

        RelativeFile resourcesFile = new RelativeFile(instantRunSupportDir, tmpZipFile);
        return ImmutableMap.of(resourcesFile, existedBefore? FileStatus.CHANGED : FileStatus.NEW);
    }

    // ----- FileSupplierTask -----

    @Override
    public File get() {
        return getOutputFile();
    }

    @NonNull
    @Override
    public Task getTask() {
        return this;
    }

    /**
     * Class that keeps track of which files are known in incremental builds. Gradle tells us
     * which files were modified, but doesn't tell us which inputs the files come from so when a
     * file is marked as deleted, we don't know which input set it was deleted from. This class
     * maintains the list of files and their source locations and can be saved to the intermediate
     * directory.
     *
     * <p>File data is loaded on creation and saved on close.
     *
     * <p><i>Implementation note:</i> the actual data is saved in a property file with the
     * file name mapped to the name of the {@link InputSet} enum defining its input set.
     */
    private static class KnownFilesSaveData {

        /**
         * Name of the file with the save data.
         */
        private static final String SAVE_DATA_FILE_NAME = "file-input-save-data.txt";

        /**
         * Property with the number of files in the property file.
         */
        private static final String COUNT_PROPERTY = "count";

        /**
         * Suffix for property with the base file.
         */
        private static final String BASE_SUFFIX = ".base";

        /**
         * Suffix for property with the file.
         */
        private static final String FILE_SUFFIX = ".file";

        /**
         * Suffix for property with the input set.
         */
        private static final String INPUT_SET_SUFFIX = ".set";

        /**
         * Cache with all known cached files.
         */
        private static final Map<File, CachedFileContents<KnownFilesSaveData>> mCache =
                Maps.newHashMap();

        /**
         * File contents cache.
         */
        @NonNull
        private final CachedFileContents<KnownFilesSaveData> mFileContentsCache;

        /**
         * Maps all files in the last build to their input set.
         */
        @NonNull
        private final Map<RelativeFile, InputSet> mFiles;

        /**
         * Has the data been modified?
         */
        private boolean mDirty;

        /**
         * Creates a new file save data and reads it one exists. To create new instances, the
         * factory method {@link #make(File)} should be used.
         *
         * @param cache the cache used
         * @throws IOException failed to read the file (not thrown if the file does not exist)
         */
        private KnownFilesSaveData(@NonNull CachedFileContents<KnownFilesSaveData> cache)
                throws IOException {
            mFileContentsCache = cache;
            mFiles = Maps.newHashMap();
            if (cache.getFile().isFile()) {
                readCurrentData();
            }

            mDirty = false;
        }

        /**
         * Creates a new {@link KnownFilesSaveData}, or obtains one from cache if there already
         * exists a cached entry.
         *
         * @param intermediateDir the intermediate directory where the cache is stored
         * @return the save data
         * @throws IOException save data file exists but there was an error reading it (not thrown
         * if the file does not exist)
         */
        @NonNull
        private static synchronized KnownFilesSaveData make(@NonNull File intermediateDir)
                throws IOException {
            File saveFile = computeSaveFile(intermediateDir);
            CachedFileContents<KnownFilesSaveData> cached = mCache.get(saveFile);
            if (cached == null) {
                cached = new CachedFileContents<>(saveFile);
                mCache.put(saveFile, cached);
            }

            KnownFilesSaveData saveData = cached.getCache();
            if (saveData == null) {
                saveData = new KnownFilesSaveData(cached);
                cached.closed(saveData);
            }

            return saveData;
        }

        /**
         * Computes what is the save file for the provided intermediate directory.
         *
         * @param intermediateDir the intermediate directory
         * @return the file
         */
        private static File computeSaveFile(@NonNull File intermediateDir) {
            return new File(intermediateDir, SAVE_DATA_FILE_NAME);
        }

        /**
         * Reads the save file data into the in-memory data structures.
         *
         * @throws IOException failed to read the file
         */
        private void readCurrentData() throws IOException {
            Closer closer = Closer.create();

            File saveFile = mFileContentsCache.getFile();

            Properties properties = new Properties();
            try {
                Reader saveDataReader = closer.register(new FileReader(saveFile));
                properties.load(saveDataReader);
            } catch (Throwable t) {
                throw closer.rethrow(t);
            } finally {
                closer.close();
            }

            String fileCountText = null;
            int fileCount;
            try {
                fileCountText = properties.getProperty(COUNT_PROPERTY);
                if (fileCountText == null) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + COUNT_PROPERTY + "' has no value).");
                }

                fileCount = Integer.parseInt(fileCountText);
                if (fileCount < 0) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + COUNT_PROPERTY + "' has value " + fileCount + ").");
                }
            } catch (NumberFormatException e) {
                throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                        + "property '" + COUNT_PROPERTY + "' has value '" + fileCountText + "').",
                        e);
            }

            for (int i = 0; i < fileCount; i++) {
                String baseName = properties.getProperty(i + BASE_SUFFIX);
                if (baseName == null) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + i + BASE_SUFFIX + "' has no value).");
                }

                String fileName = properties.getProperty(i + FILE_SUFFIX);
                if (fileName == null) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + i + FILE_SUFFIX + "' has no value).");
                }

                String inputSetName = properties.getProperty(i + INPUT_SET_SUFFIX);
                if (inputSetName == null) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + i + INPUT_SET_SUFFIX + "' has no value).");
                }

                InputSet is;
                try {
                    is = InputSet.valueOf(InputSet.class, inputSetName);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid data stored in file '" + saveFile + "' ("
                            + "property '" + i + INPUT_SET_SUFFIX + "' has invalid value '"
                            + inputSetName + "').");
                }

                mFiles.put(new RelativeFile(new File(baseName), new File(fileName)), is);
            }
        }

        /**
         * Saves current in-memory data structures to file.
         *
         * @throws IOException failed to save the data
         */
        private void saveCurrentData() throws IOException {
            if (!mDirty) {
                return;
            }

            Closer closer = Closer.create();

            Properties properties = new Properties();
            properties.put(COUNT_PROPERTY, Integer.toString(mFiles.size()));
            int idx = 0;
            for (Map.Entry<RelativeFile, InputSet> e : mFiles.entrySet()) {
                RelativeFile rf = e.getKey();

                String basePath = Verify.verifyNotNull(rf.getBase().getPath());
                Verify.verify(!basePath.isEmpty());

                String filePath = Verify.verifyNotNull(rf.getFile().getPath());
                Verify.verify(!filePath.isEmpty());

                properties.put(idx + BASE_SUFFIX, basePath);
                properties.put(idx + FILE_SUFFIX, filePath);
                properties.put(idx + INPUT_SET_SUFFIX, e.getValue().name());

                idx++;
            }

            try {
                Writer saveDataWriter = closer.register(new FileWriter(
                        mFileContentsCache.getFile()));
                properties.store(saveDataWriter, "Internal package file, do not edit.");
                mFileContentsCache.closed(this);
            } catch (Throwable t) {
                throw closer.rethrow(t);
            } finally {
                closer.close();
            }
        }

        /**
         * Obtains all relative files stored in the save data that have the provided input set and
         * whose files are included in the provided set of files. This method allows retrieving
         * the original relative files from the files, while filtering for the desired input set.
         *
         * @param files the files to filter
         * @param inputSet the input set to filter
         * @return all saved relative files that have the given input set and whose files exist
         * in the provided set
         */
        @NonNull
        private ImmutableSet<RelativeFile> find(@NonNull Set<File> files,
                @NonNull InputSet inputSet) {
            Set<RelativeFile> found = Sets.newHashSet();
            for (RelativeFile rf :
                    Maps.filterValues(mFiles, Predicates.equalTo(inputSet)).keySet()) {
                if (files.contains(rf.getFile())) {
                    found.add(rf);
                }
            }

            return ImmutableSet.copyOf(found);
        }

        /**
         * Sets all files in an input set, replacing whatever existed previously.
         *
         * @param files the files
         * @param set the input set
         */
        private void setInputSet(@NonNull Collection<RelativeFile> files, @NonNull InputSet set) {
            for (Iterator<Map.Entry<RelativeFile, InputSet>> it = mFiles.entrySet().iterator();
                    it.hasNext(); ) {
                Map.Entry<RelativeFile, InputSet> next = it.next();
                if (next.getValue() == set && !files.contains(next.getKey())) {
                    it.remove();
                    mDirty = true;
                }
            }

            files.forEach(f -> {
                if (!mFiles.containsKey(f)) {
                    mFiles.put(f, set);
                    mDirty = true;
                }
            });
        }
    }

    /**
     * Input sets for files for save data (see {@link KnownFilesSaveData}).
     */
    private enum InputSet {
        /**
         * File belongs to the dex file set.
         */
        DEX,

        /**
         * File belongs to the java resources file set.
         */
        JAVA_RESOURCE,

        /**
         * File belongs to the native resources file set.
         */
        NATIVE_RESOURCE,

        /**
         * File belongs to the android resources file set.
         */
        ANDROID_RESOURCE
    }

    // ----- ConfigAction -----

    public static class ConfigAction implements TaskConfigAction<PackageAndroidArtifact> {

        private final VariantOutputScope scope;
        private final DexPackagingPolicy dexPackagingPolicy;
        private final boolean instantRunEnabled;

        public ConfigAction(
                @NonNull VariantOutputScope scope,
                @NonNull DexPackagingPolicy dexPackagingPolicy,
                boolean instantRunEnabled) {
            this.scope = scope;
            this.dexPackagingPolicy = dexPackagingPolicy;
            this.instantRunEnabled = instantRunEnabled;
        }

        @NonNull
        @Override
        public String getName() {
            return scope.getTaskName("package");
        }

        @NonNull
        @Override
        public Class<PackageAndroidArtifact> getType() {
            return PackageAndroidArtifact.class;
        }

        @Override
        public void execute(@NonNull final PackageAndroidArtifact packageApp) {
            final VariantScope variantScope = scope.getVariantScope();
            final ApkVariantData variantData = (ApkVariantData) variantScope.getVariantData();
            final ApkVariantOutputData variantOutputData = (ApkVariantOutputData) scope
                    .getVariantOutputData();
            final GradleVariantConfiguration config = variantScope.getVariantConfiguration();

            variantOutputData.packageAndroidArtifactTask = packageApp;
            packageApp.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
            packageApp.setVariantName(
                    variantScope.getVariantConfiguration().getFullName());
            packageApp.setMinSdkVersion(config.getMinSdkVersion());
            packageApp.instantRunContext = variantScope.getInstantRunBuildContext();
            packageApp.dexPackagingPolicy = dexPackagingPolicy;
            packageApp.instantRunSupportDir = variantScope.getInstantRunSupportDir();
            packageApp.setIncrementalFolder(variantScope.getIncrementalDir(packageApp.getName()));

            File cacheByPathDir = new File(packageApp.getIncrementalFolder(), ZIP_DIFF_CACHE_DIR);
            cacheByPathDir.mkdirs();
            packageApp.cacheByPath = new FileCacheByPath(cacheByPathDir);

            if (config.isMinifyEnabled()
                    && config.getBuildType().isShrinkResources()
                    && !instantRunEnabled
                    && !config.getJackOptions().isEnabled()) {
                ConventionMappingHelper.map(packageApp, "resourceFile",
                        (Callable<File>) scope::getCompressedResourceFile);
            } else {
                ConventionMappingHelper.map(packageApp, "resourceFile", new Callable<File>() {
                    @Override
                    public File call() {
                        return variantOutputData.processResourcesTask.getPackageOutputFile();
                    }
                });
            }

            ConventionMappingHelper.map(packageApp, "dexFolders", new Callable<Set<File>>() {
                @Override
                public  Set<File> call() {
                    return variantScope.getTransformManager()
                            .getPipelineOutput(sDexFilter).keySet();
                }
            });

            ConventionMappingHelper.map(packageApp, "javaResourceFiles", new Callable<Set<File>>() {
                @Override
                public Set<File> call() throws Exception {
                    return variantScope.getTransformManager().getPipelineOutput(
                            sResFilter).keySet();
                }
            });

            ConventionMappingHelper.map(packageApp, "jniFolders", new Callable<Set<File>>() {
                @Override
                public Set<File> call() {
                    if (variantData.getSplitHandlingPolicy() ==
                            BaseVariantData.SplitHandlingPolicy.PRE_21_POLICY) {
                        return variantScope.getTransformManager().getPipelineOutput(
                                sNativeLibsFilter).keySet();
                    }

                    Set<String> filters = AbiSplitOptions.getAbiFilters(
                            scope.getGlobalScope().getExtension().getSplits().getAbiFilters());
                    return filters.isEmpty() ? variantScope.getTransformManager().getPipelineOutput(
                            sNativeLibsFilter).keySet() : Collections.<File>emptySet();
                }
            });

            ConventionMappingHelper.map(packageApp, "abiFilters", new Callable<Set<String>>() {
                @Override
                public Set<String> call() throws Exception {
                    if (variantOutputData.getMainOutputFile().getFilter(com.android.build.OutputFile.ABI) != null) {
                        return ImmutableSet.of(
                                variantOutputData.getMainOutputFile()
                                        .getFilter(com.android.build.OutputFile.ABI));
                    }
                    Set<String> supportedAbis = config.getSupportedAbis();
                    if (supportedAbis != null) {
                        return supportedAbis;
                    }

                    return ImmutableSet.of();
                }
            });
            ConventionMappingHelper.map(packageApp, "jniDebugBuild", new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return config.getBuildType().isJniDebuggable();
                }
            });

            CoreSigningConfig sc = (CoreSigningConfig) config.getSigningConfig();
            packageApp.setSigningConfig(sc);
            if (sc != null) {
                String validateSigningTaskName = "validate" + StringHelper.capitalize(sc.getName()) + "Signing";
                ValidateSigningTask validateSigningTask =
                        (ValidateSigningTask) scope.getGlobalScope().getProject().getTasks().findByName(validateSigningTaskName);
                if (validateSigningTask == null) {
                    validateSigningTask =
                            scope.getGlobalScope().getProject().getTasks().create(
                                    "validate" + StringHelper.capitalize(sc.getName()) + "Signing",
                                    ValidateSigningTask.class);
                    validateSigningTask.setAndroidBuilder(scope.getGlobalScope().getAndroidBuilder());
                    validateSigningTask.setVariantName(
                            variantScope.getVariantConfiguration().getFullName());
                    validateSigningTask.setSigningConfig(sc);
                }

                packageApp.dependsOn(validateSigningTask);
            }

            ConventionMappingHelper.map(packageApp, "packagingOptions", new Callable<PackagingOptions>() {
                @Override
                public PackagingOptions call() throws Exception {
                    return scope.getGlobalScope().getExtension().getPackagingOptions();
                }
            });

            ConventionMappingHelper.map(packageApp, "outputFile",
                    (Callable<File>) scope::getPackageApk);

            packageApp.markerFile =
                    PrePackageApplication.ConfigAction.getMarkerFile(variantScope);
            packageApp.inOldMode = AndroidGradleOptions.useOldPackaging(
                    variantScope.getGlobalScope().getProject());
            packageApp.scope = scope.getVariantScope();
        }
    }
}
