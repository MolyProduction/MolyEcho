package com.module.notelycompose.platform

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.module.notelycompose.MainActivity
import de.molyecho.notlyvoice.android.R
import com.module.notelycompose.core.debugPrintln
import com.module.notelycompose.onboarding.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

actual class Downloader(
    private val mainContext: Context,
    private val preferencesRepository: PreferencesRepository
) {

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)


    actual suspend fun startDownload(url: String, fileName: String) {
        try {
            val request = DownloadManager.Request(url.toUri())
                .setTitle("Downloading $fileName")
                .setDestinationInExternalFilesDir(
                    mainContext,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)

            val downloadManager =
                mainContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            preferencesRepository.setModelDownloadId(downloadId)

        } catch (e: NullPointerException) {
            debugPrintln {"Invalid download URL $url: ${e.message}"}
        } catch (e: Exception) {
            debugPrintln {"Failed to start download: ${e.message}"}
        }
    }

    actual suspend fun hasRunningDownload(): Boolean {
        val downloadId = preferencesRepository.getModelDownloadId().first()
        if (downloadId == -1L) {
            return false
        }
        val downloadManager =
            mainContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        cursor.use {
            if (it.moveToFirst()) {
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                return status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
            }
        }
        return false
    }

    actual suspend fun trackDownloadProgress(
        fileName: String,
        onProgressUpdated: (progress: Int, downloadedMB: String, totalMB: String) -> Unit,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val downloadId = preferencesRepository.getModelDownloadId().first()
        if (downloadId == -1L) return

        registerDownloadReceiver(downloadId)
        val downloadManager = mainContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var finalStatus = -1
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val shouldBreak = downloadManager.query(query).use { cursor ->
                if (cursor.moveToFirst()) {
                    val bytesDownloaded =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal =
                        cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    if (bytesTotal > 0) {
                        val progress = (bytesDownloaded * 100L / bytesTotal).toInt()
                        val downloadedMB = String.format("%.2f MB", bytesDownloaded / 1024.0 / 1024.0)
                        val totalMB = String.format("%.2f MB", bytesTotal / 1024.0 / 1024.0)
                        onProgressUpdated(progress, downloadedMB, totalMB)
                    }

                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        finalStatus = status
                        true
                    } else {
                        false
                    }
                } else {
                    true // Cursor leer → abbrechen
                }
            }
            if (shouldBreak) break
            delay(1000)
        }

        // Resolve outcome directly from polling result — no BroadcastReceiver race
        coroutineScope.launch {
            preferencesRepository.setModelDownloadId(-1)
        }
        when (finalStatus) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                postDownloadCompleteNotification()
                onSuccess()
            }
            DownloadManager.STATUS_FAILED -> {
                val reason = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    } else DownloadManager.ERROR_UNKNOWN
                }
                onFailed(getErrorTextFromReason(reason))
            }
        }
    }

    private fun registerDownloadReceiver(downloadId: Long) {
        val filter = IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    when (intent.action) {
                        DownloadManager.ACTION_NOTIFICATION_CLICKED -> {
                            debugPrintln{"Opening downloads..."}
                        }
                    }
                    mainContext.unregisterReceiver(this)
                }
            }
        }

        ContextCompat.registerReceiver(
            mainContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun postDownloadCompleteNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(mainContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = mainContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOAD_DONE_ID,
                mainContext.getString(R.string.notification_download_done_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val pendingIntent = PendingIntent.getActivity(
            mainContext,
            0,
            Intent(mainContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(mainContext, CHANNEL_DOWNLOAD_DONE_ID)
            .setContentTitle(mainContext.getString(R.string.notification_download_done_title))
            .setContentText(mainContext.getString(R.string.notification_download_done_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_DOWNLOAD_DONE_ID, notification)
    }

    private fun getErrorTextFromReason(reason: Int) = when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "ERROR_CANNOT_RESUME"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "ERROR_DEVICE_NOT_FOUND"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "ERROR_FILE_ALREADY_EXISTS"
            DownloadManager.ERROR_FILE_ERROR -> "ERROR_FILE_ERROR"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "ERROR_HTTP_DATA_ERROR"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "ERROR_INSUFFICIENT_SPACE"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "ERROR_TOO_MANY_REDIRECTS"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "ERROR_UNHANDLED_HTTP_CODE"
            DownloadManager.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
        else -> "DOWNLOAD_ERROR"
    }

    companion object {
        private const val CHANNEL_DOWNLOAD_DONE_ID = "download_done_channel"
        private const val NOTIFICATION_DOWNLOAD_DONE_ID = 4
    }
}