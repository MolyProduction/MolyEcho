package com.module.notelycompose.permissions

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PermissionViewModel(
    private val app: Application,
    private val launcherHolder: PermissionLauncherHolder
) : ViewModel(), PermissionHandler {

    private val _isNotificationGranted = MutableStateFlow(checkNotificationGranted())
    override val isNotificationGranted: StateFlow<Boolean> = _isNotificationGranted

    private val _isBatteryOptimizationDisabled = MutableStateFlow(checkBatteryOptimizationDisabled())
    override val isBatteryOptimizationDisabled: StateFlow<Boolean> = _isBatteryOptimizationDisabled

    private val _notificationPermissionState = MutableStateFlow(NotificationPermissionState.NOT_ASKED)
    override val notificationPermissionState: StateFlow<NotificationPermissionState> = _notificationPermissionState

    init {
        launcherHolder.onNotificationPermissionResult = { isGranted, showRationale ->
            _isNotificationGranted.value = isGranted
            if (isGranted) {
                _notificationPermissionState.value = NotificationPermissionState.NOT_ASKED
            } else {
                _notificationPermissionState.value = if (showRationale) {
                    NotificationPermissionState.DENIED_ONCE
                } else {
                    NotificationPermissionState.DENIED_PERMANENT
                }
            }
        }
    }

    override fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcherHolder.notificationLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Unter Android 13 sind Benachrichtigungen standardmäßig erlaubt
            _isNotificationGranted.value = true
            _notificationPermissionState.value = NotificationPermissionState.NOT_ASKED
        }
    }

    override fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        app.startActivity(intent)
    }

    override fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", app.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        app.startActivity(intent)
    }

    override fun refresh() {
        _isNotificationGranted.value = checkNotificationGranted()
        _isBatteryOptimizationDisabled.value = checkBatteryOptimizationDisabled()
    }

    private fun checkNotificationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkBatteryOptimizationDisabled(): Boolean {
        val pm = app.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(app.packageName)
    }
}
