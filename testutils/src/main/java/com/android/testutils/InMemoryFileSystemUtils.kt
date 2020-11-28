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

package com.android.testutils

import com.android.SdkConstants
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.io.File
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import kotlin.streams.toList

fun createFileSystem(): FileSystem {
    var config = Configuration.forCurrentPlatform()
    val root = if (OsType.getHostOs() == OsType.WINDOWS) "c:\\" else "/"
    config = config.toBuilder()
        .setRoots(root)
        .setWorkingDirectory(root)
        .setAttributeViews("posix")
        .build()
    return Jimfs.newFileSystem(config)
}

fun canWrite(path: Path): Boolean {
    return try {
        !Sets.intersection(
            Files.getPosixFilePermissions(path),
            ImmutableSet.of(
                PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.OWNER_WRITE
            )
        )
            .isEmpty()
    } catch (e: IOException) {
        false
    }
}

fun getPlatformSpecificPath(path: String): String {
    return if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS
        && (path.startsWith("/") || path.startsWith("\\"))) {
        (File(path).absolutePath
                + if (path.length > 1 && (path.endsWith("/") || path.endsWith("\\"))) File.separator else "")
    } else path
}

/**
 * Records a new absolute file path.
 * Parent folders are automatically created.
 */
fun recordExistingFile(
    path: Path, lastModified: Long = 0, inputStream: ByteArray? = null) {
    try {
        Files.createDirectories(path.parent)
        Files.write(
            path,
            inputStream ?: ByteArray(0)
        )
        Files.setLastModifiedTime(path, FileTime.fromMillis(lastModified))
    } catch (e: IOException) {
        assert(false) { e.message!! }
    }
}

/**
 * Returns the list of paths added using [.recordExistingFile]
 * and eventually updated by [.delete] operations.
 *
 * The returned list is sorted by alphabetic absolute path string.
 */
fun getExistingFiles(fileSystem: FileSystem): Array<String> {
    return fileSystem.rootDirectories
        .flatMap { Files.walk(it).use { it.toList() } }
        .filter { Files.isRegularFile(it) }
        .map { it.toString() }
        .sorted()
        .toTypedArray()
}

/**
 * Returns the list of folder paths added using {@link #recordExistingFolder(String)}
 * and eventually updated {@link #delete(File)} or {@link #mkdirs(File)} operations.
 * <p>
 * The returned list is sorted by alphabetic absolute path string.
 */
fun getExistingFolders(fileSystem: FileSystem): Array<String> {
    return fileSystem.rootDirectories
        .flatMap { Files.walk(it).use { it.toList() } }
        .filter { Files.isDirectory(it) }
        .map { it.toString() }
        .sorted()
        .toTypedArray()
}
