// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    `kotlin-dsl`
}

group = "org.tsuyomi.buildlogic"
version = "1.0"

dependencyLocking {
    lockAllConfigurations()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "tsuyomi.android.application"
            implementationClass = "org.tsuyomi.buildlogic.TsuyomiAndroidApplicationPlugin"
        }
        register("androidLibrary") {
            id = "tsuyomi.android.library"
            implementationClass = "org.tsuyomi.buildlogic.TsuyomiAndroidLibraryPlugin"
        }
        register("androidCompose") {
            id = "tsuyomi.android.compose"
            implementationClass = "org.tsuyomi.buildlogic.TsuyomiAndroidComposePlugin"
        }
        register("androidRoom") {
            id = "tsuyomi.android.room"
            implementationClass = "org.tsuyomi.buildlogic.TsuyomiAndroidRoomPlugin"
        }
        register("kotlinJvm") {
            id = "tsuyomi.kotlin.jvm"
            implementationClass = "org.tsuyomi.buildlogic.TsuyomiKotlinJvmPlugin"
        }
    }
}
