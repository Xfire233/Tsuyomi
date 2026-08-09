// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("tsuyomi.android.library")
}

android {
    namespace = "org.tsuyomi.source.quickjsruntime"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20", "-fno-exceptions", "-fno-rtti")
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(project(":shared:source-contract"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
