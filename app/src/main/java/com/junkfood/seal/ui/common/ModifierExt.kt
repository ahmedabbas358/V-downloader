package com.junkfood.seal.ui.common

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassmorphism(
    radius: Dp = 16.dp,
    color: Color = Color.Transparent
): Modifier = composed {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.blur(radius = radius).drawBehind {
            drawRect(color)
        }
    } else {
        this.drawBehind {
            drawRect(color.copy(alpha = (color.alpha + 0.5f).coerceAtMost(1f)))
        }
    }
}

fun Modifier.hapticClickable(
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val finalInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    
    this.clickable(
        interactionSource = finalInteractionSource,
        indication = androidx.compose.material3.ripple(),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    )
}
