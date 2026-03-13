package com.module.notelycompose.modelDownloader

import com.module.notelycompose.onboarding.data.PreferencesRepository
import kotlinx.coroutines.flow.first

const val NO_MODEL_SELECTION = -1
const val STANDARD_MODEL_SELECTION = 0       // German Quick (q5_0, downloadable)
const val OPTIMIZED_MODEL_SELECTION = 1      // German Accurate (cstr, downloadable)
const val MULTILINGUAL_STANDARD_SELECTION = 2 // Legacy — kept for DataStore migration, maps to ggml-small
const val MULTILINGUAL_EXTENDED_SELECTION = 3 // Multilingual (ggml-small)
const val MULTILINGUAL_MODEL = "en"
const val HINDI_MODEL = "hi"
const val FARSI = "fa"
const val GUJARATI = "gu"
const val GERMAN_MODEL = "de"

data class TranscriptionModel(val name: String, val modelType: String, val size: String, val description: String, val url: String?) {
    fun getModelDownloadSize(): String = size
    fun getModelDownloadType(): String = modelType
}

class ModelSelection(private val preferencesRepository: PreferencesRepository) {

    /**
     * Available Whisper models.
     *
     * Index layout (stable — constants below depend on these positions):
     *   0  ggml-small.bin                          multilingual (465 MB)
     *   1  ggml-base-hi.bin                        Hindi/Gujarati (140 MB)
     *   2  ggml-large-v3-turbo-german-q5_0.bin     German "Schnell" (574 MB)
     *   3  ggml-large-v3-turbo-german.bin           German "Genau"  (1.62 GB)
     */
    private val models = listOf(
        TranscriptionModel(
            "ggml-small.bin",
            MULTILINGUAL_MODEL,
            "465 MB",
            "Multilingual model (supports 50+ languages, slower, more-accurate)",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
        ),
        TranscriptionModel(
            "ggml-base-hi.bin",
            HINDI_MODEL,
            "140 MB",
            "Hindi/Gujarati optimized model",
            "https://huggingface.co/khidrew/whisper-base-hindi-ggml/resolve/main/ggml-base-hi.bin"
        ),
        TranscriptionModel(
            "ggml-large-v3-turbo-german-q5_0.bin",
            GERMAN_MODEL,
            "574 MB",
            "German large-v3-turbo model (high accuracy)",
            "https://huggingface.co/F1sk/whisper-large-v3-turbo-german-ggml-q5_0/resolve/main/ggml-large-v3-turbo-german-q5_0.bin"
        ),
        TranscriptionModel(
            "ggml-large-v3-turbo-german.bin",
            GERMAN_MODEL,
            "1.62 GB",
            "German large-v3-turbo model (highest accuracy)",
            "https://huggingface.co/cstr/whisper-large-v3-turbo-german-ggml/resolve/main/ggml-model.bin"
        )
    )

    /**
     * Returns the model matching the current user preferences.
     *
     * Model selection and transcription language are intentionally decoupled:
     * multilingual models support German transcription when Whisper receives language="de".
     */
    suspend fun getSelectedModel(): TranscriptionModel {
        val defaultLanguage = preferencesRepository.getDefaultTranscriptionLanguage().first()
        val modelSelectionValue = preferencesRepository.getModelSelection().first()

        return when (defaultLanguage) {
            HINDI_MODEL, GUJARATI -> models[1]
            FARSI -> models.first { it.name == "ggml-small.bin" }
            else -> when (modelSelectionValue) {
                OPTIMIZED_MODEL_SELECTION                       -> models[3] // German cstr "Genau"
                MULTILINGUAL_STANDARD_SELECTION,
                MULTILINGUAL_EXTENDED_SELECTION                 -> models[0] // ggml-small multilingual
                else                                            -> models[2] // German q5_0 "Schnell" (default)
            }
        }
    }

    fun getDefaultTranscriptionModel() = models[2] // German q5_0 "Schnell"
}
