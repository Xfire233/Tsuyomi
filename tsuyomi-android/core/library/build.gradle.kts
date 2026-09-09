// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
}

android {
    namespace = "org.tsuyomi.core.library"
}

dependencies {
    api(project(":shared:library-domain"))
    implementation(project(":shared:model"))
    implementation(project(":shared:source-contract"))
    implementation(libs.kotlinx.coroutines.android)
}
