// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class TsuyomiAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure(LibraryExtension::class.java) {
            compileSdk = 36

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

        tasks.withType(KotlinCompile::class.java).configureEach {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
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
