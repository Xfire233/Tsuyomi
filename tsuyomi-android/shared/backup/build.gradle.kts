// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.kotlin.jvm")
}

dependencies {
    implementation(project(":shared:locator"))
    implementation(project(":shared:model"))
}
