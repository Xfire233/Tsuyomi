// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
}

android {
    namespace = "org.tsuyomi.source.extensionmanager"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:files"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    api(project(":shared:source-contract"))
    implementation(project(":shared:model"))
    implementation(project(":source:quickjs-runtime"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle.provider)
    implementation(libs.json.canonicalization)
    implementation(libs.commons.compress)
    testImplementation(libs.junit)
}
