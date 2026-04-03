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

    suspend fun transcribeData(data: FloatArray, sampleRate: Int = 16000): String =
        withContext(scope.coroutineContext) {
            try {
                val stream = recognizer.createStream()
                stream.acceptWaveform(data, sampleRate)
                recognizer.decode(stream)
                val result = recognizer.getResult(stream)
                stream.release()
                result.text.trim()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error during ONNX transcription", e)
                ""
            }
        }

    // No-op: stopping is handled at chunk-loop level in Transcriber via isTranscribing flag.
    fun stopTranscription() = Unit

    suspend fun release() = withContext(scope.coroutineContext) {
        try {
            recognizer.release()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error releasing ONNX recognizer", e)
        }
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
