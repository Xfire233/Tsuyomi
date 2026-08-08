// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
    alias(libs.plugins.ksp)
    id("tsuyomi.android.room")
}

android {
    namespace = "org.tsuyomi.core.database"
}

ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

dependencies {
    implementation(project(":shared:locator"))
    implementation(project(":shared:model"))
    androidTestImplementation(libs.androidx.room.testing)
}
