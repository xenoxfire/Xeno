package com.example.model

enum class ThemeStyle(val id: Int, val displayName: String) {
    GLASS(1, "Glass"),
    NEON_DARK(2, "Neon Dark"),
    MINIMAL_MONO(3, "Minimal Mono"),
    AURORA(4, "Aurora");

    companion object {
        fun fromId(id: Int): ThemeStyle {
            return entries.firstOrNull { it.id == id } ?: NEON_DARK
        }
    }
}

data class SettingsModel(
    val bubbleSizeDp: Int = 56,
    val bubbleOpacity: Float = 0.95f,
    val themeStyle: ThemeStyle = ThemeStyle.NEON_DARK,
    val hapticFeedback: Boolean = true,
    val collapseAfterPaste: Boolean = false,
    val snapToEdge: Boolean = false,
    val savedText: String = "Quick response pasted via Xeno floating bubble."
)
