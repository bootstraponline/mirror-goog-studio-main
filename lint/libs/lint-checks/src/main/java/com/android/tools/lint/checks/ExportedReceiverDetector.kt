package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_EXPORTED
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_PROVIDER
import com.android.SdkConstants.TAG_RECEIVER
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.subtag
import org.w3c.dom.Element

class ExportedReceiverDetector : Detector(), XmlScanner {
    override fun getApplicableElements() =
        listOf(TAG_ACTIVITY, TAG_ACTIVITY_ALIAS, TAG_SERVICE, TAG_RECEIVER, TAG_PROVIDER)

    override fun visitElement(context: XmlContext, element: Element) {
        val intentFilter = element.subtag(TAG_INTENT_FILTER)
        val exported = element.getAttributeNodeNS(ANDROID_URI, ATTR_EXPORTED)
        if (intentFilter != null && exported == null) {
            val fix = LintFix.create().set().todo(ANDROID_URI, ATTR_EXPORTED).build()
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "When using intent filters, please specify `android:exported` as well",
                fix
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "IntentFilterExportedReceiver",
            briefDescription = "Unspecified `android:exported` in manifest",
            explanation = """
                Apps targeting Android 12 and higher are required to specify an explicit value \
                for `android:exported` when the corresponding component has an intent filter defined. \
                Otherwise, installation will fail.

                Previously, `android:exported` for components without any intent filters present \
                used to default to `false`, and when intent filters were present, the default was \
                `true`. Defaults which change value based on other values are confusing and lead to \
                apps accidentally exporting components as a side-effect of adding intent filters. \
                This is a security risk, and we have made this change to avoid introducing \
                accidental vulnerabilities.

                While the default without intent filters remains unchanged, it is now required to \
                explicitly specify a value when intent filters are present. Any app failing to meet \
                this requirement will fail to install on any Android version after Android 11.

                We recommend setting `android:exported` to false (even on previous versions of Android \
                prior to this requirement) unless you have a good reason to export a particular \
                component.
            """,
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ExportedReceiverDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}
