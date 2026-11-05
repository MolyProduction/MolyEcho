package com.module.notelycompose.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.module.notelycompose.dictation.DictationOptions
import com.module.notelycompose.notes.extension.TEXT_SIZE_BODY
import com.module.notelycompose.modelDownloader.NO_MODEL_SELECTION
import com.module.notelycompose.platform.Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_BODY_TEXT_SIZE = floatPreferencesKey("body_text_size")
        private val KEY_MODEL_SELECTION = intPreferencesKey("model_selection")
        private val KEY_ACCENT_THEME = stringPreferencesKey("accent_theme")
        private val KEY_DICTATION_AUTO_TRANSCRIBE = booleanPreferencesKey("dictation_auto_transcribe")
        private val KEY_DICTATION_AUTO_COPY = booleanPreferencesKey("dictation_auto_copy")
        private val KEY_DICTATION_NOTIFY_READY = booleanPreferencesKey("dictation_notify_ready")
    }

    suspend fun hasCompletedOnboarding(): Boolean {
        return dataStore.data.first()[KEY_ONBOARDING_COMPLETED] ?: false
    }


    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setDefaultTranscriptionLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = language
        }
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME] = theme
        }
    }

     fun getDefaultTranscriptionLanguage(): Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "de"
    }


    fun getTheme(): Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_THEME]?:Theme.SYSTEM.name
    }

    fun getBodyTextSize(): Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_BODY_TEXT_SIZE] ?: TEXT_SIZE_BODY
    }

    suspend fun setBodyTextSize(size: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_BODY_TEXT_SIZE] = size
        }
    }

    fun getModelSelection(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_MODEL_SELECTION] ?: NO_MODEL_SELECTION
    }

    suspend fun setModelSelection(modelSelection: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_MODEL_SELECTION] = modelSelection
        }
    }

    fun getAccentTheme(): Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_ACCENT_THEME] ?: "GREEN"
    }

    suspend fun setAccentTheme(accent: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCENT_THEME] = accent
        }
    }

    // --- MolyDiktat (alle Optionen standardmäßig an) ---

    fun getDictationAutoTranscribe(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DICTATION_AUTO_TRANSCRIBE] ?: true
    }

    suspend fun setDictationAutoTranscribe(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_DICTATION_AUTO_TRANSCRIBE] = enabled }
    }

    fun getDictationAutoCopy(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DICTATION_AUTO_COPY] ?: true
    }

    suspend fun setDictationAutoCopy(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_DICTATION_AUTO_COPY] = enabled }
    }

    fun getDictationNotifyReady(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DICTATION_NOTIFY_READY] ?: true
    }

    suspend fun setDictationNotifyReady(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_DICTATION_NOTIFY_READY] = enabled }
    }

    /** Liest alle drei Optionen als Momentaufnahme (für den Diktat-Start). */
    suspend fun getDictationOptions(): DictationOptions = DictationOptions(
        autoTranscribe = getDictationAutoTranscribe().first(),
        autoCopy = getDictationAutoCopy().first(),
        notifyReady = getDictationNotifyReady().first()
    )
}
