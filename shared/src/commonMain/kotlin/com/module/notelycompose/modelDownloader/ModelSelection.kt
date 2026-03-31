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

data class TranscriptionModel(
    val name: String,
    val modelType: String,
    val size: String,
    val description: String,
    val url: String?,
    val format: ModelFormat = ModelFormat.GGML,
    /**
     * For ONNX models: list of (local filename, download URL) pairs.
     * Null for single-file GGML models.
     */
    val downloadFiles: List<Pair<String, String>>? = null
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
     *   1  whisper-large-v3-turbo-german/       German "Schnell" (~400 MB, ONNX)
     *   2  ggml-large-v3-turbo-german.bin       German "Genau"  (1.62 GB, GGML)
     *
     * NOTE: ONNX download URLs for model 1 are placeholders.
     * Update with actual HuggingFace URLs after running sherpa-onnx export and uploading.
     * Expected files from: python export-onnx.py --model large-v3-turbo --checkpoint primeline/whisper-large-v3-turbo-german
     */
    private val models = listOf(
        // Index 0 — Multilingual GGML (unchanged)
        TranscriptionModel(
            name = "ggml-small.bin",
            modelType = MULTILINGUAL_MODEL,
            size = "465 MB",
            description = "Multilingual model (supports 50+ languages, slower, more-accurate)",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            format = ModelFormat.GGML
        ),
        // Index 1 — German Schnell, now ONNX via sherpa-onnx
        TranscriptionModel(
            name = "whisper-large-v3-turbo-german",
            modelType = GERMAN_MODEL,
            size = "~400 MB",
            description = "German large-v3-turbo model — fast (ONNX/XNNPACK)",
            url = null,
            format = ModelFormat.ONNX,
            downloadFiles = listOf(
                // TODO: Replace placeholder URLs with actual HuggingFace URLs after model upload
                "large-v3-turbo-encoder.int8.onnx" to "https://huggingface.co/TODO/whisper-large-v3-turbo-german-sherpa-onnx/resolve/main/large-v3-turbo-encoder.int8.onnx",
                "large-v3-turbo-decoder.int8.onnx" to "https://huggingface.co/TODO/whisper-large-v3-turbo-german-sherpa-onnx/resolve/main/large-v3-turbo-decoder.int8.onnx",
                "large-v3-turbo-tokens.txt" to "https://huggingface.co/TODO/whisper-large-v3-turbo-german-sherpa-onnx/resolve/main/large-v3-turbo-tokens.txt"
            )
        ),
        // Index 2 — German Genau GGML (unchanged)
        TranscriptionModel(
            name = "ggml-large-v3-turbo-german.bin",
            modelType = GERMAN_MODEL,
            size = "1.62 GB",
            description = "German large-v3-turbo model (highest accuracy)",
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
