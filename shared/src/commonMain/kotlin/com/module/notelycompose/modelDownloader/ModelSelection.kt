package com.module.notelycompose.modelDownloader

import com.module.notelycompose.onboarding.data.PreferencesRepository
import kotlinx.coroutines.flow.first

const val NO_MODEL_SELECTION = -1
const val STANDARD_MODEL_SELECTION = 0
const val OPTIMIZED_MODEL_SELECTION = 1
const val ENGLISH_MODEL = "en"
const val OPTIMIZED_MODEL = "en"
const val HINDI_MODEL = "hi"
const val FARSI = "fa"
const val GUJARATI = "gu"
const val GERMAN_MODEL = "de"
const val BUNDLED_GERMAN_MODEL_FILENAME = "ggml-tiny-german.bin"

data class TranscriptionModel(val name:String, val modelType: String, val size:String, val description:String, val url:String){
    fun getModelDownloadSize():String = size
    fun getModelDownloadType():String = modelType
}

class ModelSelection(private val preferencesRepository: PreferencesRepository) {

    /**
     * Available Whisper models
     */
    private val models = listOf(
        TranscriptionModel(
            "ggml-base-en.bin",
            ENGLISH_MODEL,
            "142 MB",
            "Multilingual model (supports 50+ languages)",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
        ),
        TranscriptionModel(
            "ggml-small.bin",
            OPTIMIZED_MODEL,
            "465 MB",
            "Multilingual model (supports 50+ languages, slower, more-accurate)",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
        ),
        // index 2 model hindi
        TranscriptionModel(
            "ggml-base-hi.bin",
            HINDI_MODEL,
            "140 MB",
            "Hindi/Gujarati optimized model",
            "https://huggingface.co/khidrew/whisper-base-hindi-ggml/resolve/main/ggml-base-hi.bin"
        ),
        // index 3 German tiny model (bundled in APK)
        TranscriptionModel(
            BUNDLED_GERMAN_MODEL_FILENAME,
            GERMAN_MODEL,
            "75 MB",
            "German optimized model (bundled)",
            ""
        ),
        // index 4 German large-v3-turbo model (downloadable)
        TranscriptionModel(
            "ggml-large-v3-turbo-german.bin",
            GERMAN_MODEL,
            "1.62 GB",
            "German large-v3-turbo model (high accuracy)",
            "https://huggingface.co/cstr/whisper-large-v3-turbo-german-ggml/resolve/main/ggml-model.bin"
        )
    )

    /**
     * Get the appropriate model based on transcription language
     * @return The model to use
     */
    suspend fun getSelectedModel(): TranscriptionModel {
        val defaultLanguage = preferencesRepository.getDefaultTranscriptionLanguage().first()
        return when (defaultLanguage) {
            HINDI_MODEL, GUJARATI -> models[2] // hindi
            FARSI -> models[1] // optimised
            GERMAN_MODEL -> {
                if (preferencesRepository.getModelSelection().first() == OPTIMIZED_MODEL_SELECTION) {
                    models[4] // german turbo (downloadable)
                } else {
                    models[3] // german tiny (bundled, default)
                }
            }
            else -> {
                if(preferencesRepository.getModelSelection().first() == STANDARD_MODEL_SELECTION
                    || preferencesRepository.getModelSelection().first() == NO_MODEL_SELECTION) {
                    models[0] // standard
                } else {
                    models[1] // optimised
                }
            }
        }
    }

    fun getDefaultTranscriptionModel() = models[3] // german tiny (bundled)


}