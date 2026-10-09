package com.example.ui.overlay

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.example.model.ThemeStyle

object ThemeDrawer {

    fun createBackgroundShader(theme: ThemeStyle, bounds: RectF): Shader? {
        return when (theme) {
            ThemeStyle.AURORA -> {
                LinearGradient(
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    0xFFDB2777.toInt(),
                    0xFF7C3AED.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            else -> null
        }
    }

    fun createStrokeShader(theme: ThemeStyle, bounds: RectF): Shader? {
        return when (theme) {
            ThemeStyle.NEON_DARK -> {
                LinearGradient(
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    0xFF00F2FE.toInt(),
                    0xFF9B51E0.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            else -> null
        }
    }

    fun getContentColor(theme: ThemeStyle): Int {
        return when (theme) {
            ThemeStyle.GLASS -> 0xFFFFFFFF.toInt()
            ThemeStyle.NEON_DARK -> 0xFF00F2FE.toInt()
            ThemeStyle.MINIMAL_MONO -> 0xFFF4F4F5.toInt()
            ThemeStyle.AURORA -> 0xFFFFFFFF.toInt()
        }
    }
}
