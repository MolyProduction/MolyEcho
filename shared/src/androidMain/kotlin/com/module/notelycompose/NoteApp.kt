package com.module.notelycompose

import android.app.Application
import android.os.Environment
import com.module.notelycompose.di.initKoinApplication
import com.module.notelycompose.modelDownloader.BUNDLED_GERMAN_MODEL_FILENAME
import com.module.notelycompose.onboarding.data.PreferencesRepository
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import java.io.File

class NoteApp : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO)
    private val preferencesRepository: PreferencesRepository by inject()

    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())
        initKoinApplication {
            androidContext(this@NoteApp)
            androidLogger()
        }
        appScope.launch {
            extractBundledGermanModelIfNeeded()
        }
    }

    private suspend fun extractBundledGermanModelIfNeeded() {
        if (preferencesRepository.isBundledModelExtracted().first()) return
        val targetDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val targetFile = File(targetDir, BUNDLED_GERMAN_MODEL_FILENAME)
        if (targetFile.exists()) {
            preferencesRepository.setBundledModelExtracted(true)
            return
        }
        try {
            assets.open(BUNDLED_GERMAN_MODEL_FILENAME).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            preferencesRepository.setBundledModelExtracted(true)
            Napier.i("Bundled German model extracted successfully")
        } catch (e: Exception) {
            Napier.e("Failed to extract bundled German model: ${e.message}")
        }
    }
}