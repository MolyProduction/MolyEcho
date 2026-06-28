package com.module.notelycompose.permissions

import kotlinx.coroutines.flow.StateFlow

enum class NotificationPermissionState {
    NOT_ASKED,        // Noch nicht angefragt
    DENIED_ONCE,      // Einmal abgelehnt – Rationale anzeigen
    DENIED_PERMANENT  // Dauerhaft abgelehnt – App-Einstellungen öffnen
}

interface PermissionHandler {
    val isNotificationGranted: StateFlow<Boolean>
    val isBatteryOptimizationDisabled: StateFlow<Boolean>
    val notificationPermissionState: StateFlow<NotificationPermissionState>
    fun requestNotificationPermission()
    fun openBatterySettings()
    fun openNotificationSettings()
    fun refresh()
}
