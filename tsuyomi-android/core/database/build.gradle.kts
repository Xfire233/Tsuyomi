// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
    alias(libs.plugins.ksp)
    id("tsuyomi.android.room")
}

android {
    namespace = "org.tsuyomi.core.database"
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
}

ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

dependencies {
    implementation(project(":shared:locator"))
    implementation(project(":shared:model"))
    implementation(project(":shared:backup"))
    implementation(project(":shared:smart-shelf"))
    implementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.androidx.room.testing)
}
