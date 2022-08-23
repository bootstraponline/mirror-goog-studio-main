/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.app_package

fun samplePassJava(
    packageName: String
): String {
  return """
package $packageName;

import android.app.Activity;

import java.util.Date;
import java.util.Random;

/**
 * A helper class that allows us to easily configure the JSON we will be sending to
 * {@link com.google.android.gms.pay.PayClient#savePasses(String, Activity, int)}.
 *
 * If you are using the <a href="https://wallet-lab-tools.web.app/issuers">temporary issuer</a> tool,
 * then you can use this class with the parameters created on the linked website.
 *
 * If you are seeking to implement your own custom pass, you will need to define your own class for
 * each specific pass class that you create.
 */
public class SamplePass {
    private final String issuerEmail;
    private final String issuerId;
    private final String passClass;
    private final String passId;

    public SamplePass(String issuerEmail, String issuerId, String passClass, String passId) {
        this.issuerEmail = issuerEmail;
        this.issuerId = issuerId;
        this.passClass = passClass;
        this.passId = passId;
    }

    public final String toJson() {
        final Random random = new Random();
        return "\n    {\n      \"iss\": \""
                + this.issuerEmail
                + "\",\n      \"aud\": \"google\",\n      \"typ\": \"savetowallet\",\n      \"iat\": "
                + (new Date()).getTime() / 1000L
                + ",\n      \"origins\": [],\n      \"payload\": {\n        \"genericObjects\": [\n" +
                "          {\n            \"id\": \""
                + this.issuerId
                + '.'
                + this.passId
                + "\",\n            \"classId\": \""
                + this.passClass
                + "\",\n            \"genericType\": \"GENERIC_TYPE_UNSPECIFIED\",\n            \"hexBackgroundColor\": \"#4285f4\",\n            \"logo\": {\n              \"sourceUri\": {\n                \"uri\": \"https://storage.googleapis.com/wallet-lab-tools-codelab-artifacts-public/pass_google_logo.jpg\"\n              }\n            },\n            \"cardTitle\": {\n              \"defaultValue\": {\n                \"language\": \"en\",\n                \"value\": \"Google I/O '22  [DEMO ONLY]\"\n              }\n            },\n            \"subheader\": {\n              \"defaultValue\": {\n                \"language\": \"en\",\n                \"value\": \"Attendee\"\n              }\n            },\n            \"header\": {\n              \"defaultValue\": {\n                \"language\": \"en\",\n                \"value\": \"Nicholas Corder\"\n              }\n            },\n            \"barcode\": {\n              \"type\": \"QR_CODE\",\n              \"value\": \""
                + this.passId
                + "\"\n            },\n            \"heroImage\": {\n              \"sourceUri\": {\n                \"uri\": \"https://storage.googleapis.com/wallet-lab-tools-codelab-artifacts-public/google-io-hero-demo-only.jpg\"\n              }\n            },\n            \"textModulesData\": [\n              {\n                \"header\": \"POINTS\",\n                \"body\": \""
                + random.nextInt(9999)
                + "\",\n                \"id\": \"points\"\n              },\n              {\n                \"header\": \"CONTACTS\",\n                \"body\": \""
                + random.nextInt(99)
                + "\",\n                \"id\": \"contacts\"\n              }\n            ]\n          }\n        ]\n      }\n    }\n    ";

    }
}
"""
}
