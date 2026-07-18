package com.junkfood.seal.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

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

@androidx.compose.foundation.ExperimentalFoundationApi
fun Modifier.hapticCombinedClickable(
    interactionSource: MutableInteractionSource? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val finalInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    
    this.androidx.compose.foundation.combinedClickable(
        interactionSource = finalInteractionSource,
        indication = androidx.compose.material3.ripple(),
        onLongClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            if (onLongClick != null) onLongClick()
        },
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    )
}

