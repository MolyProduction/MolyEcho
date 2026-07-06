package com.module.notelycompose.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.module.notelycompose.MainActivity
import com.module.notelycompose.core.debugPrintln
import com.module.notelycompose.service.ModelDownloadService
import de.molyecho.notlyvoice.android.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Direct streaming downloader built on HttpURLConnection.
 *
 * Replaces the previous android.app.DownloadManager implementation: DownloadManager ran the
 * transfer in a separate system process and got stuck in an endless PAUSED_WAITING_TO_RETRY
 * loop against HuggingFace's redirecting CDN (verified via logcat — download never left 0 %).
 * A direct connection gives us explicit redirect handling, real byte-level progress and a
 * deterministic success/failure outcome.
 *
 * Ownership model: the Downloader (a singleton) owns the transfer on its own scope, wrapped
 * in a dataSync foreground service so the process survives backgrounding. At most one
 * download session runs at any time — startDownload() while a session is active is a no-op,
 * and trackDownloadProgress() only OBSERVES the active session (progress StateFlow + result
 * Deferred), so concurrent trackers from different screens can never start a second transfer
 * onto the same file.
 *
 * Files are streamed to `<filesDir>/models/<fileName>` (the location Transcriber reads from),
 * writing first to a `.part` sibling and renaming only after the byte count was validated
 * against Content-Length, so a partial download never looks like a complete model. The
 * `.part` file is kept on failure/cancel and resumed on the next attempt via an HTTP Range
 * request.
 */
actual class Downloader(private val mainContext: Context) {

    private data class ProgressSnapshot(
        val pct: Int,
        val downloadedMB: String,
        val totalMB: String
    )

    private sealed interface DownloadResult {
        object Success : DownloadResult
        object Cancelled : DownloadResult
        data class Failed(val message: String) : DownloadResult
    }

    private class Session(val url: String, val fileName: String) {
        val progress = MutableStateFlow(ProgressSnapshot(0, "0,00 MB", "?"))
        val result = CompletableDeferred<DownloadResult>()
        @Volatile var cancelRequested = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionLock = Any()
    @Volatile private var session: Session? = null

    actual suspend fun startDownload(url: String, fileName: String) {
        val newSession: Session
        synchronized(sessionLock) {
            val current = session
            if (current != null && !current.result.isCompleted) {
                // Guard: a transfer is already running. Callers attach to it via
                // trackDownloadProgress() instead of starting a competing writer.
                Log.w(LOG_TAG, "startDownload ignored, session active for ${current.fileName}")
                return
            }
            newSession = Session(url, fileName)
            session = newSession
        }
        Log.d(LOG_TAG, "startDownload url=$url file=$fileName")
        scope.launch { runDownload(newSession) }
    }

    actual suspend fun hasRunningDownload(): Boolean =
        session?.result?.isCompleted == false

    actual suspend fun cancelDownload() {
        session?.cancelRequested = true
        debugPrintln { "Download: cancel requested" }
    }

    /**
     * Observes the active session until it reaches a terminal state, then dispatches
     * exactly one of the terminal callbacks. Never performs the transfer itself and is
     * therefore safe to call from multiple screens at once.
     */
    actual suspend fun trackDownloadProgress(
        fileName: String,
        onProgressUpdated: (progress: Int, downloadedMB: String, totalMB: String) -> Unit,
        onSuccess: () -> Unit,
        onCancelled: () -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val s = session
        if (s == null) {
            onFailed(mainContext.getString(R.string.download_error_not_started))
            return
        }
        val result = coroutineScope {
            val collector = launch {
                s.progress.collect { onProgressUpdated(it.pct, it.downloadedMB, it.totalMB) }
            }
            try {
                s.result.await()
            } finally {
                collector.cancel()
            }
        }
        when (result) {
            is DownloadResult.Success -> onSuccess()
            is DownloadResult.Cancelled -> onCancelled()
            is DownloadResult.Failed -> onFailed(result.message)
        }
    }

    private fun runDownload(s: Session) {
        val dest = File(mainContext.filesDir, "models/${s.fileName}")
        val part = File(dest.parentFile, dest.name + ".part")
        var result: DownloadResult
        try {
            dest.parentFile?.mkdirs()
            if (dest.exists()) {
                // The rename below only ever happens after a size-validated transfer, so an
                // existing destination is a complete file — skip the download (relevant for
                // multi-file retries: already-finished files are not downloaded again).
                Log.d(LOG_TAG, "file already complete, skipping download: ${s.fileName}")
                s.progress.value = ProgressSnapshot(100, formatMb(dest.length()), formatMb(dest.length()))
                result = DownloadResult.Success
            } else {
                ModelDownloadService.start(mainContext)
                try {
                    result = transfer(s, part)
                } finally {
                    ModelDownloadService.stop(mainContext)
                }
                if (result is DownloadResult.Success) {
                    if (dest.exists()) dest.delete()
                    if (!part.renameTo(dest)) {
                        // Fallback for filesystems where rename onto an existing path fails.
                        part.copyTo(dest, overwrite = true)
                        part.delete()
                    }
                    Log.d(LOG_TAG, "download complete file=${s.fileName} size=${dest.length()}")
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "download error file=${s.fileName}: ${e.message}")
            result = DownloadResult.Failed(e.message ?: mainContext.getString(R.string.download_error_generic))
        }
        when (result) {
            is DownloadResult.Cancelled -> Log.d(LOG_TAG, "download cancelled file=${s.fileName}")
            is DownloadResult.Failed -> Log.w(LOG_TAG, "download failed file=${s.fileName} reason=${result.message}")
            else -> {}
        }
        s.result.complete(result)
    }

    private fun transfer(s: Session, part: File): DownloadResult {
        var connection: HttpURLConnection? = null
        try {
            val resumeFrom = if (part.exists()) part.length() else 0L
            connection = openConnectionFollowingRedirects(s.url, resumeFrom)
            if (connection == null) {
                // HTTP 416: our resume offset is at/after the end of the resource — the
                // .part file already contains all bytes of a previous, almost-complete run.
                Log.d(LOG_TAG, "resume offset beyond resource end, treating .part as complete")
                return DownloadResult.Success
            }
            val append = connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            val alreadyDownloaded = if (append) resumeFrom else 0L
            val remaining = connection.contentLengthLong // -1 if the server omits Content-Length
            val total = if (remaining >= 0) alreadyDownloaded + remaining else -1L
            Log.d(LOG_TAG, "connected resumeFrom=$resumeFrom append=$append total=$total url=${s.url}")

            var downloaded = alreadyDownloaded
            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var lastReportedPct = -1
                    var lastReportMs = 0L
                    var lastNotificationMs = 0L
                    while (true) {
                        if (s.cancelRequested) return DownloadResult.Cancelled
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (total > 0) {
                            val pct = (downloaded * 100L / total).toInt()
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                s.progress.value = ProgressSnapshot(pct, formatMb(downloaded), formatMb(total))
                            }
                            if (now - lastNotificationMs >= NOTIFICATION_THROTTLE_MS) {
                                lastNotificationMs = now
                                ModelDownloadService.progress(
                                    mainContext, pct, "${formatMb(downloaded)} / ${formatMb(total)}"
                                )
                            }
                        } else if (now - lastReportMs >= PROGRESS_THROTTLE_MS) {
                            // Unknown size: surface byte movement so the UI isn't frozen at 0.
                            lastReportMs = now
                            s.progress.value = ProgressSnapshot(0, formatMb(downloaded), "?")
                            if (now - lastNotificationMs >= NOTIFICATION_THROTTLE_MS) {
                                lastNotificationMs = now
                                ModelDownloadService.progress(mainContext, -1, formatMb(downloaded))
                            }
                        }
                    }
                }
            }
            if (total > 0 && downloaded != total) {
                // Truncated stream that ended without an exception. Keep the .part file —
                // the next attempt resumes from this offset.
                return DownloadResult.Failed(
                    mainContext.getString(R.string.download_error_incomplete, formatMb(downloaded), formatMb(total))
                )
            }
            return DownloadResult.Success
        } catch (e: IOException) {
            // Keep the .part file for a Range-resume on the next attempt.
            return DownloadResult.Failed(e.message ?: mainContext.getString(R.string.download_error_network))
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Opens a connection and follows redirects manually — including cross-protocol ones
     * (HuggingFace redirects huggingface.co → its signed CDN host), which
     * HttpURLConnection.setInstanceFollowRedirects does NOT handle automatically.
     *
     * When [resumeFrom] > 0 a Range header is sent (on every hop — only the final host
     * answers it). Returns null for HTTP 416 (range starts beyond the end of the resource,
     * i.e. the .part file is already complete).
     */
    private fun openConnectionFollowingRedirects(initialUrl: String, resumeFrom: Long): HttpURLConnection? {
        var current = URL(initialUrl)
        var redirects = 0
        while (true) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MolyEcho-Android")
                if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
            }
            val code = connection.responseCode
            if (code in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                    ?: throw IOException("HTTP $code ohne Location-Header")
                connection.disconnect()
                if (++redirects > MAX_REDIRECTS) throw IOException("Zu viele Weiterleitungen")
                current = URL(current, location) // resolves relative + absolute targets
                continue
            }
            if (code == 416) {
                connection.disconnect()
                return null
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                connection.disconnect()
                throw IOException("HTTP $code")
            }
            return connection
        }
    }

    private fun formatMb(bytes: Long): String = String.format("%.2f MB", bytes / 1024.0 / 1024.0)

    actual fun isNetworkMetered(): Boolean {
        val cm = mainContext.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.isActiveNetworkMetered
    }

    actual fun notifyDownloadComplete() {
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

    companion object {
        private const val LOG_TAG = "MolyDownload"
        private const val CHANNEL_DOWNLOAD_DONE_ID = "download_done_channel"
        private const val NOTIFICATION_DOWNLOAD_DONE_ID = 4
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val PROGRESS_THROTTLE_MS = 500L
        private const val NOTIFICATION_THROTTLE_MS = 1_000L
        private val REDIRECT_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,  // 301
            HttpURLConnection.HTTP_MOVED_TEMP,  // 302
            HttpURLConnection.HTTP_SEE_OTHER,   // 303
            307,                                // Temporary Redirect
            308                                 // Permanent Redirect
        )
    }
}
