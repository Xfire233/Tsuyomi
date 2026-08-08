// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
}

android {
    namespace = "org.tsuyomi.source.quickjsruntime"
}

dependencies {
    implementation(project(":shared:source-contract"))
}
