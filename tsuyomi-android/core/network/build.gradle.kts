// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
}

android {
    namespace = "org.tsuyomi.core.network"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core:files"))
    implementation(project(":shared:source-contract"))
    testImplementation(libs.junit)
}
