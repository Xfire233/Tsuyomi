// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tsuyomi-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":shared:model")
include(":shared:locator")
include(":shared:backup")
include(":shared:smart-shelf")
include(":shared:source-contract")

include(":core:ui")
include(":core:display")
include(":core:database")
include(":core:preferences")
include(":core:network")
include(":core:files")
include(":core:security")
include(":core:webview")

include(":source:quickjs-runtime")
include(":source:extension-manager")
include(":source:extension-testkit")

include(":reader:engine")
include(":reader:ui")
include(":reader:tts")

include(":feature:library")
include(":feature:browse")
include(":feature:search")
include(":feature:book")
include(":feature:reader")
include(":feature:settings")
include(":feature:backup")
include(":feature:extensions")
