// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
    id("tsuyomi.android.compose")
    id("com.android.compose.screenshot")
}

android {
    namespace = "org.tsuyomi.feature.library"
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:ui"))
    implementation(project(":core:media"))
    implementation(project(":shared:model"))
    implementation(project(":shared:locator"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
