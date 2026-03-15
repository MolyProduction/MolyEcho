package com.module.notelycompose.permissions

import kotlinx.coroutines.flow.StateFlow

interface PermissionHandler {
    val isNotificationGranted: StateFlow<Boolean>
    val isBatteryOptimizationDisabled: StateFlow<Boolean>
    fun requestNotificationPermission()
    fun openBatterySettings()
    fun refresh()
}
