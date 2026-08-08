// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.android.compose.screenshot") version libs.versions.screenshot.get() apply false
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
