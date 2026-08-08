/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.core.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val displayStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 28.sp,
    lineHeight = 42.sp,
)

private val headlineStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp,
    lineHeight = 34.sp,
)

private val titleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 28.sp,
)

private val bodyStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
)

private val labelStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 22.sp,
)

private val captionStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
)

/**
 * Tsuyomi Ink type scale. Uses the system CJK sans-serif, only Regular/Medium weights, and every
 * line height is at least 1.5x the font size so CJK glyphs never clip at large font scales.
 */
val TsuyomiTypography: Typography = Typography(
    displayLarge = displayStyle,
    displayMedium = displayStyle,
    displaySmall = displayStyle,
    headlineLarge = headlineStyle,
    headlineMedium = headlineStyle,
    headlineSmall = headlineStyle,
    titleLarge = titleStyle,
    titleMedium = titleStyle,
    titleSmall = labelStyle.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = bodyStyle,
    bodyMedium = bodyStyle.copy(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = captionStyle,
    labelLarge = labelStyle,
    labelMedium = captionStyle.copy(fontWeight = FontWeight.Medium),
    labelSmall = captionStyle,
)

/** Corner radii for the standard profile. E-ink components use explicit angular geometry. */
val TsuyomiShapes: Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** Spacing scale on a 4dp base unit. */
object TsuyomiSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp
    val Xxl = 48.dp
}
