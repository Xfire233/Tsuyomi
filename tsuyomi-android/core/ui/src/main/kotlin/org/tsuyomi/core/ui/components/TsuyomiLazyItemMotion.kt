/* SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.tsuyomi.core.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import org.tsuyomi.core.display.LocalDisplayEnvironment
import org.tsuyomi.core.ui.theme.TsuyomiMotion
import org.tsuyomi.core.ui.theme.instantMotion

/** Applies the shared bounded placement/fade transition, skipping preview-only motion. */
@Composable
fun Modifier.tsuyomiAnimateItem(scope: LazyItemScope): Modifier {
    val instant = LocalDisplayEnvironment.current.instantMotion
    return if (LocalInspectionMode.current) this else with(scope) {
        this@tsuyomiAnimateItem.animateItem(
            fadeInSpec = org.tsuyomi.core.ui.theme.policyMotionSpec(instant, TsuyomiMotion.SWITCH_DURATION_MS),
            placementSpec = org.tsuyomi.core.ui.theme.policyMotionSpec(instant, TsuyomiMotion.EXPAND_DURATION_MS),
            fadeOutSpec = org.tsuyomi.core.ui.theme.policyMotionSpec(instant, TsuyomiMotion.SWITCH_DURATION_MS),
        )
    }
}

/** Grid equivalent of [tsuyomiAnimateItem]. */
@Composable
fun Modifier.tsuyomiAnimateItem(scope: LazyGridItemScope): Modifier {
    val instant = LocalDisplayEnvironment.current.instantMotion
    return if (LocalInspectionMode.current) this else with(scope) {
        this@tsuyomiAnimateItem.animateItem(
            fadeInSpec = org.tsuyomi.core.ui.theme.policyMotionSpec(instant, TsuyomiMotion.SWITCH_DURATION_MS),
            placementSpec = org.tsuyomi.core.ui.theme.policyMotionSpec(instant, TsuyomiMotion.EXPAND_DURATION_MS),
            fadeOutSpec = org.tsuyomi.core.ui.theme.policyMotionSpec(instant, TsuyomiMotion.SWITCH_DURATION_MS),
        )
    }
}
