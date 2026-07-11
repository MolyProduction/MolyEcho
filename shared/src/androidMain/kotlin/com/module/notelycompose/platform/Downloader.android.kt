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
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference

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
 *
 * Große Dateien (≥ 2×32 MB) mit Range-Unterstützung (Probe-Antwort 206) werden parallel in
 * bis zu [SegmentPlan.MAX_SEGMENTS] Segmenten geladen — Einzelverbindungen sind auf WLAN
 * bzw. beim CDN oft pro Verbindung limitiert. Der Fortschritt je Segment liegt in einer
 * `.smeta`-Sidecar-Datei für Resume. Invariante: die `.smeta` wird VOR dem Vorallozieren
 * der `.part` geschrieben und erst nach validiertem Abschluss gelöscht — eine `.part` ohne
 * `.smeta` ist deshalb immer sequenzieller Fortschritt (oder fertig) und der bestehende
 * Legacy-Resume-Pfad bleibt korrekt.
 *
 * DNS-Fallback: liefert das lokale DNS für einen Host keine (oder nur Null-Route-)Adressen —
 * beobachtet bei Router-Filtern für `*.hf.co` — werden die IPs per DNS-over-HTTPS
 * ([DohResolver]) geholt und die Verbindung über [DirectHttpsClient] mit voller
 * TLS-Validierung gegen den Original-Hostnamen aufgebaut.
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
                debugPrintln { "Download: startDownload ignoriert, Session aktiv für ${current.fileName}" }
                return
            }
            newSession = Session(url, fileName)
            session = newSession
        }
        debugPrintln { "Download: start url=$url file=$fileName" }
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

    private suspend fun runDownload(s: Session) {
        val dest = File(mainContext.filesDir, "models/${s.fileName}")
        val part = File(dest.parentFile, dest.name + ".part")
        val meta = File(dest.parentFile, dest.name + ".smeta")
        var result: DownloadResult
        try {
            dest.parentFile?.mkdirs()
            if (dest.exists()) {
                // The rename below only ever happens after a size-validated transfer, so an
                // existing destination is a complete file — skip the download (relevant for
                // multi-file retries: already-finished files are not downloaded again).
                debugPrintln { "Download: Datei bereits vollständig, überspringe ${s.fileName}" }
                s.progress.value = ProgressSnapshot(100, formatMb(dest.length()), formatMb(dest.length()))
                result = DownloadResult.Success
            } else {
                ModelDownloadService.start(mainContext)
                try {
                    result = transfer(s, part, meta)
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
                    debugPrintln { "Download: fertig file=${s.fileName} size=${dest.length()}" }
                }
            }
        } catch (e: Exception) {
            debugPrintln { "Download: Fehler file=${s.fileName}: ${e.message}" }
            result = DownloadResult.Failed(e.message ?: mainContext.getString(R.string.download_error_generic))
        }
        when (result) {
            is DownloadResult.Cancelled -> debugPrintln { "Download: abgebrochen file=${s.fileName}" }
            is DownloadResult.Failed -> debugPrintln { "Download: fehlgeschlagen file=${s.fileName} grund=${result.message}" }
            else -> {}
        }
        s.result.complete(result)
    }

    /**
     * Weiche zwischen den Transferpfaden:
     * 1. Gültige `.smeta` → segmentierten Download fortsetzen.
     * 2. `.part` ohne `.smeta` → sequenzieller Legacy-Resume (Range ab Dateiende).
     * 3. Frisch: Probe ab Byte 0 — antwortet der Server mit 206 und lohnt sich die Größe,
     *    parallel in Segmenten laden, sonst sequenziell (die Probe-Verbindung wird dabei
     *    direkt weiterverwendet, kein zweiter Request).
     */
    private suspend fun transfer(s: Session, part: File, meta: File): DownloadResult {
        if (meta.exists()) {
            val parsed = SegmentPlan.parseMeta(runCatching { meta.readText() }.getOrDefault(""))
            if (parsed != null && part.exists() && part.length() == parsed.total) {
                debugPrintln { "Download: segmentierter Resume, done=${parsed.done.sum()}/${parsed.total}" }
                return segmentedTransfer(s, part, meta, parsed.total, parsed.done)
            }
            // Meta defekt oder passt nicht zur .part — fail-safe komplett neu anfangen.
            debugPrintln { "Download: .smeta inkonsistent, starte frisch" }
            meta.delete()
            part.delete()
        }
        if (part.exists()) {
            return sequentialTransfer(s, part, resumeFrom = part.length())
        }

        var probe: HttpSource? = null
        try {
            probe = openFollowingRedirects(s.url, 0) ?: return DownloadResult.Success
            val total = probe.contentLength
            val segments =
                if (probe.code == HttpURLConnection.HTTP_PARTIAL) SegmentPlan.plan(total)
                else emptyList()
            if (segments.size > 1) {
                debugPrintln { "Download: parallel n=${segments.size} total=$total file=${s.fileName}" }
                // Reihenfolge sichert die Invariante: erst .smeta, dann vorallozieren.
                meta.writeText(SegmentPlan.encodeMeta(total, LongArray(segments.size)))
                RandomAccessFile(part, "rw").use { it.setLength(total) }
                val segment0Source = probe
                probe = null // Ownership geht an segmentedTransfer (Segment 0)
                return segmentedTransfer(
                    s, part, meta, total, LongArray(segments.size), segment0Source
                )
            }
            val preOpened = probe
            probe = null // Ownership geht an sequentialTransfer
            return sequentialTransfer(s, part, resumeFrom = 0, preOpened = preOpened)
        } catch (e: IOException) {
            return DownloadResult.Failed(e.message ?: mainContext.getString(R.string.download_error_network))
        } finally {
            probe?.close()
        }
    }

    /**
     * Lädt alle noch offenen Segmente parallel in dieselbe vorallozierte [part]-Datei
     * (je Segment ein eigenes RandomAccessFile mit seek auf den Segment-Offset).
     * Fehler und Abbruch stoppen alle Segmente kooperativ; der Stand wandert für den
     * nächsten Resume in die [meta]-Datei. Erst nach validiertem Gesamtabschluss wird
     * die Meta gelöscht — danach greift beim Aufrufer der übliche Rename.
     */
    private suspend fun segmentedTransfer(
        s: Session,
        part: File,
        meta: File,
        total: Long,
        initialDone: LongArray,
        segment0Source: HttpSource? = null
    ): DownloadResult {
        val segments = SegmentPlan.plan(total)
        val done = AtomicLongArray(initialDone)
        val abortMessage = AtomicReference<String?>(null)
        val progressLock = Any()
        var lastPct = -1
        var lastNotificationMs = 0L
        var lastMetaMs = System.currentTimeMillis()

        fun snapshotDone() = LongArray(segments.size) { done.get(it) }

        fun persistMeta() = synchronized(progressLock) {
            runCatching { meta.writeText(SegmentPlan.encodeMeta(total, snapshotDone())) }
        }

        fun reportProgress() {
            val sum = (0 until segments.size).sumOf { done.get(it) }
            synchronized(progressLock) {
                val pct = (sum * 100L / total).toInt()
                if (pct != lastPct) {
                    lastPct = pct
                    s.progress.value = ProgressSnapshot(pct, formatMb(sum), formatMb(total))
                }
                val now = System.currentTimeMillis()
                if (now - lastNotificationMs >= NOTIFICATION_THROTTLE_MS) {
                    lastNotificationMs = now
                    ModelDownloadService.progress(
                        mainContext, pct, "${formatMb(sum)} / ${formatMb(total)}"
                    )
                }
                if (now - lastMetaMs >= META_FLUSH_MS) {
                    lastMetaMs = now
                    runCatching { meta.writeText(SegmentPlan.encodeMeta(total, snapshotDone())) }
                }
            }
        }

        coroutineScope {
            for (seg in segments) {
                launch(Dispatchers.IO) {
                    var source: HttpSource? = null
                    try {
                        val already = done.get(seg.index)
                        if (already >= seg.size) return@launch // Segment war schon fertig
                        source = if (seg.index == 0 && segment0Source != null && already == 0L) {
                            segment0Source
                        } else {
                            openFollowingRedirects(s.url, seg.start + already, rangeEnd = seg.end - 1)
                                ?: throw IOException("HTTP 416 für Segment ${seg.index}")
                        }
                        if (seg.start + already > 0 && source.code != HttpURLConnection.HTTP_PARTIAL) {
                            // Antwort beginnt bei Byte 0 — an diesem Offset zu schreiben
                            // würde die Datei korrumpieren.
                            throw IOException("Server ignoriert Range (HTTP ${source.code})")
                        }
                        RandomAccessFile(part, "rw").use { raf ->
                            raf.seek(seg.start + already)
                            val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
                            var remaining = seg.size - already
                            val input = source.body()
                            while (remaining > 0) {
                                if (s.cancelRequested || abortMessage.get() != null) return@launch
                                val read = input.read(
                                    buffer, 0, minOf(buffer.size.toLong(), remaining).toInt()
                                )
                                if (read < 0) {
                                    throw IOException("Verbindung endete vor Segmentende")
                                }
                                raf.write(buffer, 0, read)
                                remaining -= read
                                done.addAndGet(seg.index, read.toLong())
                                reportProgress()
                            }
                        }
                    } catch (e: Exception) {
                        // Auch Nicht-IOExceptions kooperativ einsammeln, damit kein Segment
                        // per Strukturabbruch hängen bleibt (blockierende Reads suspendieren nie).
                        debugPrintln { "Download: Segment ${seg.index} fehlgeschlagen: ${e.message}" }
                        abortMessage.compareAndSet(
                            null, e.message ?: mainContext.getString(R.string.download_error_network)
                        )
                    } finally {
                        source?.close()
                    }
                }
            }
        }
        persistMeta()

        if (s.cancelRequested) return DownloadResult.Cancelled
        abortMessage.get()?.let { return DownloadResult.Failed(it) }
        val sum = snapshotDone().sum()
        if (sum != total) {
            return DownloadResult.Failed(
                mainContext.getString(R.string.download_error_incomplete, formatMb(sum), formatMb(total))
            )
        }
        meta.delete()
        return DownloadResult.Success
    }

    private fun sequentialTransfer(
        s: Session,
        part: File,
        resumeFrom: Long,
        preOpened: HttpSource? = null
    ): DownloadResult {
        var source: HttpSource? = preOpened
        try {
            if (source == null) {
                source = openFollowingRedirects(s.url, resumeFrom)
                if (source == null) {
                    // HTTP 416: our resume offset is at/after the end of the resource — the
                    // .part file already contains all bytes of a previous, almost-complete run.
                    debugPrintln { "Download: Resume-Offset hinter Ressourcen-Ende, .part ist komplett" }
                    return DownloadResult.Success
                }
            }
            val append = source.code == HttpURLConnection.HTTP_PARTIAL
            val alreadyDownloaded = if (append) resumeFrom else 0L
            val remaining = source.contentLength // -1 if the server omits Content-Length
            val total = if (remaining >= 0) alreadyDownloaded + remaining else -1L
            debugPrintln { "Download: verbunden resumeFrom=$resumeFrom append=$append total=$total url=${s.url}" }

            var downloaded = alreadyDownloaded
            source.body().use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
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
            source?.close()
        }
    }

    /**
     * Opens a connection and follows redirects manually — including cross-protocol ones
     * (HuggingFace redirects huggingface.co → its signed CDN host), which
     * HttpURLConnection.setInstanceFollowRedirects does NOT handle automatically.
     *
     * A Range header is sent on every hop (only the final host answers it); with
     * resumeFrom = 0 it doubles as the probe for range support (206 vs. 200). [rangeEnd]
     * (inclusive, -1 = open-ended) bounds a segment request. Returns null for HTTP 416
     * (range starts beyond the end of the resource, i.e. the .part file is already
     * complete).
     */
    private fun openFollowingRedirects(
        initialUrl: String,
        resumeFrom: Long,
        rangeEnd: Long = -1L
    ): HttpSource? {
        var current = URL(initialUrl)
        var redirects = 0
        while (true) {
            val source = openSingle(current, resumeFrom, rangeEnd)
            val code = source.code
            if (code in REDIRECT_CODES) {
                val location = source.header("Location")
                    ?: run { source.close(); throw IOException("HTTP $code ohne Location-Header") }
                source.close()
                if (++redirects > MAX_REDIRECTS) throw IOException("Zu viele Weiterleitungen")
                current = URL(current, location) // resolves relative + absolute targets
                continue
            }
            if (code == 416) {
                source.close()
                return null
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                source.close()
                throw IOException("HTTP $code")
            }
            return source
        }
    }

    /**
     * Opens ONE hop. Normally via HttpURLConnection; if the local DNS cannot resolve the
     * host (or only returns null routes like 0.0.0.0 — sinkholing routers), the host is
     * resolved via DNS-over-HTTPS and connected directly with full TLS validation.
     */
    private fun openSingle(url: URL, resumeFrom: Long, rangeEnd: Long): HttpSource {
        if (!isSystemDnsBroken(url.host)) {
            try {
                val connection = openConnection(url, resumeFrom, rangeEnd)
                connection.responseCode // erzwingt den Verbindungsaufbau innerhalb des try
                return UrlConnectionSource(connection)
            } catch (e: UnknownHostException) {
                debugPrintln { "Download: Systemauflösung für ${url.host} fehlgeschlagen, versuche DoH" }
            }
        } else {
            debugPrintln { "Download: lokales DNS für ${url.host} defekt/gefiltert (Null-Route), DoH-Fallback" }
        }

        val ips = DohResolver.resolve(url.host)
        if (ips.isEmpty()) throw UnknownHostException("${url.host} (auch via DoH nicht auflösbar)")
        var lastError: IOException? = null
        for (ip in ips.take(MAX_DOH_IPS)) {
            try {
                return DirectHttpsClient.get(url, ip, resumeFrom, rangeEnd)
            } catch (e: IOException) {
                debugPrintln { "Download: DoH-Verbindung über $ip fehlgeschlagen: ${e.message}" }
                lastError = e
            }
        }
        throw lastError ?: IOException("DoH-Fallback für ${url.host} fehlgeschlagen")
    }

    private fun openConnection(url: URL, resumeFrom: Long, rangeEnd: Long): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MolyEcho-Android")
            // identity: kein transparentes gzip — Content-Length bleibt für Progress,
            // Größenvalidierung und Range-Arithmetik verlässlich.
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Range", "bytes=$resumeFrom-${if (rangeEnd >= 0) "$rangeEnd" else ""}")
        }

    /** true = Host löst nicht auf oder liefert ausschließlich Null-Routen/Loopback. */
    private fun isSystemDnsBroken(host: String): Boolean = try {
        InetAddress.getAllByName(host).all { it.isAnyLocalAddress || it.isLoopbackAddress }
    } catch (e: UnknownHostException) {
        true
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
        private const val CHANNEL_DOWNLOAD_DONE_ID = "download_done_channel"
        private const val NOTIFICATION_DOWNLOAD_DONE_ID = 4
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val MAX_DOH_IPS = 3
        private const val PROGRESS_THROTTLE_MS = 500L
        private const val NOTIFICATION_THROTTLE_MS = 1_000L
        /** 256 KB statt 8-KB-Default: weniger Syscalls/JNI-Übergänge pro übertragenem GB. */
        private const val TRANSFER_BUFFER_BYTES = 256 * 1024
        /** Wie oft der Segment-Fortschritt für Resume in die .smeta geschrieben wird. */
        private const val META_FLUSH_MS = 2_000L
        private val REDIRECT_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,  // 301
            HttpURLConnection.HTTP_MOVED_TEMP,  // 302
            HttpURLConnection.HTTP_SEE_OTHER,   // 303
            307,                                // Temporary Redirect
            308                                 // Permanent Redirect
        )
    }
}
