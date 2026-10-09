package com.example

import com.example.model.SettingsModel
import com.example.model.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun themeStyle_fromId_worksCorrectly() {
        assertEquals(ThemeStyle.GLASS, ThemeStyle.fromId(1))
        assertEquals(ThemeStyle.NEON_DARK, ThemeStyle.fromId(2))
        assertEquals(ThemeStyle.MINIMAL_MONO, ThemeStyle.fromId(3))
        assertEquals(ThemeStyle.AURORA, ThemeStyle.fromId(4))
        assertEquals(ThemeStyle.NEON_DARK, ThemeStyle.fromId(999))
    }

    @Test
    fun settingsModel_defaultsAreValid() {
        val defaultModel = SettingsModel()
        assertEquals(56, defaultModel.bubbleSizeDp)
        assertEquals(0.95f, defaultModel.bubbleOpacity, 0.001f)
        assertEquals(ThemeStyle.NEON_DARK, defaultModel.themeStyle)
        assertTrue(defaultModel.hapticFeedback)
        assertFalse(defaultModel.collapseAfterPaste)
        assertFalse(defaultModel.snapToEdge)
        assertTrue(defaultModel.savedText.isNotEmpty())
    }
}
