package com.junkfood.seal.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class SquircleShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(size.width / 2f).coerceAtMost(size.height / 2f)
        val w = size.width
        val h = size.height
        
        val p = 0.5522847498f // Kappa
        // A simple continuous curve approximation
        val c = r * (1 - p)
        val offset = r * 0.15f // Superellipse offset approximation

        val path = Path().apply {
            moveTo(r + offset, 0f)
            lineTo(w - (r + offset), 0f)
            cubicTo(w - c, 0f, w, c, w, r + offset)
            lineTo(w, h - (r + offset))
            cubicTo(w, h - c, w - c, h, w - (r + offset), h)
            lineTo(r + offset, h)
            cubicTo(c, h, 0f, h - c, 0f, h - (r + offset))
            lineTo(0f, r + offset)
            cubicTo(0f, c, c, 0f, r + offset, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
