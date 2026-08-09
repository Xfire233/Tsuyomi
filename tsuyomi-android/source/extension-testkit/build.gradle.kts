// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
}

android {
    namespace = "org.tsuyomi.source.extensiontestkit"
}

dependencies {
    implementation(project(":shared:source-contract"))
    api(project(":source:extension-manager"))
    testImplementation(libs.junit)
}
