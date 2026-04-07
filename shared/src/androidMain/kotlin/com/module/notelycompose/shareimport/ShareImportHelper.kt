package com.module.notelycompose.shareimport

import android.content.Context
import android.net.Uri
import android.os.Build
import audio.converter.AudioConverter
import audio.utils.savePickedAudioToAppStorage
import audio.utils.savePickedVideoToAppStorage
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UnsupportedAudioFormatException : Exception(
    "Dieses Audioformat wird auf diesem Gerät nicht unterstützt. Android 10 oder höher erforderlich."
)

/** Gibt true zurück, wenn der MIME-Typ einen Opus-Decoder benötigt (erst API 29+). */
internal fun mimeTypeRequiresModernApi(mimeType: String?): Boolean =
    mimeType == "audio/opus" || mimeType == "audio/ogg"

/**
 * Wandelt eine geteilte URI (audio- oder video-) in eine 16 kHz-Mono-WAV-Datei um,
 * indem der bereits vorhandene AndroidAudioConverter genutzt wird.
 *
 * API-Beschraenkung: Opus (WhatsApp-Format) ist erst ab API 29 nativ decodierbar.
 * Fuer neuere Formate oder aeltere Geraete wird UnsupportedAudioFormatException geworfen.
 */
class ShareImportHelper(
    private val context: Context,
    private val audioConverter: AudioConverter
) {
    /**
     * @param uri  content://-URI aus dem Share-Intent
     * @param onProgress  Fortschrittscallback 0.0-1.0 (optional)
     * @return  Absoluter Pfad zur erzeugten WAV-Datei
     */
    suspend fun importSharedUri(
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri)

        // Opus benötigt API 29+ (Android 10)
        if (mimeTypeRequiresModernApi(mimeType) && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw UnsupportedAudioFormatException()
        }

        val isVideo = mimeType?.startsWith("video/") == true

        // Inhalt in temporäre Datei kopieren (ContentResolver to lokaler Speicher)
        val tempFile = if (isVideo) {
            context.savePickedVideoToAppStorage(uri)
        } else {
            context.savePickedAudioToAppStorage(uri)
        } ?: throw IOException("URI konnte nicht gelesen werden: $uri")

        try {
            // Bestehenden AudioConverter nutzen (MediaCodec, 16 kHz Mono WAV-Ausgabe)
            if (isVideo) {
                audioConverter.extractAudioFromVideoToWav(tempFile.absolutePath, onProgress)
                    ?: throw IOException("Videokonvertierung fehlgeschlagen")
            } else {
                audioConverter.convertAudioToWav(tempFile.absolutePath, onProgress)
                    ?: throw IOException("Audiokonvertierung fehlgeschlagen")
            }
        } finally {
            tempFile.delete()  // Temp-Kopie aufräumen
        }
    }
}
