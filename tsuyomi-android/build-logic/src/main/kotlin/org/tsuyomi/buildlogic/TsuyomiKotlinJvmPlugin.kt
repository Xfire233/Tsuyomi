// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class TsuyomiKotlinJvmPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure(KotlinJvmProjectExtension::class.java) {
            jvmToolchain(17)
        }
        tasks.withType(KotlinCompile::class.java).configureEach {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        }
        tasks.withType(Test::class.java).configureEach {
            useJUnit()
        }
        dependencies.add("testImplementation", "org.jetbrains.kotlin:kotlin-test:2.3.0")
        dependencies.add("testImplementation", "junit:junit:4.13.2")
    }
        }
}
