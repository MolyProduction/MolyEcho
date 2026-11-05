package com.module.notelycompose.dictation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.module.notelycompose.service.TranscriptionForegroundService
import de.molyecho.notlyvoice.android.R

/**
 * Tipp auf „Text bereit": kopiert das letzte Diktat in die Zwischenablage.
 * Bewusst ein Receiver ohne Activity - Notification-Trampoline sind ab Android 12 gesperrt.
 */
class DictationCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DictationIntents.ACTION_COPY_RESULT) return
        val text = DictationResultStore(context).read() ?: return
        val copied = copyToClipboard(context, text)
        // Android 13+ zeigt selbst eine Bestätigung der Zwischenablage; davor ein eigener Toast.
        if (copied && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.dictation_toast_copied, Toast.LENGTH_SHORT).show()
        }
        NotificationManagerCompat.from(context)
            .cancel(TranscriptionForegroundService.NOTIFICATION_DICTATION_ID)
    }
}
