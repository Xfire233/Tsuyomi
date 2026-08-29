/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.android

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch
import org.tsuyomi.core.display.ColorSchemePreference
import org.tsuyomi.core.display.DisplayController
import org.tsuyomi.core.display.DisplayEnvironment
import org.tsuyomi.core.display.DisplayEnvironmentProvider
import org.tsuyomi.core.display.DisplayEnvironmentResolver
import org.tsuyomi.core.display.DisplaySystemState
import org.tsuyomi.core.display.DisplayPreference
import org.tsuyomi.core.ui.theme.TsuyomiBootScreen
import org.tsuyomi.core.ui.theme.TsuyomiTheme
import org.tsuyomi.core.ui.theme.rememberSystemReducedMotion
import org.tsuyomi.feature.settings.DisplaySettingsActions
import org.tsuyomi.feature.settings.DisplaySettingsScreen
import org.tsuyomi.feature.settings.DisplaySettingsUiState
import org.tsuyomi.feature.settings.DisplayWriteFailure

@Composable
internal fun TsuyomiApplicationRoot(controller: DisplayController) {
    val redrawEpoch by controller.redrawEpoch.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val reducedMotion = rememberSystemReducedMotion()
    val resolver = remember { DisplayEnvironmentResolver() }
    val preferences by controller.preferences.collectAsStateWithLifecycle(initialValue = null)

    val currentPreferences = preferences
    if (currentPreferences == null) {
        TsuyomiBootScreen {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Text(
                    text = stringResource(R.string.boot_loading),
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                )
            }
        }
        return
    }

    val environment = resolver.resolve(
        preferences = currentPreferences,
        classification = controller.classification,
        system = DisplaySystemState(
            apiLevel = android.os.Build.VERSION.SDK_INT,
            systemDark = systemDark,
            reducedMotion = reducedMotion,
            redrawEpoch = redrawEpoch,
        ),
    )

    DisplayEnvironmentProvider(environment) {
        TsuyomiTheme {
            SystemBarPolicy(environment)
            TsuyomiApp(environment, controller)
        }
    }
}

@Composable
internal fun DisplaySettingsRoute(
    environment: DisplayEnvironment,
    controller: DisplayController,
) {
    val scope = rememberCoroutineScope()
    val arbiter = rememberDisplayWriteArbiter()

    fun submit(key: String, write: suspend () -> Unit) {
        val ticket = arbiter.begin(key)
        scope.launch {
            try {
                write()
                arbiter.succeed(ticket)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                arbiter.fail(ticket)
            }
        }
    }

    fun retry() {
        val key = arbiter.retryKey ?: return
        when {
            key.startsWith("profile:") -> submit(key) {
                controller.setDisplayPreference(DisplayPreference.valueOf(key.substringAfter(':')))
            }
            key.startsWith("scheme:") -> submit(key) {
                controller.setColorSchemePreference(ColorSchemePreference.valueOf(key.substringAfter(':')))
            }
            key.startsWith("dynamic:") -> submit(key) {
                controller.setDynamicColorEnabled(key.substringAfter(':').toBooleanStrict())
            }
        }
    }

    DisplaySettingsScreen(
        state = DisplaySettingsUiState(
            environment = environment,
            writeFailure = if (arbiter.hasFailure) DisplayWriteFailure(arbiter.failureSequence) else null,
        ),
        actions = DisplaySettingsActions(
            onDisplayPreferenceChange = { preference ->
                submit("profile:${preference.name}") { controller.setDisplayPreference(preference) }
            },
            onColorSchemePreferenceChange = { preference ->
                submit("scheme:${preference.name}") { controller.setColorSchemePreference(preference) }
            },
            onDynamicColorEnabledChange = { enabled ->
                submit("dynamic:$enabled") { controller.setDynamicColorEnabled(enabled) }
            },
            onRefreshNow = controller::requestRedraw,
            onRetryWrite = ::retry,
            onAcknowledgeWriteFailure = arbiter::acknowledge,
        ),
    )
}

@Composable
internal fun SystemBarPolicy(environment: DisplayEnvironment) {
    val activity = LocalActivity.current ?: return
    val background = MaterialTheme.colorScheme.background.toArgb()
    val lightIcons = environment.effectiveDarkTheme
    SideEffect {
        activity.window.statusBarColor = background
        activity.window.navigationBarColor = background
        activity.window.isNavigationBarContrastEnforced = false
        activity.window.isStatusBarContrastEnforced = false
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
        }
    }
}
