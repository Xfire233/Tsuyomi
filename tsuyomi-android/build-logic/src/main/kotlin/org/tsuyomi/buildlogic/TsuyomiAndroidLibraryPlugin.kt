// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class TsuyomiAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
        TsuyomiArchitectureVerification.install(this)
        pluginManager.apply("com.android.library")

        extensions.configure(LibraryExtension::class.java) {
            compileSdk = 37

            defaultConfig {
                minSdk = 29
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                }
            }
            lint {
                warningsAsErrors = true
                abortOnError = true
                checkDependencies = true
            }
        }

        tasks.withType(Test::class.java).configureEach {
            useJUnit()
        }
        dependencies.add("testImplementation", "junit:junit:4.13.2")
        dependencies.add("androidTestImplementation", "androidx.test:runner:1.7.0")
        dependencies.add("androidTestImplementation", "androidx.test.ext:junit:1.3.0")
        dependencies.add("androidTestImplementation", "androidx.test.espresso:espresso-core:3.7.0")
    }
        }
}
