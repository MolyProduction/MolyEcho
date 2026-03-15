package com.module.notelycompose.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import audio.utils.LauncherHolder
import com.module.notelycompose.core.debugPrintln
import com.module.notelycompose.utils.StreamingAudioChunker
import com.module.notelycompose.utils.StreamingAudioChunk
import com.module.notelycompose.utils.ChunkTranscriptionResult
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
import kotlin.coroutines.resume

actual class Transcriber(
    private val context: Context,
    private val launcherHolder: LauncherHolder
) {
    private var canTranscribe: Boolean = false
    private var isTranscribing = false
    private val modelsPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    private var whisperContext: WhisperContext? = null
    private var permissionContinuation: ((Boolean) -> Unit)? = null
    private val streamingChunker = StreamingAudioChunker()
    @Volatile private var currentLoadedModelName: String? = null
    private val inactivityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inactivityJob: Job? = null

    // Serializes model loading and finish() so finish() always waits until the JNI
    // call completes before releasing the context and stopping the service.
    private val modelLoadMutex = Mutex()

    // Set to true by finish() so loadBaseModel() can self-cancel after JNI returns.
    @Volatile private var isCancelled = false

    companion object {
        private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L // 10 Minuten
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


    actual suspend fun initialize(modelFileName: String) {
        if (currentLoadedModelName == modelFileName && whisperContext != null) {
            debugPrintln { "speech: model $modelFileName already loaded, skipping re-init" }
            // Ensure canTranscribe is true whenever the model is confirmed loaded.
            // Without this, a previous early-return from start() (canTranscribe = false
            // path) would leave canTranscribe stuck as false even though the model is
            // ready, causing every subsequent transcription attempt to fail immediately.
            if (!isTranscribing) {
                canTranscribe = true
            }
            resetInactivityTimer()
            return
        }
        cancelInactivityTimer()
        debugPrintln { "speech: initialize model $modelFileName" }
        whisperContext?.release()
        whisperContext = null
        currentLoadedModelName = null
        canTranscribe = false
        loadBaseModel(modelFileName)
        if (isCancelled) return
        if (whisperContext != null) {
            resetInactivityTimer()
        }
    }

    private suspend fun loadBaseModel(modelFileName: String) {
        modelLoadMutex.withLock {
            // Reset isCancelled inside the lock so a stale finish() from a previous
            // session (which sets isCancelled = true before acquiring the lock) cannot
            // abort a new loading request. The post-JNI check below still catches a
            // finish() that fires *during* the long-running JNI call.
            isCancelled = false
            try {
                debugPrintln { "Loading model: $modelFileName\n" }
                val targetDir = modelsPath ?: run {
                    debugPrintln { "External storage unavailable — cannot load model $modelFileName" }
                    return@withLock
                }
                val modelFile = File(targetDir, modelFileName)
                if (!modelFile.exists()) {
                    extractFromAssets(modelFileName, modelFile)
                }
                whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
                if (isCancelled) {
                    // finish() was called while JNI ran – release immediately
                    whisperContext?.release()
                    whisperContext = null
                    return@withLock
                }
                canTranscribe = true
                currentLoadedModelName = modelFileName
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
            } catch (e: Throwable) {
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

    actual fun doesModelExists(modelFileName: String) : Boolean{
        val modelFile = modelsPath?.let { File(it, modelFileName) }
        if (modelFile?.exists() == true) return true
        return try {
            context.assets.open(modelFileName).use { true }
        } catch (e: Exception) {
            false
        }
    }

    actual fun deleteModel(modelFileName: String): Boolean {
        val modelFile = modelsPath?.let { File(it, modelFileName) } ?: return false
        return if (modelFile.exists()) modelFile.delete() else false
    }

    actual fun getModelFileSizeBytes(modelFileName: String): Long {
        val modelFile = modelsPath?.let { File(it, modelFileName) } ?: return 0L
        return if (modelFile.exists()) modelFile.length() else 0L
    }

    actual fun isValidModel(modelFileName: String): Boolean {
        // Check on-disk file
        val modelFile = modelsPath?.let { File(it, modelFileName) }
        if (modelFile?.exists() == true && modelFile.length() > 0) return true
        // Check bundled asset
        return try {
            context.assets.open(modelFileName).use { true }
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun stop() {
        isTranscribing = false
        whisperContext?.stopTranscription()
    }

    actual suspend fun finish() {
        // Capture the model name before signalling cancellation. Inside the mutex we
        // compare against the current value: if a new session has already loaded a
        // different model (or started a fresh load that reset the name to null), this
        // finish() belongs to a stale ViewModel and must not destroy the new session.
        val modelAtFinish = currentLoadedModelName
        isCancelled = true
        cancelInactivityTimer()
        // Wait for any in-progress loadBaseModel() JNI call to complete before releasing
        // the context. This prevents the foreground service from being stopped while the
        // JNI thread is still executing.
        modelLoadMutex.withLock {
            if (currentLoadedModelName == modelAtFinish) {
                whisperContext?.release()
                whisperContext = null
                currentLoadedModelName = null
                canTranscribe = false
            }
            // If currentLoadedModelName differs, a new session loaded a different model
            // after this finish() was initiated — leave the new session's state intact.
        }
    }

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = inactivityScope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            debugPrintln { "speech: inactivity timeout — releasing model $currentLoadedModelName" }
            whisperContext?.release()
            whisperContext = null
            currentLoadedModelName = null
            canTranscribe = false
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
            onError()
            return
        }

        canTranscribe = false
        isTranscribing = true
        cancelInactivityTimer()

        try {
            debugPrintln{"Reading WAV file chunks directly from disk...\n"}

            // Split WAV file into streaming chunks without loading entire file into memory
            val streamingChunks = streamingChunker.splitWavFileIntoChunks(filePath)
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

                    // Create a temporary AudioChunk for compatibility with existing merge logic
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
