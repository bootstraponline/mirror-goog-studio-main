/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.errors

import com.android.build.gradle.options.Option
import com.android.build.gradle.options.Version

/**
 * Reporter for issues during evaluation.
 *
 *
 * This handles dealing with errors differently if the project is being run from the command line
 * or from the IDE, in particular during Sync when we don't want to throw any exception
 */
interface DeprecationReporter {

    /** Enum for deprecated element removal target.  */
    enum class DeprecationTarget constructor(

        // Mark these properties as private to prevent external usages from constructing
        // inconsistent messages from these values. They should use methods like
        // getDeprecationTargetMessage() instead.
        private val removalTarget: Version,

        /**
         * Additional message to be shown below the pre-formatted error/warning message.
         *
         * Note that this additional message should be constructed such that it fits well in the
         * overall message:
         *
         *     "This feature will be removed in version X.Y of the Android Gradle plugin.\n
         *     $additionalMessage"
         *
         * For example, avoid writing additional messages that say "This feature is planned for
         * removal", as it will be duplicated.
         */
        private val additionalMessage: String? = null
    ) {

        FUTURE_VERSION(Version.FUTURE_VERSION),

        VERSION_5_0(Version.VERSION_5_0),

        // deprecation of compile in favor of api/implementation
        CONFIG_NAME(
            Version.FUTURE_VERSION,
            "For more information, see http://d.android.com/r/tools/update-dependency-configurations.html."
        ),

        // deprecation due to the move to the new DSL.
        OLD_DSL(Version.FUTURE_VERSION),

        // Obsolete Dex Options
        DEX_OPTIONS(Version.FUTURE_VERSION),

        // When legacy dexer will be removed and fully replaced by D8.
        LEGACY_DEXER(
            Version.FUTURE_VERSION,
            "For more details, see https://d.android.com/r/studio-ui/d8-overview.html"
        ),

        // Deprecation of disabling Desugar
        DESUGAR_TOOL(Version.FUTURE_VERSION),

        // Deprecation of Task Access in the variant API
        TASK_ACCESS_VIA_VARIANT(Version.FUTURE_VERSION, "Estimated removal time: end of 2019."),

        DSL_USE_PROGUARD(
            Version.FUTURE_VERSION,
            "Use 'android.enableR8' in gradle.properties to switch between R8 and Proguard."
        ),

        ENABLE_R8(Version.FUTURE_VERSION, "You will no longer be able to disable R8"),

        USE_PROPERTIES(
            Version.FUTURE_VERSION,
            "Gradle Properties must be used to change Variant information."
        ),

        INCLUDE_COMPILE_CLASSPATH(
            Version.FUTURE_VERSION,
            "It does not do anything and AGP no longer includes annotation processors added on your project's compile classpath"
        ),

        ;

        fun getDeprecationTargetMessage(): String {
            return removalTarget.getDeprecationTargetMessage() +
                    (additionalMessage?.let { "\n$it" } ?: "")
        }
    }

    /**
     * Reports a deprecation usage in the DSL/API.
     *
     * @param newDslElement the DSL element to use instead, with the name of the class owning it
     * @param oldDslElement the name of the deprecated element, with the name of the class
     * owning it.
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportDeprecatedUsage(
            newDslElement: String,
            oldDslElement: String,
            deprecationTarget: DeprecationTarget)

    /**
     * Reports a deprecation usage in the DSL/API.
     *
     * @param newApiElement the DSL element to use instead, with the name of the class owning it
     * @param oldApiElement the name of the deprecated element, with the name of the class
     * owning it.
     * @param url URL to documentation about the deprecation
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportDeprecatedApi(
        newApiElement: String,
        oldApiElement: String,
        url: String,
        deprecationTarget: DeprecationTarget)

    /**
     * Reports a deprecated value usage for a DSL element in the DSL/API.
     *
     * @param dslElement name of DSL element containing the deprecated value, with the name of the
     * class.
     * @param oldValue value of the DSL element which has been deprecated.
     * @param newValue optional new value replacing the deprecated value.
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportDeprecatedValue(
            dslElement: String,
            oldValue: String,
            newValue: String?,
            deprecationTarget: DeprecationTarget)

    /**
     * Reports a deprecation usage in the DSL/API.
     *
     * @param oldDslElement the name of the deprecated element, with the name of the class
     * owning it.
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportObsoleteUsage(
            oldDslElement: String,
            deprecationTarget: DeprecationTarget)

    /**
     * Reports a renamed Configuration.
     *
     * @param newConfiguration the name of the [org.gradle.api.artifacts.Configuration] to use
     * instead
     * @param oldConfiguration the name of the deprecated [org.gradle.api.artifacts.Configuration]
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportRenamedConfiguration(
            newConfiguration: String,
            oldConfiguration: String,
            deprecationTarget: DeprecationTarget)

    /**
     * Reports a deprecated Configuration, that gets replaced by an optional DSL element
     *
     * @param newDslElement the name of the DSL element that replaces the configuration
     * @param oldConfiguration the name of the deprecated [org.gradle.api.artifacts.Configuration]
     * @param deprecationTarget when the deprecated element is going to be removed. A line about the
     * timing is added to the message.
     */
    fun reportDeprecatedConfiguration(
        newDslElement: String,
        oldConfiguration: String,
        deprecationTarget: DeprecationTarget)

    /**
     * Reports issues with the given option if there are any.
     *
     * @param option the option to report issues for
     * @param value the value of the option
     */
    fun reportOptionIssuesIfAny(option: Option<*>, value: Any)

}
