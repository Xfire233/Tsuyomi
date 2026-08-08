// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class TsuyomiAndroidRoomPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {


        val catalog = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
        dependencies.add("implementation", catalog.findLibrary("androidx-room-runtime").get().get())
        dependencies.add("implementation", catalog.findLibrary("androidx-room-ktx").get().get())
        dependencies.add("ksp", catalog.findLibrary("androidx-room-compiler").get().get())
        }
    }
}
