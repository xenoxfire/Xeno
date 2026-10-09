package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsRepository
import com.example.model.SettingsModel
import com.example.model.ThemeStyle
import com.example.service.OverlayService
import com.example.time.TimeController
import com.example.util.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

data class PermissionsState(
    val isOverlayGranted: Boolean = false,
    val isAccessibilityGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isShizukuRunning: Boolean = false,
    val isShizukuPermissionGranted: Boolean = false
) {
    val areAllGranted: Boolean
        get() = isOverlayGranted && isAccessibilityGranted && isNotificationGranted &&
                isBatteryOptimizationIgnored && isShizukuRunning && isShizukuPermissionGranted
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository.getInstance(application)

    val settings: StateFlow<SettingsModel> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsModel()
    )

    val isServiceRunning: StateFlow<Boolean> = OverlayService.isRunningFlow

    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage: StateFlow<String?> = _userFeedbackMessage.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        _permissionsState.value = PermissionsState(
            isOverlayGranted = PermissionManager.canDrawOverlays(context),
            isAccessibilityGranted = PermissionManager.isAccessibilityServiceEnabled(context),
            isNotificationGranted = PermissionManager.isNotificationPermissionGranted(context),
            isBatteryOptimizationIgnored = PermissionManager.isIgnoringBatteryOptimizations(context),
            isShizukuRunning = PermissionManager.isShizukuRunning(),
            isShizukuPermissionGranted = PermissionManager.isShizukuPermissionGranted()
        )
    }

    fun toggleService() {
        val context = getApplication<Application>()
        if (isServiceRunning.value) {
            OverlayService.stop(context)
        } else {
            if (_permissionsState.value.areAllGranted) {
                OverlayService.start(context)
            } else {
                _userFeedbackMessage.value = "Please grant all required permissions first!"
            }
        }
    }

    fun saveText(newText: String) {
        viewModelScope.launch {
            repository.updateSavedText(newText)
            _userFeedbackMessage.value = "Saved text updated!"
        }
    }

    fun restoreAutoTime() {
        viewModelScope.launch {
            val success = TimeController.enableAutoTime()
            if (success) {
                _userFeedbackMessage.value = "Automatic time restored (auto_time = 1)"
            } else {
                _userFeedbackMessage.value = "Failed: Shizuku not ready"
            }
        }
    }

    fun requestShizukuPermission() {
        try {
            if (TimeController.isShizukuAvailable()) {
                if (!TimeController.hasShizukuPermission()) {
                    Shizuku.requestPermission(1001)
                }
            }
        } catch (e: Exception) {
            _userFeedbackMessage.value = "Shizuku request error: ${e.message}"
        }
    }

    fun updateBubbleSize(sizeDp: Int) {
        viewModelScope.launch {
            repository.updateBubbleSize(sizeDp)
        }
    }

    fun updateBubbleOpacity(opacity: Float) {
        viewModelScope.launch {
            repository.updateBubbleOpacity(opacity)
        }
    }

    fun updateThemeStyle(style: ThemeStyle) {
        viewModelScope.launch {
            repository.updateThemeStyle(style)
        }
    }

    fun updateHaptic(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateHapticFeedback(enabled)
        }
    }

    fun updateCollapseAfterPaste(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateCollapseAfterPaste(enabled)
        }
    }

    fun updateSnapToEdge(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSnapToEdge(enabled)
        }
    }

    fun clearFeedbackMessage() {
        _userFeedbackMessage.value = null
    }
}
