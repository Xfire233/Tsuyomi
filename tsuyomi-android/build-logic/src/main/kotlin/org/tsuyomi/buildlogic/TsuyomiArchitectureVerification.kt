// SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
// SPDX-License-Identifier: Apache-2.0

package org.tsuyomi.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

internal object TsuyomiArchitectureVerification {
    private const val taskName = "verifyTsuyomiArchitecture"
    private const val installedMarker = "org.tsuyomi.architectureVerificationInstalled"

    fun install(project: Project) {
        val root = project.rootProject
        val task = if (root.extensions.extraProperties.has(installedMarker)) {
            root.tasks.named(taskName, VerifyTsuyomiArchitectureTask::class.java)
        } else {
            root.extensions.extraProperties.set(installedMarker, true)
            root.tasks.register(taskName, VerifyTsuyomiArchitectureTask::class.java) {
                rootDirectory.set(root.layout.projectDirectory)
                kotlinSources.from(
                    root.fileTree(root.rootDir) {
                        include(
                            "app/src/**/*.kt",
                            "core/*/src/**/*.kt",
                            "feature/*/src/**/*.kt",
                            "reader/*/src/**/*.kt",
                            "shared/*/src/**/*.kt",
                            "source/*/src/**/*.kt",
                        )
                    },
                )
            }.also { verifier ->
                root.gradle.projectsEvaluated {
                    verifier.configure {
                        declaredProjectEdges.set(collectProjectEdges(root))
                    }
                }
            }
        }

        project.tasks.matching {
            it.name == "check" || it.name.startsWith("assemble") || it.name.startsWith("bundle")
        }.configureEach {
            dependsOn(task)
        }
    }

    private fun collectProjectEdges(root: Project): List<String> = root.allprojects
        .flatMap { source ->
            source.configurations.flatMap { configuration ->
                configuration.dependencies.withType(ProjectDependency::class.java).map { dependency ->
                    listOf(source.path, configuration.name, dependency.path).joinToString("\t")
                }
            }
        }
        .distinct()
        .sorted()
}

abstract class VerifyTsuyomiArchitectureTask : DefaultTask() {
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:Input
    abstract val declaredProjectEdges: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinSources: ConfigurableFileCollection

    init {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Verifies the binding module DAG and UI ownership boundaries."
    }

    @TaskAction
    fun verifyArchitecture() {
        val rootDir = rootDirectory.get().asFile
        val violations = buildList {
            declaredProjectEdges.get().forEach { encodedEdge ->
                val (source, configuration, target) = encodedEdge.split('\t')
                // AGP connects a project's test variants to its own production variant.
                if (source == target) return@forEach
                val fixtureEdge = configuration == "androidTestImplementation" &&
                    target in allowedAndroidTestEdges[source].orEmpty()
                val allowedTargets = allowedProjectEdges[source]
                if (allowedTargets != null && target !in allowedTargets && !fixtureEdge) {
                    val buildFile = source.removePrefix(":").replace(':', '/') + "/build.gradle.kts"
                    add(
                        "Dependency DAG violation: $buildFile declares $configuration dependency on " +
                            "$target, which is not listed for $source in UI Constitution §2.2.",
                    )
                }
            }

            kotlinSources.files
                .sortedBy { it.relativeTo(rootDir).invariantSeparatorsPath }
                .forEach { file ->
                    val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
                    val module = moduleFor(relativePath) ?: return@forEach
                    if (module == ":core:ui") return@forEach

                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            val importPath = importPattern.matchEntire(line)?.groupValues?.get(1) ?: return@forEachIndexed
                            when {
                                importPath.startsWith("androidx.compose.animation.") -> add(
                                    "UI ownership violation: $relativePath:${index + 1} imports $importPath; " +
                                        "Compose animation APIs are only permitted in :core:ui.",
                                )
                                isInteractiveMaterialImport(importPath) -> add(
                                    "UI ownership violation: $relativePath:${index + 1} imports $importPath; " +
                                        "interactive Material APIs are only permitted in :core:ui.",
                                )
                                isNavControllerImport(importPath) && module != ":app" -> add(
                                    "Navigation ownership violation: $relativePath:${index + 1} imports $importPath; " +
                                        "NavController is only permitted in :app route hosts.",
                                )
                            }
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(separator = "\n"))
        }
    }

    private fun moduleFor(relativePath: String): String? {
        val segments = relativePath.split('/')
        return when {
            segments.firstOrNull() == "app" && segments.getOrNull(1) == "src" -> ":app"
            segments.getOrNull(0) in setOf("core", "feature", "reader", "shared", "source") &&
                segments.getOrNull(2) == "src" -> ":${segments[0]}:${segments[1]}"
            else -> null
        }
    }

    private fun isInteractiveMaterialImport(importPath: String): Boolean {
        if (!importPath.startsWith("androidx.compose.material3.")) return false
        val symbol = importPath.substringAfterLast('.')
        return symbol == "*" || interactiveMaterialFragments.any { fragment -> symbol.contains(fragment) }
    }

    private fun isNavControllerImport(importPath: String): Boolean =
        importPath.startsWith("androidx.navigation.") &&
            ("NavController" in importPath || importPath.endsWith(".NavHostController"))

    private companion object {
        val allowedAndroidTestEdges = mapOf(":reader:ui" to setOf(":core:preferences"))
        val importPattern = Regex("""\s*import\s+([A-Za-z0-9_.*]+)(?:\s+as\s+[A-Za-z0-9_]+)?\s*""")

        val interactiveMaterialFragments = listOf(
            "AlertDialog",
            "Button",
            "TextField",
            "Checkbox",
            "Switch",
            "Chip",
            "Menu",
            "Sheet",
            "Snackbar",
            "Drawer",
            "RadioButton",
            "Slider",
            "DatePicker",
            "TimePicker",
            "TimeInput",
            "Tab",
            "NavigationBar",
            "NavigationRail",
            "PullToRefresh",
            "SearchBar",
            "Tooltip",
        )

        val allowedProjectEdges = mapOf(
            ":shared:library-domain" to setOf(
                ":shared:model",
                ":shared:locator",
                ":shared:smart-shelf",
                ":shared:source-contract",
            ),
            ":core:preferences" to setOf(
                ":shared:backup",
                ":shared:library-domain",
                ":shared:model",
            ),
            ":core:display" to setOf(
                ":core:preferences",
                ":shared:model",
                ":shared:locator",
            ),
            ":core:ui" to setOf(
                ":core:display",
                ":shared:library-domain",
                ":shared:model",
                ":core:media",
            ),
            ":core:library" to setOf(
                ":shared:library-domain",
                ":shared:source-contract",
                ":shared:backup",
                ":shared:model",
            ),
            ":core:database" to setOf(
                ":shared:library-domain",
                ":shared:model",
                ":shared:locator",
                ":shared:smart-shelf",
                ":shared:backup",
                ":shared:source-contract",
            ),
            ":core:media" to setOf(
                ":core:network",
                ":core:files",
                ":core:security",
                ":core:display",
                ":shared:library-domain",
                ":shared:source-contract",
            ),
            ":reader:engine" to setOf(
                ":shared:locator",
                ":shared:model",
                ":shared:source-contract",
                ":shared:library-domain",
            ),
            ":reader:ui" to setOf(
                ":core:ui",
                ":core:display",
                ":reader:engine",
                ":shared:library-domain",
                ":shared:locator",
                ":shared:backup",
                ":shared:source-contract",
                ":core:media",
            ),
            ":feature:library" to setOf(
                ":core:ui",
                ":core:display",
                ":core:library",
                ":core:preferences",
                ":shared:library-domain",
                ":shared:model",
                ":shared:locator",
                ":shared:source-contract",
                ":core:media",
            ),
            ":feature:book" to setOf(
                ":core:ui",
                ":core:display",
                ":core:library",
                ":core:preferences",
                ":shared:library-domain",
                ":shared:model",
                ":shared:locator",
                ":shared:source-contract",
                ":core:media",
            ),
            ":feature:search" to setOf(
                ":core:ui",
                ":core:display",
                ":core:library",
                ":shared:library-domain",
                ":shared:model",
                ":shared:source-contract",
                ":core:media",
            ),
            ":feature:browse" to setOf(
                ":core:ui",
                ":core:display",
                ":core:library",
                ":core:preferences",
                ":shared:library-domain",
                ":shared:model",
                ":shared:source-contract",
                ":core:media",
            ),
            ":feature:settings" to setOf(
                ":core:ui",
                ":core:display",
                ":core:preferences",
                ":shared:library-domain",
                ":shared:backup",
            ),
            ":feature:extensions" to setOf(
                ":core:ui",
                ":core:display",
                ":source:extension-manager",
                ":shared:source-contract",
            ),
            ":feature:reader" to setOf(
                ":core:ui",
                ":core:display",
                ":core:preferences",
                ":reader:ui",
                ":shared:library-domain",
                ":shared:locator",
                ":shared:source-contract",
                ":shared:backup",
                ":core:media",
            ),
            ":feature:backup" to setOf(
                ":core:ui",
                ":core:display",
                ":core:preferences",
                ":core:library",
                ":core:files",
                ":shared:backup",
                ":shared:library-domain",
            ),
            ":app" to setOf(
                ":feature:library",
                ":feature:browse",
                ":feature:settings",
                ":feature:search",
                ":feature:book",
                ":feature:reader",
                ":feature:backup",
                ":feature:extensions",
                ":core:ui",
                ":core:display",
                ":core:preferences",
                ":core:library",
                ":core:database",
                ":core:media",
                ":core:network",
                ":core:files",
                ":core:security",
                ":core:webview",
                ":source:extension-manager",
                ":source:extension-testkit",
                ":reader:engine",
                ":reader:ui",
                ":reader:tts",
                ":shared:library-domain",
                ":shared:model",
                ":shared:locator",
                ":shared:backup",
                ":shared:smart-shelf",
                ":shared:source-contract",
            ),
        )
    }
}
