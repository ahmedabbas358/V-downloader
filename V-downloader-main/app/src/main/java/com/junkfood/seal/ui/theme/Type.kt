@file:OptIn(ExperimentalTextApi::class, ExperimentalTextApi::class, ExperimentalTextApi::class)

package com.junkfood.seal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp

val Typography =
    Typography().run {
        copy(
            bodyLarge = bodyLarge.copy(letterSpacing = 0.15.sp).applyLinebreak().applyTextDirection(),
            bodyMedium = bodyMedium.copy(letterSpacing = 0.25.sp).applyLinebreak().applyTextDirection(),
            bodySmall = bodySmall.copy(letterSpacing = 0.4.sp).applyLinebreak().applyTextDirection(),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp).applyTextDirection(),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp).applyTextDirection(),
            titleSmall = titleSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp).applyTextDirection(),
            headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp).applyTextDirection(),
            headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp).applyTextDirection(),
            headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp).applyTextDirection(),
            displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp).applyTextDirection(),
            displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp).applyTextDirection(),
            displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp).applyTextDirection(),
            labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp).applyTextDirection(),
            labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp).applyTextDirection(),
            labelSmall = labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp).applyTextDirection(),
        )
    }

private fun TextStyle.applyLinebreak(): TextStyle = this.copy(lineBreak = LineBreak.Paragraph)

private fun TextStyle.applyTextDirection(): TextStyle =
    this.copy(textDirection = TextDirection.Content)
