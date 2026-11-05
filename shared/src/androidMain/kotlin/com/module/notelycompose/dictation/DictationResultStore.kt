package com.module.notelycompose.dictation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.File

/**
 * Letztes Diktat-Ergebnis als app-private Datei. Der Text läuft bewusst nicht über
 * Intent-Extras (Größenlimit, Sichtbarkeit) und der Receiver braucht keinen DB-Zugriff.
 */
class DictationResultStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    /** Überschreibt das vorherige Ergebnis. */
    fun write(text: String) {
        file.writeText(text, Charsets.UTF_8)
    }

    /** Liefert den gespeicherten Text oder null, wenn nichts (Verwertbares) vorliegt. */
    fun read(): String? =
        runCatching { if (file.exists()) file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() } else null }
            .getOrNull()

    companion object {
        private const val FILE_NAME = "dictation_last.txt"
    }
}

/** Schreibt Text in die Zwischenablage. Gibt false zurück, wenn das System es verweigert. */
internal fun copyToClipboard(context: Context, text: String): Boolean = try {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MolyDiktat", text))
    true
} catch (e: Exception) {
    false
}
