// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
    id("tsuyomi.android.compose")
}

android {
    namespace = "org.tsuyomi.reader.ui"
}

dependencies {
    implementation(project(":core:display"))
    implementation(project(":core:ui"))
    implementation(project(":reader:engine"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
}
