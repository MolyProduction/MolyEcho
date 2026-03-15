package com.module.notelycompose.permissions

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
actual fun rememberPermissionHandler(): PermissionHandler =
    koinViewModel<PermissionViewModel>()
