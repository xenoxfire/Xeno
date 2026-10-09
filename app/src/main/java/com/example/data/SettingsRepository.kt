package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SettingsModel
import com.example.model.ThemeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xeno_settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val BUBBLE_SIZE = intPreferencesKey("bubble_size_dp")
        val BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
        val THEME_STYLE_ID = intPreferencesKey("theme_style_id")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val COLLAPSE_AFTER_PASTE = booleanPreferencesKey("collapse_after_paste")
        val SNAP_TO_EDGE = booleanPreferencesKey("snap_to_edge")
        val SAVED_TEXT = stringPreferencesKey("saved_text")
        val BUBBLE_POS_X = intPreferencesKey("bubble_pos_x")
        val BUBBLE_POS_Y = intPreferencesKey("bubble_pos_y")
    }

    val settingsFlow: Flow<SettingsModel> = context.dataStore.data.map { preferences ->
        SettingsModel(
            bubbleSizeDp = preferences[PreferencesKeys.BUBBLE_SIZE] ?: 56,
            bubbleOpacity = preferences[PreferencesKeys.BUBBLE_OPACITY] ?: 0.95f,
            themeStyle = ThemeStyle.fromId(preferences[PreferencesKeys.THEME_STYLE_ID] ?: ThemeStyle.NEON_DARK.id),
            hapticFeedback = preferences[PreferencesKeys.HAPTIC_FEEDBACK] ?: true,
            collapseAfterPaste = preferences[PreferencesKeys.COLLAPSE_AFTER_PASTE] ?: false,
            snapToEdge = preferences[PreferencesKeys.SNAP_TO_EDGE] ?: false,
            savedText = preferences[PreferencesKeys.SAVED_TEXT] ?: "Quick response pasted via Xeno floating bubble."
        )
    }

    suspend fun updateSavedText(text: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVED_TEXT] = text
        }
    }

    suspend fun updateBubbleSize(sizeDp: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUBBLE_SIZE] = sizeDp
        }
    }

    suspend fun updateBubbleOpacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUBBLE_OPACITY] = opacity
        }
    }

    suspend fun updateThemeStyle(style: ThemeStyle) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_STYLE_ID] = style.id
        }
    }

    suspend fun updateHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_FEEDBACK] = enabled
        }
    }

    suspend fun updateCollapseAfterPaste(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLLAPSE_AFTER_PASTE] = enabled
        }
    }

    suspend fun updateSnapToEdge(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SNAP_TO_EDGE] = enabled
        }
    }

    suspend fun saveBubblePosition(x: Int, y: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUBBLE_POS_X] = x
            preferences[PreferencesKeys.BUBBLE_POS_Y] = y
        }
    }

    suspend fun getSavedBubblePosition(): Pair<Int, Int>? {
        val preferences = context.dataStore.data.first()
        val x = preferences[PreferencesKeys.BUBBLE_POS_X]
        val y = preferences[PreferencesKeys.BUBBLE_POS_Y]
        return if (x != null && y != null) Pair(x, y) else null
    }

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
