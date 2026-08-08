// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class TsuyomiAndroidComposePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
        extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true

        val catalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
        val composeBom = catalog.findLibrary("androidx-compose-bom").get().get()
        dependencies.add("implementation", dependencies.platform(composeBom))
    }
        }
}
