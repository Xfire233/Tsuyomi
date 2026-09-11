// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    id("tsuyomi.android.application")
    id("tsuyomi.android.compose")
}

val repositoryKeyId = providers.gradleProperty("tsuyomi.repository.keyId").orElse("").get()
val repositoryPublicKey = providers.gradleProperty("tsuyomi.repository.publicKey").orElse("").get()
require(repositoryKeyId.isEmpty() == repositoryPublicKey.isEmpty()) {
    "Official repository keyId and publicKey must be configured together"
}
require(repositoryKeyId.isEmpty() || Regex("[A-Za-z0-9._-]{8,128}").matches(repositoryKeyId))
require(repositoryPublicKey.isEmpty() || Regex("[A-Za-z0-9+/]{43}=").matches(repositoryPublicKey))
require(
    repositoryPublicKey.isEmpty() || !Base64.getDecoder().decode(repositoryPublicKey).contentEquals(
        Base64.getDecoder().decode("ebVWLo/mVPlAeLES6KmLp5AfhTrmlb7X4OORC60ElmQ="),
    ),
) { "The public deterministic fixture key cannot be an official repository root" }

android {
    namespace = "org.tsuyomi.android"
    defaultConfig {
        applicationId = "org.tsuyomi.android"
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunnerArguments["keep_p4c_review_state"] =
            providers.gradleProperty("tsuyomi.keepP4cReviewState").orElse("false").get()
        buildConfigField("String", "OFFICIAL_REPOSITORY_KEY_ID", "\"$repositoryKeyId\"")
        buildConfigField("String", "OFFICIAL_REPOSITORY_PUBLIC_KEY", "\"$repositoryPublicKey\"")
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".fixture"
            versionNameSuffix = "-fixture"
        }
        create("online") {
            initWith(getByName("release"))
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    buildFeatures {
        buildConfig = true
    }
    sourceSets.getByName("debug").assets.directories += "../source/extension-testkit/fixtures/wenku8"
    testOptions {
        managedDevices {
            localDevices {
                create("tsuyomiPixel6Api29") {
                    device = "Pixel 6"
                    apiLevel = 29
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

dependencies {
    implementation(project(":core:display"))
    implementation(project(":core:database"))
    implementation(project(":core:library"))
    implementation(project(":core:ui"))
    implementation(project(":core:preferences"))
    implementation(project(":core:network"))
    implementation(project(":core:media"))
    implementation(project(":core:security"))
    implementation(project(":core:webview"))
    implementation(project(":feature:library"))
    implementation(project(":feature:browse"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:search"))
    implementation(project(":feature:book"))
    implementation(project(":feature:reader"))
    implementation(project(":reader:engine"))
    implementation(project(":shared:backup"))
    implementation(project(":shared:smart-shelf"))
    implementation(project(":feature:backup"))
    implementation(project(":shared:library-domain"))
    implementation(project(":shared:source-contract"))
    implementation(project(":core:files"))
    implementation(project(":source:extension-manager"))
    debugImplementation(project(":source:extension-testkit"))
    "onlineImplementation"(project(":source:extension-testkit"))
    implementation(project(":shared:locator"))
    implementation(project(":shared:model"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.bouncycastle.provider)
    androidTestImplementation(libs.json.canonicalization)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
}
