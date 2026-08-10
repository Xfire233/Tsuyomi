// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
    id("tsuyomi.android.compose")
}

android {
    namespace = "org.tsuyomi.feature.reader"
}

dependencies {
    implementation(project(":core:display"))
    implementation(project(":core:ui"))
    implementation(project(":reader:ui"))
    implementation(project(":shared:locator"))
    implementation(project(":shared:backup"))
    implementation(project(":shared:source-contract"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
