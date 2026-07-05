package com.whispercpp.whisper

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val LOG_TAG = "SherpaWhisperContext"

class SherpaWhisperContext private constructor(
    private val recognizer: OfflineRecognizer
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(executor.asCoroutineDispatcher())

    /**
     * Transcribes one chunk. Errors are rethrown (not swallowed into an empty string) so the
     * caller can count failed chunks and inform the user about an incomplete transcript.
     */
    suspend fun transcribeData(data: FloatArray, sampleRate: Int = 16000): String =
        withContext(scope.coroutineContext) {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(data, sampleRate)
                recognizer.decode(stream)
                recognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        }

    // No-op: stopping is handled at chunk-loop level in Transcriber via isTranscribing flag.
    fun stopTranscription() = Unit

    suspend fun release() {
        withContext(scope.coroutineContext) {
            try {
                recognizer.release()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error releasing ONNX recognizer", e)
            }
        }
        // Shut down from OUTSIDE the executor's own thread. Calling awaitTermination from
        // within the executor task itself can never succeed (the running task blocks
        // termination) and used to stall every release() for the full timeout.
        executor.shutdown()
    }

    companion object {
        // File names produced by sherpa-onnx export-onnx.py --model large-v3-turbo
        const val ENCODER_FILE = "large-v3-turbo-encoder.int8.onnx"
        const val DECODER_FILE = "large-v3-turbo-decoder.int8.onnx"
        const val TOKENS_FILE = "large-v3-turbo-tokens.txt"

        fun createContext(modelDir: String): SherpaWhisperContext {
            val numThreads = WhisperCpuConfig.preferredThreadCount.coerceAtLeast(2)

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = 16000,
                    featureDim = 128  // large-v3-turbo uses 128 mel bins (not 80 which is for small/medium)
                ),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$modelDir/$ENCODER_FILE",
                        decoder = "$modelDir/$DECODER_FILE",
                        language = "de",
                        task = "transcribe",
                        tailPaddings = -1  // -1 = use sherpa-onnx default (no explicit tail padding)
                    ),
                    tokens = "$modelDir/$TOKENS_FILE",
                    numThreads = numThreads,
                    provider = "cpu",
                    debug = false
                ),
                decodingMethod = "greedy_search"
            )
            // AssetManager is only needed when loading from Android assets.
            // We use absolute file paths, so pass null.
            val recognizer = OfflineRecognizer(null, config)
            return SherpaWhisperContext(recognizer)
        }
    }
}
