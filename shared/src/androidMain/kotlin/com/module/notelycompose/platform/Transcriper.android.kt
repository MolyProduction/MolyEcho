package com.module.notelycompose.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import audio.utils.LauncherHolder
import com.module.notelycompose.core.debugPrintln
import com.module.notelycompose.utils.SilenceAnalyzer
import com.module.notelycompose.utils.StreamingAudioChunker
import com.module.notelycompose.utils.StreamingAudioChunk
import com.module.notelycompose.utils.ChunkTranscriptionResult
import com.module.notelycompose.utils.isSilentChunk
import com.module.notelycompose.modelDownloader.ModelFormat
import com.whispercpp.whisper.SherpaWhisperContext
import com.whispercpp.whisper.WhisperCallback
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

actual class Transcriber(
    private val context: Context,
    private val launcherHolder: LauncherHolder
) {
    private var canTranscribe: Boolean = false
    @Volatile private var isTranscribing = false
    private val modelsPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    private var whisperContext: WhisperContext? = null
    private var sherpaContext: SherpaWhisperContext? = null
    // @Volatile matches the same risk accepted for currentLoadedModelName:
    // writes to currentLoadedModelFormat are not atomic with currentLoadedModelName,
    // but inactivity-timer cleanup is best-effort and this pair is only read
    // inside the modelLoadMutex where it matters for correctness.
    @Volatile private var currentLoadedModelFormat: ModelFormat? = null
    private var permissionContinuation: ((Boolean) -> Unit)? = null
    private val streamingChunker = StreamingAudioChunker()
    @Volatile private var currentLoadedModelName: String? = null
    private val inactivityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var inactivityJob: Job? = null

    // Serializes model loading and finish() so finish() always waits until the JNI
    // call completes before releasing the context and stopping the service.
    private val modelLoadMutex = Mutex()

    // Monotonically increasing counter. Incremented at the top of every initialize() call.
    // ViewModels capture the value right after initialize() returns and pass it back to
    // finish(). The mutex block in finish() compares the passed token against the current
    // value: if they differ, a newer session has already started and this finish() belongs
    // to a stale ViewModel — the model must not be released.
    private val sessionCounter = AtomicLong(0L)
    actual val currentSessionToken: Long get() = sessionCounter.get()

    companion object {
        private const val LOG_TAG = "Transcriber"
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L // 10 Minuten
        val ONNX_REQUIRED_FILES = listOf(
            SherpaWhisperContext.ENCODER_FILE,
            SherpaWhisperContext.DECODER_FILE,
            SherpaWhisperContext.TOKENS_FILE
        )
    }


    actual fun hasRecordingPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }


    actual suspend fun requestRecordingPermission(): Boolean {
        if (hasRecordingPermission()) {
            return true
        }

        return suspendCancellableCoroutine { continuation ->
            permissionContinuation = { isGranted ->
                continuation.resume(isGranted)
            }

            if (launcherHolder.permissionLauncher != null) {
                launcherHolder.permissionLauncher?.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            } else {
                continuation.resume(false)
            }

            continuation.invokeOnCancellation {
                permissionContinuation = null
            }
        }
    }


    actual suspend fun initialize(modelFileName: String, modelFormat: ModelFormat) {
        // Bump the session counter first — before the fast-path check. This ensures that a
        // finish() from the previous ViewModel (which captured the old token) will see a
        // mismatching token and skip the release, even when both sessions use the same model.
        sessionCounter.incrementAndGet()

        if (currentLoadedModelName == modelFileName && currentLoadedModelFormat == modelFormat
            && (whisperContext != null || sherpaContext != null)) {
            debugPrintln { "speech: model $modelFileName already loaded, skipping re-init" }
            if (!isTranscribing) canTranscribe = true
            resetInactivityTimer()
            return
        }
        cancelInactivityTimer()
        debugPrintln { "speech: initialize model $modelFileName (format=$modelFormat)" }
        whisperContext?.release()
        whisperContext = null
        sherpaContext?.release()
        sherpaContext = null
        currentLoadedModelName = null
        currentLoadedModelFormat = null
        canTranscribe = false
        loadBaseModel(modelFileName, modelFormat)
        if (whisperContext != null || sherpaContext != null) resetInactivityTimer()
    }

    private suspend fun loadBaseModel(modelFileName: String, modelFormat: ModelFormat) {
        modelLoadMutex.withLock {
            try {
                debugPrintln { "Loading model: $modelFileName (format=$modelFormat)" }
                val targetDir = modelsPath ?: run {
                    debugPrintln { "External storage unavailable — cannot load $modelFileName" }
                    return@withLock
                }

                if (modelFormat == ModelFormat.ONNX) {
                    val modelDir = File(targetDir, modelFileName)
                    if (!modelDir.isDirectory) {
                        Log.e(LOG_TAG, "ONNX model directory not found: ${modelDir.absolutePath}")
                        debugPrintln { "ONNX model directory not found: ${modelDir.absolutePath}" }
                        return@withLock
                    }
                    // Verify all required files exist before calling into JNI
                    val missingFiles = ONNX_REQUIRED_FILES.filter { !File(modelDir, it).exists() }
                    if (missingFiles.isNotEmpty()) {
                        Log.e(LOG_TAG, "ONNX model files missing in ${modelDir.absolutePath}: $missingFiles")
                        return@withLock
                    }
                    sherpaContext = SherpaWhisperContext.createContext(modelDir.absolutePath)
                } else {
                    val modelFile = File(targetDir, modelFileName)
                    if (!modelFile.exists()) extractFromAssets(modelFileName, modelFile)
                    whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
                }

                canTranscribe = true
                currentLoadedModelName = modelFileName
                currentLoadedModelFormat = modelFormat
            } catch (e: OutOfMemoryError) {
                Log.e(LOG_TAG, "OutOfMemoryError loading model $modelFileName", e)
                e.printStackTrace()
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "Failed to load model $modelFileName (${e.javaClass.simpleName}): ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    private fun extractFromAssets(modelFileName: String, targetFile: File) {
        try {
            context.assets.open(modelFileName).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            debugPrintln { "Extracted bundled model $modelFileName from assets" }
        } catch (e: Exception) {
            // Not a bundled model, ignore
        }
    }

    actual fun doesModelExists(modelFileName: String): Boolean {
        val target = modelsPath?.let { File(it, modelFileName) }
        return when {
            target == null -> false
            target.isDirectory -> ONNX_REQUIRED_FILES.all { File(target, it).exists() }
            target.exists() -> true
            else -> try { context.assets.open(modelFileName).use { true } } catch (e: Exception) { false }
        }
    }

    actual fun deleteModel(modelFileName: String): Boolean {
        val target = modelsPath?.let { File(it, modelFileName) } ?: return false
        return when {
            target.isDirectory -> target.deleteRecursively()
            target.exists() -> target.delete()
            else -> false
        }
    }

    actual fun getModelFileSizeBytes(modelFileName: String): Long {
        val target = modelsPath?.let { File(it, modelFileName) } ?: return 0L
        return when {
            target.isDirectory -> target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            target.exists() -> target.length()
            else -> 0L
        }
    }

    actual fun getAudioDurationSeconds(filePath: String): Int {
        return try {
            val file = java.io.RandomAccessFile(filePath, "r")
            val header = ByteArray(44)
            file.read(header)
            file.close()
            val buffer = java.nio.ByteBuffer.wrap(header)
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val channels = buffer.getShort(22).toInt()
            val sampleRate = buffer.getInt(24)
            val bitsPerSample = buffer.getShort(34).toInt()
            val dataSize = buffer.getInt(40).toLong() and 0xFFFFFFFFL
            if (sampleRate <= 0 || channels <= 0 || bitsPerSample <= 0) return 0
            (dataSize / (sampleRate * channels * (bitsPerSample / 8.0))).toInt()
        } catch (e: Exception) {
            0
        }
    }

    actual fun isValidModel(modelFileName: String): Boolean {
        val target = modelsPath?.let { File(it, modelFileName) } ?: return false
        return when {
            target.isDirectory -> ONNX_REQUIRED_FILES.all { File(target, it).exists() }
            target.exists() -> target.length() > 0
            else -> try { context.assets.open(modelFileName).use { true } } catch (e: Exception) { false }
        }
    }

    actual suspend fun stop() {
        isTranscribing = false
        whisperContext?.stopTranscription()
        sherpaContext?.stopTranscription()
    }

    actual suspend fun finish(sessionToken: Long) {
        cancelInactivityTimer()
        // Wait for any in-progress loadBaseModel() JNI call to complete before attempting
        // to release the context. This prevents the foreground service from being stopped
        // while the JNI thread is still executing.
        modelLoadMutex.withLock {
            // Guard: only release if the session counter still matches the token this
            // finish() was called with. A mismatch means a newer initialize() has already
            // bumped the counter, so this finish() belongs to a stale ViewModel — leave
            // the new session's model and state intact.
            if (sessionCounter.get() == sessionToken) {
                whisperContext?.release()
                whisperContext = null
                sherpaContext?.release()
                sherpaContext = null
                currentLoadedModelName = null
                currentLoadedModelFormat = null
                canTranscribe = false
            }
        }
    }

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        // Token zum Zeitpunkt des Timer-Starts. Nach dem delay() wird geprüft ob er
        // noch aktuell ist — ein zwischenzeitliches initialize() erhöht den Counter
        // und verhindert so das Freisetzen des neuen Modells.
        val tokenAtReset = sessionCounter.get()
        inactivityJob = inactivityScope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            modelLoadMutex.withLock {
                if (sessionCounter.get() != tokenAtReset) return@withLock
                debugPrintln { "speech: inactivity timeout — releasing model $currentLoadedModelName" }
                whisperContext?.release()
                whisperContext = null
                sherpaContext?.release()
                sherpaContext = null
                currentLoadedModelFormat = null
                currentLoadedModelName = null
                canTranscribe = false
            }
        }
    }

    private fun cancelInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = null
    }

    actual suspend fun start(
        filePath: String, language: String,
        onProgress : (Int) -> Unit,
        onNewSegment : (Long, Long,String) -> Unit,
        onComplete : () -> Unit,
        onError : () -> Unit
    ) {
        if (!canTranscribe) {
            Log.e(LOG_TAG, "start() called but model is not ready. " +
                "model=$currentLoadedModelName format=$currentLoadedModelFormat " +
                "whisperCtx=${whisperContext != null} sherpaCtx=${sherpaContext != null}")
            onError()
            return
        }

        canTranscribe = false
        isTranscribing = true
        cancelInactivityTimer()

        try {
            debugPrintln{"Reading WAV file chunks directly from disk...\n"}

            // Split WAV file into streaming chunks without loading entire file into memory.
            // ONNX: Whisper's offline decoder has a hard 30-second receptive window (480,000
            // samples at 16 kHz). Passing more audio to acceptWaveform() silently truncates
            // at 30 s. Use exact 30 s chunks with no overlap (overlap would duplicate words
            // because sherpa-onnx has no initialPrompt support between chunks).
            // GGML: whisper.cpp handles arbitrary-length audio internally, so the default
            // 10 MB chunks (~5.5 min) are fine.
            val streamingChunks = if (currentLoadedModelFormat == ModelFormat.ONNX) {
                val onnxChunkBytes = 30 * 16_000 * 2 // 30 s × 16 kHz × 2 bytes (16-bit mono)
                val rawChunks = streamingChunker.splitWavFileIntoChunks(
                    filePath,
                    chunkSizeBytes = onnxChunkBytes,
                    overlapSizeBytes = 0
                )
                val silenceAnalysis = SilenceAnalyzer.analyze(filePath)
                if (silenceAnalysis.shouldApplyVad) {
                    val filtered = rawChunks.filterNot { it.isSilentChunk(silenceAnalysis) }.toMutableList()
                    debugPrintln { "VAD: skipped ${rawChunks.size - filtered.size}/${rawChunks.size} silent chunks" }
                    filtered
                } else {
                    rawChunks
                }
            } else {
                // GGML: unverändert, kein VAD
                streamingChunker.splitWavFileIntoChunks(filePath)
            }
            debugPrintln{"Processing ${streamingChunks.size} streaming chunks...\n"}

            val start = System.currentTimeMillis()
            val chunkResults = mutableListOf<ChunkTranscriptionResult>()
            var completedChunks = 0
            var previousChunkPrompt: String? = null

            streamingChunks.forEachIndexed { chunkIndex, streamingChunk ->
                if (!isTranscribing) {
                    debugPrintln{"Transcription stopped by user"}
                    return@forEachIndexed
                }

                debugPrintln{"Processing streaming chunk ${chunkIndex + 1}/${streamingChunks.size} (${streamingChunk.durationSeconds}s)"}

                val chunkSegments = mutableListOf<com.module.notelycompose.utils.TranscriptionSegment>()
                var chunkText = ""

                try {
                    // Read chunk data directly from file (using reusable arrays)
                    val chunkData = streamingChunker.readChunkData(streamingChunk)
                    debugPrintln{"Transcription: Read ${chunkData.size} samples from chunk $chunkIndex (reusable array)"}

                    // Update progress to show chunk is starting
                    val chunkProgress = 100.0 / streamingChunks.size
                    val startProgress = (completedChunks * chunkProgress).toInt().coerceIn(0, 100)
                    onProgress(startProgress)

                    if (currentLoadedModelFormat == ModelFormat.ONNX) {
                        // ONNX path: synchronous, no segment-level callbacks
                        val text = sherpaContext?.transcribeData(chunkData) ?: ""
                        chunkText = text

                        if (text.isNotBlank()) {
                            val chunkStartMs = ((streamingChunk.startOffset - 44).toDouble() /
                                (streamingChunk.header.sampleRate * streamingChunk.header.channels *
                                 (streamingChunk.header.bitsPerSample / 8.0) / 1000.0)).toLong()
                            val chunkEndMs = ((streamingChunk.endOffset - 44).toDouble() /
                                (streamingChunk.header.sampleRate * streamingChunk.header.channels *
                                 (streamingChunk.header.bitsPerSample / 8.0) / 1000.0)).toLong()

                            chunkSegments.add(com.module.notelycompose.utils.TranscriptionSegment(
                                chunkStartMs, chunkEndMs, text
                            ))
                            onNewSegment(chunkStartMs, chunkEndMs, text)
                        }

                        completedChunks++
                        val overallProgress = (completedChunks * 100.0 / streamingChunks.size).toInt().coerceIn(0, 100)
                        onProgress(overallProgress)

                        // Note: sherpa-onnx does not support initial prompts between chunks.
                        // previousChunkPrompt is not used for the ONNX path.

                    } else {
                        // GGML path (original whisperContext code)
                        val result = whisperContext?.transcribeData(
                            chunkData,
                            language,
                            initialPrompt = previousChunkPrompt,
                            callback = object : WhisperCallback {
                            override fun onNewSegment(startMs: Long, endMs: Long, text: String) {
                                // Adjust timing to account for chunk position in original audio
                                val chunkStartTimeMs = (streamingChunk.startOffset - 44) / (streamingChunk.header.sampleRate * streamingChunk.header.channels * (streamingChunk.header.bitsPerSample / 8.0) / 1000.0)
                                val adjustedStartMs = startMs + chunkStartTimeMs.toLong()
                                val adjustedEndMs = endMs + chunkStartTimeMs.toLong()

                                chunkSegments.add(com.module.notelycompose.utils.TranscriptionSegment(
                                    adjustedStartMs, adjustedEndMs, text
                                ))

                                // Call the original callback with adjusted timing
                                onNewSegment(adjustedStartMs, adjustedEndMs, text)
                            }

                            override fun onProgress(progress: Int) {
                                // Simple chunk-based progress: each chunk represents equal progress
                                val totalChunks = streamingChunks.size
                                val chunkProgress = 100.0 / totalChunks

                                // Progress = completed chunks + current chunk progress
                                val overallProgress = ((completedChunks * chunkProgress) + (progress * chunkProgress / 100.0)).toInt().coerceIn(0, 100)

                                debugPrintln{"Transcription: Chunk $chunkIndex progress: $progress%, Overall: $overallProgress%"}
                                onProgress(overallProgress)
                            }

                            override fun onComplete() {
                                // This will be called for each chunk
                                completedChunks++
                                debugPrintln{"Transcription: Transcription completed for chunk $chunkIndex (${completedChunks}/${streamingChunks.size} completed)"}
                            }
                        })

                        chunkText = result ?: ""

                        // Letzten Teil des Chunks als Prompt für nächsten Chunk speichern
                        val rawText = chunkSegments.joinToString(" ") { it.text.trim() }
                        previousChunkPrompt = if (rawText.length > 100) rawText.takeLast(100) else rawText.ifBlank { null }
                    }

                    // TODO(pre-existing): chunkResults is accumulated here but never consumed —
                    // the block below clears it immediately. This scaffolding is left intact
                    // to avoid changing pre-existing GGML behavior; remove when the merge logic is implemented.
                    val tempAudioChunk = com.module.notelycompose.utils.AudioChunk(
                        startSample = ((streamingChunk.startOffset - 44) / (streamingChunk.header.channels * (streamingChunk.header.bitsPerSample / 8))).toInt(),
                        endSample = ((streamingChunk.endOffset - 44) / (streamingChunk.header.channels * (streamingChunk.header.bitsPerSample / 8))).toInt(),
                        data = chunkData
                    )

                    chunkResults.add(ChunkTranscriptionResult(tempAudioChunk, chunkText, chunkSegments))

                    // Clear chunk data from memory after processing (reusable array)
                    chunkData.fill(0.0f)
                    debugPrintln{"Transcription: Cleared chunk $chunkIndex data from memory (${chunkData.size} samples, reusable array)"}

                } catch (e: Exception) {
                    debugPrintln{"Error processing streaming chunk $chunkIndex: ${e.localizedMessage}"}
                    e.printStackTrace()
                }
            }

            // Merge results from all chunks
            if (isTranscribing && chunkResults.isNotEmpty()) {

                // Clear chunk results from memory after merging
                chunkResults.clear()
                debugPrintln{"Transcription: Cleared all chunk results from memory"}
            }

            val elapsed = System.currentTimeMillis() - start
            debugPrintln{"Done ($elapsed ms)\n"}

            // Clear streaming chunks from memory
            streamingChunks.clear()
            debugPrintln{"Transcription: Cleared streaming chunks list from memory"}

            // Clear reusable arrays from memory
            streamingChunker.clearReusableArrays()
            val arraySizes = streamingChunker.getReusableArraySizes()
            debugPrintln{"Transcription: Cleared reusable arrays from memory (FloatArray: ${arraySizes.first}, ByteArray: ${arraySizes.second})"}

            if (isTranscribing) {
                onComplete()
            }

        } catch (e: OutOfMemoryError) {
            onError()
            e.printStackTrace()
            debugPrintln{"OutOfMemoryError: File too large to process - ${e.message}\n"}
        } catch (e: Exception) {
            onError()
            e.printStackTrace()
            debugPrintln{"${e.localizedMessage}\n"}
        }

        canTranscribe = true
        isTranscribing = false
        resetInactivityTimer() // Timer nach Transkription neu starten
    }
}
