/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas
import android.content.Intent

import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import java.io.File
import java.nio.charset.StandardCharsets
import org.tsuyomi.prototype.uiatlas.model.AtlasContext
import org.tsuyomi.prototype.uiatlas.model.AtlasLayout
import org.tsuyomi.prototype.uiatlas.model.AtlasLibraryView
import org.tsuyomi.prototype.uiatlas.model.AtlasPageState
import org.tsuyomi.prototype.uiatlas.model.AtlasProfile
import org.tsuyomi.prototype.uiatlas.model.AtlasRoute
import org.tsuyomi.prototype.uiatlas.model.AtlasThemeKind
import org.tsuyomi.prototype.uiatlas.model.AtlasReaderSeekPreview
import org.tsuyomi.prototype.uiatlas.model.AtlasReviewSpec
import org.tsuyomi.prototype.uiatlas.model.AtlasVariant
import org.tsuyomi.prototype.uiatlas.runtime.PrototypeRuntime
import org.tsuyomi.prototype.uiatlas.review.ReviewJsonExporter

/**
 * Launcher activity of the prototype UI atlas.
 *
 * Deterministic capture entry point (Atlas Spec §7): every frame dimension arrives as an intent
 * extra, so a capture harness can render any route × state × profile × theme × variant without
 * mutating fixture data. Recognized extras:
 *
 * - `route` — enum name (`LIBRARY_HISTORY`), pattern (`library/history`), or concrete path
 * - `state` — loading / content / empty / error / offline / refreshing / selection / mutation /
 *   unresolved / modal
 * - `profile` — standard / eink
 * - `theme` — light / dark / dynamic (deterministic seed palette)
 * - `variant` — comparison variant + option, e.g. `H-b`, `E-2`
 * - `layout` — list / grid (defaults per §5.4 when absent)
 * - `view` — library view: all / continue / recent / read-later / dormant / collection / mirror
 * - `motion` — `reduced` enables the reduced-motion INSTANT policy
 * - `capture` — `true` renders only the route surface; otherwise the product-faithful
 *   书架 / 浏览 / 更多 root navigator remains visible
 * - `systemUi` — `true` draws deterministic screenshot-only status/navigation bars and a centered camera cutout
 * - `immersive` — Reader-only; `true` begins in immersive reading, but Reader chrome, settings,
 *   directories, errors and verification always restore the actual Android system bars
 *
 * Unrecognized or absent extras fall back to deterministic defaults.
 * - `reviewId` / `reviewScenario` / `reviewDefault` / `reviewVerifies` — browser-review metadata only; never rendered in the app viewport
 * - `seek` — cancel / commit / return_origin deterministic Reader seek evidence
 * - `inlineModal` — screenshot-host adapter for Standard Material 3 modal content
 */
class MainActivity : ComponentActivity() {
    private lateinit var createReviewDocument: ActivityResultLauncher<String>
    private var pendingReviewJson: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createReviewDocument = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            val json = pendingReviewJson
            pendingReviewJson = null
            if (uri != null && json != null) {
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(json.toByteArray(StandardCharsets.UTF_8))
                    }
                }
            }
        }
        window.setBackgroundDrawableResource(android.R.color.white)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val initial = contextFromIntent()
        val runtime = PrototypeRuntime(applicationContext, persistent = !initial.capture)
        configureSystemBars(initial.profile)
        setContent {
            AtlasApp(
                initial = initial,
                runtime = runtime,
                onReaderImmersiveChanged = { immersive ->
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        if (immersive) {
                            hide(WindowInsetsCompat.Type.systemBars())
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                },
                onExportReview = { includeStale -> exportReview(runtime, includeStale) },
                onShareReview = { includeStale -> shareReview(runtime, includeStale) },
            )
        }
    }

    override fun onStop() {
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        super.onStop()
    }

    private fun exportReview(runtime: PrototypeRuntime, includeStaleBuilds: Boolean) {
        pendingReviewJson = ReviewJsonExporter.export(this, runtime.reviews.snapshot.value, includeStaleBuilds)
        createReviewDocument.launch("tsuyomi-review-${BuildConfig.PROTOTYPE_BUILD_ID.take(12)}.json")
    }

    private fun shareReview(runtime: PrototypeRuntime, includeStaleBuilds: Boolean) {
        val json = ReviewJsonExporter.export(this, runtime.reviews.snapshot.value, includeStaleBuilds)
        val directory = File(cacheDir, "review-share").apply { mkdirs() }
        val file = File(directory, "tsuyomi-review-${BuildConfig.PROTOTYPE_BUILD_ID.take(12)}.json")
        file.writeText(json, StandardCharsets.UTF_8)
        val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享 Tsuyomi 审阅 JSON"))
    }
    @Suppress("DEPRECATION")
    private fun configureSystemBars(profile: AtlasProfile) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (profile == AtlasProfile.EINK) {
            window.statusBarColor = android.graphics.Color.WHITE
            window.navigationBarColor = android.graphics.Color.WHITE
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    private fun contextFromIntent(): AtlasContext {
        val extras = intent?.extras
        fun extra(key: String): String? = extras?.getString(key)
        return AtlasContext(
            route = AtlasRoute.parse(extra("route")) ?: AtlasRoute.LIBRARY,
            state = AtlasPageState.parse(extra("state")) ?: AtlasPageState.CONTENT,
            profile = AtlasProfile.parse(extra("profile")) ?: AtlasProfile.STANDARD,
            theme = AtlasThemeKind.parse(extra("theme")) ?: AtlasThemeKind.LIGHT,
            variant = AtlasVariant.parse(extra("variant")),
            layout = AtlasLayout.parse(extra("layout")),
            libraryView = AtlasLibraryView.parse(extra("view")) ?: AtlasLibraryView.ALL,
            selectedSearchSourceId = extra("selectedSourceId")?.trim()?.takeIf(String::isNotEmpty),
            reducedMotion = extra("motion")?.trim()?.lowercase() == "reduced",
            tutorial = extra("tutorial")?.trim()?.lowercase() == "true",
            capture = extra("capture")?.trim()?.lowercase() == "true",
            inlineModalPreview = extra("inlineModal")?.trim()?.lowercase() == "true",
            simulateSystemUi = extra("systemUi")?.trim()?.lowercase() == "true",
            readerImmersive = extra("immersive")?.trim()?.lowercase() == "true",
            review = extra("reviewId")?.let { id ->
                AtlasReviewSpec(
                    id = id,
                    scenario = extra("reviewScenario").orEmpty(),
                    currentDefault = extra("reviewDefault").orEmpty(),
                    verifies = extra("reviewVerifies").orEmpty(),
                )
            },
            readerSeekPreview = extra("seek")?.trim()?.uppercase()?.let { raw ->
                runCatching { AtlasReaderSeekPreview.valueOf(raw) }.getOrNull()
            },
        )
    }
}
