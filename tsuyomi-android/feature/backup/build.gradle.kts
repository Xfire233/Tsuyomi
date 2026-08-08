// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
    id("tsuyomi.android.compose")
}

android {
    namespace = "org.tsuyomi.feature.backup"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:files"))
    implementation(project(":core:ui"))
    implementation(project(":shared:backup"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
