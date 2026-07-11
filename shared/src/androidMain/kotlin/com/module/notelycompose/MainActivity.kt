package com.module.notelycompose

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import audio.utils.LauncherHolder
import org.koin.android.ext.android.inject
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.module.notelycompose.onboarding.data.PreferencesRepository
import com.module.notelycompose.platform.Theme
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.module.notelycompose.permissions.PermissionLauncherHolder
import android.content.Intent
import androidx.core.content.IntentCompat

class MainActivity : AppCompatActivity() {
    private val fileSaverLauncherHolder by inject<FileSaverLauncherHolder>()
    private val folderPickerLauncherHolder by inject<FolderPickerLauncherHolder>()
    private val permissionLauncherHolder by inject<PermissionLauncherHolder>()
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        injectLauncher()
        setupFileSaverLauncher()
        setupFolderPickerLauncher()
        setupNotificationPermissionLauncher()
        handleShareIntent(intent)
        enableEdgeToEdge()
        setContent {
            val preferenceRepository by inject<PreferencesRepository>()
            val uiMode by preferenceRepository.getTheme().collectAsState(Theme.SYSTEM.name)
            val darkTheme = when (uiMode) {
                Theme.DARK.name -> true
                Theme.LIGHT.name -> false
                else -> isSystemInDarkTheme()
            }
            // Ersetzt den (deprecated) Accompanist-SystemUiController: transparente
            // System-Bars mit zum Theme passender Icon-Farbe. Muss dem In-App-Theme
            // aus dem DataStore folgen, nicht nur dem System-Dunkelmodus — deshalb
            // hier statt einmalig in onCreate.
            LaunchedEffect(darkTheme) {
                val style =
                    if (darkTheme) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            App()
        }
    }

    private fun setupFileSaverLauncher() {
        fileSaverLauncherHolder.fileSaverLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri: Uri? ->
                uri?.let {
                    fileSaverLauncherHolder.onFileSaved?.invoke(uri.toString())
                }
            }
    }

    private fun setupFolderPickerLauncher() {
        folderPickerLauncherHolder.folderPickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                if (uri != null) {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                // Always invoke callback, even if uri is null
                folderPickerLauncherHolder.onFolderSelected?.invoke(uri)
            }
    }

    private fun setupNotificationPermissionLauncher() {
        permissionLauncherHolder.notificationLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                val showRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted) {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    )
                } else false
                permissionLauncherHolder.onNotificationPermissionResult?.invoke(isGranted, showRationale)
            }
    }

    private fun injectLauncher() {
        val launcherHolder by inject<LauncherHolder>()
        launcherHolder.init(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
            ?: return
        if (!ShareIntentBus.incoming.tryEmit(uri)) {
            ShareIntentBus.incoming.resetReplayCache()
            ShareIntentBus.incoming.tryEmit(uri)
        }
    }
}
