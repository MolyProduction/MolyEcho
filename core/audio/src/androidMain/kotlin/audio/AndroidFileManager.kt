package audio

import android.content.Context
import android.net.Uri
import audio.converter.AudioConverter
import audio.utils.LauncherHolder
import audio.utils.deleteFile
import audio.utils.savePickedAudioToAppStorage
import audio.utils.savePickedVideoToAppStorage

internal class AndroidFileManager(
    private val context: Context,
    private val launcherHolder: LauncherHolder,
    private val audioConverter: AudioConverter
) : FileManager {

    private var pickedAudioUri: Uri? = null
    private var pickedVideoUri: Uri? = null

    // Kein Berechtigungs-Check noetig: ACTION_GET_CONTENT erteilt eine Lesefreigabe
    // pro ausgewaehlter Datei (Play-Richtlinie verbietet READ_MEDIA_* fuer diesen Zweck).
    override fun launchAudioPicker(onResult: () -> Unit) {
        pickedAudioUri = null

        launcherHolder.audioPickerLauncher?.launch { uri ->
            pickedAudioUri = uri
            uri?.let { onResult() }
        }
    }

    override fun launchVideoPicker(onResult: () -> Unit) {
        pickedVideoUri = null

        launcherHolder.videoPickerLauncher?.launch { uri ->
            pickedVideoUri = uri
            uri?.let { onResult() }
        }
    }

    override suspend fun processPickedAudioToWav(onProgress: (Float) -> Unit): String? {
        val inputPath = copyAudioToAppStorage() ?: return null
        val outputPath = audioConverter.convertAudioToWav(inputPath, onProgress)
        deleteFile(inputPath)
        return outputPath
    }

    override suspend fun processPickedVideoToWav(onProgress: (Float) -> Unit): String? {
        val inputPath = copyVideoToAppStorage() ?: return null
        val outputPath = audioConverter.extractAudioFromVideoToWav(inputPath, onProgress)
        deleteFile(inputPath)
        return outputPath
    }

    private fun copyAudioToAppStorage(): String? {
        return pickedAudioUri?.let { context.savePickedAudioToAppStorage(it)?.absolutePath }
            .also { pickedAudioUri = null }
    }

    private fun copyVideoToAppStorage(): String? {
        return pickedVideoUri?.let { context.savePickedVideoToAppStorage(it)?.absolutePath }
            .also { pickedVideoUri = null }
    }
}