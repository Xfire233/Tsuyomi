// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("tsuyomi.android.application")
    id("tsuyomi.android.compose")
}

android {
    namespace = "org.tsuyomi.android"
    defaultConfig {
        applicationId = "org.tsuyomi.android"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:display"))
    implementation(project(":core:ui"))
    implementation(project(":core:preferences"))
    implementation(project(":feature:library"))
    implementation(project(":feature:browse"))
    implementation(project(":feature:settings"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
