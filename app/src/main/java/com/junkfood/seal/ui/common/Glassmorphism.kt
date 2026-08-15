package com.junkfood.seal.ui.common

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassmorphism(
    cornerRadius: Dp = 24.dp,
    blurRadius: Float = 0f,
    backgroundColor: Color? = null,
    borderColor: Color? = null
): Modifier = composed {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val fallbackBackgroundColor = backgroundColor ?: if (isDark) {
        androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f)
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f)
    }
    
    val fallbackBorderColor = borderColor ?: if (isDark) {
        androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    this.then(
        Modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(fallbackBackgroundColor)
            .then(
                if (blurRadius > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.graphicsLayer {
                        renderEffect = RenderEffect.createBlurEffect(
                            blurRadius,
                            blurRadius,
                            Shader.TileMode.DECAL
                        ).asComposeRenderEffect()
                    }
                } else {
                    Modifier
                }
            )
            .border(1.dp, fallbackBorderColor, RoundedCornerShape(cornerRadius))
    )
}
