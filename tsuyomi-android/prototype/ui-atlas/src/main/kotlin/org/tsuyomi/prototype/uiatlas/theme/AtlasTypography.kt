/*
 * SPDX-FileCopyrightText: 2026 Tsuyomi Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.tsuyomi.prototype.uiatlas.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
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
 * Atlas fork of the constitution §3.2 type scale: system CJK sans, Regular/Medium only, and every
 * line height ≥ 1.5× the font size so CJK glyphs never clip at fontScale 2.0.
 */
val AtlasTypography: Typography = Typography(
    displayLarge = displayStyle,
    displayMedium = displayStyle,
    displaySmall = displayStyle,
    headlineLarge = headlineStyle,
    headlineMedium = headlineStyle,
    headlineSmall = headlineStyle,
    titleLarge = titleStyle,
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = labelStyle.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = bodyStyle,
    bodyMedium = bodyStyle.copy(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = captionStyle,
    labelLarge = labelStyle,
    labelMedium = captionStyle.copy(fontWeight = FontWeight.Medium),
    labelSmall = captionStyle,
)

/** Standard-profile corner radii (constitution §3.4). */
val AtlasShapes: Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** E-ink angular geometry: separation comes from opaque borders, never from radius or shadow. */
val AtlasEInkShapes: Shapes = Shapes(
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(4.dp),
)

/** Spacing scale on a 4dp base unit (constitution §3.3). Feature code never uses raw dp gaps. */
object AtlasSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp
    val Xxl = 48.dp
}
