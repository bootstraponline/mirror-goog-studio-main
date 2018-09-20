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

import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.Option
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.EvalIssueReporter
import com.android.builder.errors.EvalIssueReporter.Severity
import com.android.builder.errors.EvalIssueReporter.Type

class DeprecationReporterImpl(
        private val issueReporter: EvalIssueReporter,
        private val projectOptions: ProjectOptions,
        private val projectPath: String) : DeprecationReporter {

    private val suppressedOptionWarnings: Set<String> =
        projectOptions[StringOption.SUPPRESS_UNSUPPORTED_OPTION_WARNINGS]?.splitToSequence(',')?.toSet()
                ?: setOf()

    override fun reportDeprecatedUsage(
            newDslElement: String,
            oldDslElement: String,
            deprecationTarget: DeprecationTarget) {
        issueReporter.reportIssue(
                Type.DEPRECATED_DSL,
                Severity.WARNING,
                "DSL element '$oldDslElement' is obsolete and has been replaced with '$newDslElement'.\n" +
                        "It will be removed ${deprecationTarget.removalTime}",
                "$oldDslElement::$newDslElement::${deprecationTarget.name}")
    }

    override fun reportDeprecatedUsage(
            newDslElement: String,
            oldDslElement: String,
            url: String,
            deprecationTarget: DeprecationTarget) {
        issueReporter.reportIssue(
                Type.DEPRECATED_DSL,
                Severity.WARNING,
                "DSL element '$oldDslElement' is obsolete and has been replaced with '$newDslElement'.\n" +
                        "It will be removed ${deprecationTarget.removalTime}\n" +
                        "For more information, see $url",
                "$oldDslElement::$newDslElement::${deprecationTarget.name}")
    }

    override fun reportDeprecatedApi(
        newApiElement: String,
        oldApiElement: String,
        url: String,
        deprecationTarget: DeprecationTarget
    ) {
        if (!checkAndSet(oldApiElement)) {
            val severity = if (projectOptions.get(BooleanOption.IDE_INVOKED_FROM_IDE)
                && projectOptions.get(BooleanOption.DEBUG_OBSOLETE_API)
            )
                Severity.ERROR
            else
                Severity.WARNING

            issueReporter.reportIssue(
                Type.DEPRECATED_DSL,
                severity,
                "API '$oldApiElement' is obsolete and has been replaced with '$newApiElement'.\n" +
                        "It will be removed ${deprecationTarget.removalTime}\n" +
                        "For more information, see $url\n\n" +
                        "To determine what is calling $oldApiElement, use -P${BooleanOption.DEBUG_OBSOLETE_API.propertyName}=true on the command line to display a stack trace"
            )
        }
    }

    override fun reportObsoleteUsage(oldDslElement: String,
            deprecationTarget: DeprecationTarget) {
        issueReporter.reportIssue(
                Type.DEPRECATED_DSL,
                Severity.WARNING,
                "DSL element '$oldDslElement' is obsolete and will be removed ${deprecationTarget.removalTime}",
                "$oldDslElement::::${deprecationTarget.name}")
    }

    override fun reportObsoleteUsage(
            oldDslElement: String,
            url: String,
            deprecationTarget: DeprecationTarget) {
        issueReporter.reportIssue(
                Type.DEPRECATED_DSL,
                Severity.WARNING,
                "DSL element '$oldDslElement' is obsolete and will be removed ${deprecationTarget.removalTime}\n" +
                        "For more information, see $url",
                "$oldDslElement::::${deprecationTarget.name}")
    }

    override fun reportRenamedConfiguration(
            newConfiguration: String,
            oldConfiguration: String,
            deprecationTarget: DeprecationTarget,
            url: String?) {
        val msg =
            "Configuration '$oldConfiguration' is obsolete and has been replaced with '$newConfiguration'.\n" +
                    "It will be removed ${deprecationTarget.removalTime}"

        issueReporter.reportIssue(
                Type.DEPRECATED_CONFIGURATION,
                Severity.WARNING,
                if (url != null) "$msg For more information see: $url" else msg,
                "$oldConfiguration::$newConfiguration::${deprecationTarget.name}")
    }

    override fun reportDeprecatedConfiguration(
        newDslElement: String,
        oldConfiguration: String,
        deprecationTarget: DeprecationTarget
    ) {
        issueReporter.reportIssue(
            Type.DEPRECATED_CONFIGURATION,
            Severity.WARNING,
            "Configuration '$oldConfiguration' is obsolete and has been replaced with DSL element '$newDslElement'.\n" +
                    "It will be removed ${deprecationTarget.removalTime}",
            "$oldConfiguration::$newDslElement::${deprecationTarget.name}")
    }

    override fun reportDeprecatedValue(dslElement: String,
            oldValue: String,
            newValue: String?,
            url: String?,
            deprecationTarget: DeprecationReporter.DeprecationTarget) {
        issueReporter.reportIssue(Type.DEPRECATED_DSL_VALUE,
                Severity.WARNING,
                "DSL element '$dslElement' has a value '$oldValue' which is obsolete " +
                        if (newValue != null)
                            "and has been replaced with '$newValue'.\n"
                        else
                            "and has not been replaced.\n" +
                        "It will be removed ${deprecationTarget.removalTime}\n",
                url)
    }

    override fun reportDeprecatedOption(
            option: String,
            value: String?,
            deprecationTarget: DeprecationTarget) {
        if (suppressedOptionWarnings.contains(option)) {
            return
        }
        issueReporter.reportIssue(
                Type.UNSUPPORTED_PROJECT_OPTION_USE,
                Severity.WARNING,
                "The option '$option' is deprecated and should not be used anymore.\n" +
                        (if (value !=null) "Use '$option=$value' to remove this warning.\n" else "") +
                        "It will be removed ${deprecationTarget.removalTime}.")
    }


    override fun reportExperimentalOption(option: Option<*>, value: String) {
        if (suppressedOptionWarnings.contains(option.propertyName)) {
            return
        }
        issueReporter.reportIssue(
            Type.UNSUPPORTED_PROJECT_OPTION_USE,
            Severity.WARNING,
            "The option setting '${option.propertyName}=$value' is experimental and unsupported.\n" +
                    (if (option.defaultValue != null)"The current default is '${option.defaultValue.toString()}'\n" else "") +
                    option.additionalInfo,
            option.propertyName)
    }

    companion object {
        /**
         * Set of obsolete APIs that have been warned already.
         */
        private val obsoleteApis = mutableSetOf<String>()

        /**
         * Checks if the given API is part of the set already and adds it if not.
         *
         * @return true if the api is already part of the set.
         */
        fun checkAndSet(api: String): Boolean = synchronized(obsoleteApis) {
            return if (obsoleteApis.contains(api)) {
                true
            } else {
                obsoleteApis.add(api)
                false
            }
        }

        fun clean() {
            synchronized(obsoleteApis) {
                obsoleteApis.clear()
            }
        }
    }
}
