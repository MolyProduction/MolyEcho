package com.module.notelycompose.modelDownloader

import com.module.notelycompose.onboarding.data.PreferencesRepository
import kotlinx.coroutines.flow.first

const val NO_MODEL_SELECTION = -1
const val STANDARD_MODEL_SELECTION = 0
const val OPTIMIZED_MODEL_SELECTION = 1
const val MULTILINGUAL_EXTENDED_SELECTION = 3
const val MULTILINGUAL_MODEL = "en"
const val GERMAN_MODEL = "de"

enum class ModelFormat { GGML, ONNX }

/**
 * One file of a multi-file model. [sizeBytes] is the expected download size (from the
 * HuggingFace API) and is used for byte-weighted aggregate progress across files.
 */
data class DownloadFile(
    val fileName: String,
    val url: String,
    val sizeBytes: Long
)

data class TranscriptionModel(
    val name: String,
    val modelType: String,
    val size: String,
    val description: String,
    val url: String?,
    val format: ModelFormat = ModelFormat.GGML,
    /**
     * For ONNX models: list of files to download. Null for single-file GGML models.
     */
    val downloadFiles: List<DownloadFile>? = null
) {
    fun getModelDownloadSize(): String = size
    fun getModelDownloadType(): String = modelType

    /** True if the model needs to be downloaded (not a bundled asset). */
    val isDownloadRequired: Boolean
        get() = url != null || downloadFiles != null
}

class ModelSelection(private val preferencesRepository: PreferencesRepository) {

    /**
     * Index layout (stable — constants below depend on these positions):
     *   0  ggml-small.bin                      multilingual (465 MB, GGML)
     *   1  whisper-large-v3-turbo-german/       German "Schnell" (~990 MB, ONNX)
     *   2  ggml-large-v3-turbo-german.bin       German "Genau"  (1.62 GB, GGML)
     *
     * ONNX files for model 1 were exported via sherpa-onnx:
     * python export-onnx.py --model large-v3-turbo --checkpoint primeline/whisper-large-v3-turbo-german
     * sizeBytes values come from the HuggingFace API (repo MolyProduction/…-sherpa-onnx).
     */
    private val models = listOf(
        // Index 0 — Multilingual GGML (unchanged)
        TranscriptionModel(
            name = "ggml-small.bin",
            modelType = MULTILINGUAL_MODEL,
            size = "465 MB",
            // User-visible in the download dialog → German (project rule: all UI texts German)
            description = "Mehrsprachiges Modell (50+ Sprachen)",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            format = ModelFormat.GGML
        ),
        // Index 1 — German Schnell, now ONNX via sherpa-onnx
        TranscriptionModel(
            name = "whisper-large-v3-turbo-german",
            modelType = GERMAN_MODEL,
            size = "~990 MB",
            description = "Deutsches Modell – Schnell (ONNX)",
            url = null,
            format = ModelFormat.ONNX,
            downloadFiles = listOf(
                DownloadFile(
                    fileName = "large-v3-turbo-encoder.int8.onnx",
                    url = "https://huggingface.co/MolyProduction/whisper-large-v3-turbo-german-sherpa-onnx/resolve/main/large-v3-turbo-encoder.int8.onnx",
                    sizeBytes = 674_622_356L
                ),
                DownloadFile(
                    fileName = "large-v3-turbo-decoder.int8.onnx",
                    url = "https://huggingface.co/MolyProduction/whisper-large-v3-turbo-german-sherpa-onnx/resolve/main/large-v3-turbo-decoder.int8.onnx",
                    sizeBytes = 361_070_805L
                ),
                DownloadFile(
                    fileName = "large-v3-turbo-tokens.txt",
                    url = "https://huggingface.co/MolyProduction/whisper-large-v3-turbo-german-sherpa-onnx/resolve/main/large-v3-turbo-tokens.txt",
                    sizeBytes = 866_987L
                )
            )
        ),
        // Index 2 — German Genau GGML (unchanged)
        TranscriptionModel(
            name = "ggml-large-v3-turbo-german.bin",
            modelType = GERMAN_MODEL,
            size = "1,62 GB",
            description = "Deutsches Modell – Genau (höchste Genauigkeit)",
            url = "https://huggingface.co/cstr/whisper-large-v3-turbo-german-ggml/resolve/main/ggml-model.bin",
            format = ModelFormat.GGML
        )
    )

    suspend fun getSelectedModel(): TranscriptionModel {
        val modelSelectionValue = preferencesRepository.getModelSelection().first()
        return when (modelSelectionValue) {
            OPTIMIZED_MODEL_SELECTION       -> models[2]
            MULTILINGUAL_EXTENDED_SELECTION -> models[0]
            else                            -> models[1]
        }
    }

    fun getDefaultTranscriptionModel() = models[1]

    fun getModelBySelection(selectionConstant: Int): TranscriptionModel {
        return when (selectionConstant) {
            OPTIMIZED_MODEL_SELECTION       -> models[2]
            MULTILINGUAL_EXTENDED_SELECTION -> models[0]
            else                            -> models[1]
        }
    }
}
