/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.deployer;

enum InstallStatus {
    OK,

    // All possible INSTALL_FAIL / INSTALL_PARSE_FAIL from PackageManager.java
    INSTALL_FAILED_ABORTED,
    INSTALL_FAILED_ALREADY_EXISTS,
    INSTALL_FAILED_BAD_DEX_METADATA,
    INSTALL_FAILED_BAD_SIGNATURE,
    INSTALL_FAILED_CONFLICTING_PROVIDER,
    INSTALL_FAILED_CONTAINER_ERROR,
    INSTALL_FAILED_CPU_ABI_INCOMPATIBLE,
    INSTALL_FAILED_DEXOPT,
    INSTALL_FAILED_DUPLICATE_PACKAGE,
    INSTALL_FAILED_DUPLICATE_PERMISSION,
    INSTALL_FAILED_INSTANT_APP_INVALID,
    INSTALL_FAILED_INSUFFICIENT_STORAGE,
    INSTALL_FAILED_INTERNAL_ERROR,
    INSTALL_FAILED_INVALID_APK,
    INSTALL_FAILED_INVALID_INSTALL_LOCATION,
    INSTALL_FAILED_INVALID_URI,
    INSTALL_FAILED_MEDIA_UNAVAILABLE,
    INSTALL_FAILED_MISSING_FEATURE,
    INSTALL_FAILED_MISSING_SHARED_LIBRARY,
    INSTALL_FAILED_MISSING_SPLIT,
    INSTALL_FAILED_MULTIPACKAGE_INCONSISTENCY,
    INSTALL_FAILED_NEWER_SDK,
    INSTALL_FAILED_NO_MATCHING_ABIS,
    INSTALL_FAILED_NO_SHARED_USER,
    INSTALL_FAILED_OLDER_SDK,
    INSTALL_FAILED_OTHER_STAGED_SESSION_IN_PROGRESS,
    INSTALL_FAILED_PACKAGE_CHANGED,
    INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE,
    INSTALL_FAILED_REPLACE_COULDNT_DELETE,
    INSTALL_FAILED_SANDBOX_VERSION_DOWNGRADE,
    INSTALL_FAILED_SHARED_USER_INCOMPATIBLE,
    INSTALL_FAILED_TEST_ONLY,
    INSTALL_FAILED_UID_CHANGED,
    INSTALL_FAILED_UPDATE_INCOMPATIBLE,
    INSTALL_FAILED_USER_RESTRICTED,
    INSTALL_FAILED_VERIFICATION_FAILURE,
    INSTALL_FAILED_VERIFICATION_TIMEOUT,
    INSTALL_FAILED_VERSION_DOWNGRADE,
    INSTALL_FAILED_WRONG_INSTALLED_VERSION,
    INSTALL_PARSE_FAILED_BAD_MANIFEST,
    INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME,
    INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID,
    INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING,
    INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES,
    INSTALL_PARSE_FAILED_MANIFEST_EMPTY,
    INSTALL_PARSE_FAILED_MANIFEST_MALFORMED,
    INSTALL_PARSE_FAILED_NO_CERTIFICATES,
    INSTALL_PARSE_FAILED_NOT_APK,
    INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION,

    DEVICE_NOT_RESPONDING,
    INCONSISTENT_CERTIFICATES,
    NO_CERTIFICATE,
    DEVICE_NOT_FOUND,
    SHELL_UNRESPONSIVE,
    MULTI_APKS_NO_SUPPORTED_BELOW21,
    UNKNOWN_ERROR,
    SKIPPED_INSTALL, // no changes.
    ;
}
