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
        versionCode = 2
        versionName = "0.2.0"
    }
    buildFeatures {
        buildConfig = true
    }
    sourceSets.getByName("debug").assets.srcDir("../../tsuyomi-extensions/fixtures/wenku8")
}

dependencies {
    implementation(project(":core:display"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))
    implementation(project(":core:preferences"))
    implementation(project(":core:network"))
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
    implementation(project(":shared:source-contract"))
    implementation(project(":core:files"))
    implementation(project(":source:extension-manager"))
    debugImplementation(project(":source:extension-testkit"))
    implementation(project(":shared:locator"))
    implementation(project(":shared:model"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
}
