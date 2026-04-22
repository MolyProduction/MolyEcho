package com.module.notelycompose.utils

import com.module.notelycompose.core.debugPrintln
import java.io.RandomAccessFile

/**
 * Ergebnis des Stille-Scans.
 *
 * @param silenceRatio Anteil stiller 1s-Fenster (0.0 = alles Sprache, 1.0 = alles Stille)
 * @param silentWindows BooleanArray der Länge totalSeconds; true = dieses 1s-Fenster ist still
 */
data class SilenceAnalysisResult(
    val silenceRatio: Float,
    val silentWindows: BooleanArray
) {
    /** true wenn VAD aktiv sein soll (Stille-Anteil überschreitet Trigger-Schwelle) */
    val shouldApplyVad: Boolean get() = silenceRatio >= SilenceAnalyzer.VAD_TRIGGER_RATIO

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SilenceAnalysisResult) return false
        return silenceRatio == other.silenceRatio && silentWindows.contentEquals(other.silentWindows)
    }

    override fun hashCode(): Int = 31 * silenceRatio.hashCode() + silentWindows.contentHashCode()
}

/**
 * Scannt eine WAV-Datei auf stille Abschnitte ohne das gesamte File in den Speicher zu laden.
 * Liest das File in 1-Sekunden-Fenstern als ByteArray und berechnet den RMS-Wert pro Fenster.
 */
object SilenceAnalyzer {

    /** RMS unter diesem Wert gilt als "still" (1 % Full Scale; Sprache typisch 0.05–0.3) */
    const val SILENCE_RMS_THRESHOLD = 0.01f

    /** Feature wird nur aktiviert wenn mehr als dieser Anteil des Files still ist */
    const val VAD_TRIGGER_RATIO = 0.15f

    /**
     * Analysiert die WAV-Datei und liefert Stille-Informationen zurück.
     * Schlägt der Scan fehl (kein valides WAV, I/O-Fehler), wird
     * SilenceAnalysisResult(0f, BooleanArray(0)) zurückgegeben → shouldApplyVad = false.
     */
    fun analyze(filePath: String): SilenceAnalysisResult {
        return try {
            analyzeInternal(filePath)
        } catch (e: Exception) {
            debugPrintln { "SilenceAnalyzer: Fehler beim Scan, VAD deaktiviert: ${e.message}" }
            SilenceAnalysisResult(silenceRatio = 0f, silentWindows = BooleanArray(0))
        }
    }

    private fun analyzeInternal(filePath: String): SilenceAnalysisResult {
        val file = RandomAccessFile(filePath, "r")
        return try {
            val info = WavFileParser.parse(file)

            if (info.bitsPerSample != 16) {
                debugPrintln { "SilenceAnalyzer: Nicht-unterstütztes Format (bits=${info.bitsPerSample}), VAD deaktiviert" }
                return SilenceAnalysisResult(silenceRatio = 0f, silentWindows = BooleanArray(0))
            }

            val bytesPerSecond = (info.sampleRate * info.channels * (info.bitsPerSample / 8)).toLong()
            val totalSeconds   = (info.dataSize / bytesPerSecond).toInt()

            if (totalSeconds <= 0) {
                return SilenceAnalysisResult(silenceRatio = 0f, silentWindows = BooleanArray(0))
            }

            val silentWindows = BooleanArray(totalSeconds)
            val windowBuffer  = ByteArray(bytesPerSecond.toInt())
            var silentCount   = 0

            file.seek(info.dataOffset)

            for (sec in 0 until totalSeconds) {
                val bytesRead = file.read(windowBuffer, 0, bytesPerSecond.toInt())
                if (bytesRead <= 0) break

                val rms = computeRms(windowBuffer, bytesRead)
                if (rms < SILENCE_RMS_THRESHOLD) {
                    silentWindows[sec] = true
                    silentCount++
                }
            }

            val silenceRatio = silentCount.toFloat() / totalSeconds
            debugPrintln { "SilenceAnalyzer: silenceRatio=$silenceRatio ($silentCount/$totalSeconds Fenster still), shouldApplyVad=${silenceRatio >= VAD_TRIGGER_RATIO}" }

            SilenceAnalysisResult(silenceRatio = silenceRatio, silentWindows = silentWindows)
        } finally {
            file.close()
        }
    }

    /**
     * Berechnet den RMS-Wert eines Puffers mit 16-bit Little-Endian PCM-Daten.
     * Ergebnis ist auf [-1.0, 1.0] normiert.
     */
    private fun computeRms(buffer: ByteArray, bytesRead: Int): Float {
        val sampleCount = bytesRead / 2 // 16-bit = 2 Bytes pro Sample
        if (sampleCount == 0) return 0f

        var sumSquares = 0.0
        var i = 0
        while (i < bytesRead - 1) {
            // Little-Endian 16-bit signed integer
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val normalized = sample.toShort().toFloat() / 32767.0f
            sumSquares += normalized * normalized
            i += 2
        }
        return Math.sqrt(sumSquares / sampleCount).toFloat()
    }
}

/**
 * Prüft ob dieser Chunk vollständig still ist (alle 1s-Fenster unter dem RMS-Schwellwert).
 * Ein Chunk mit auch nur 1s Sprache wird nie als still eingestuft.
 */
fun StreamingAudioChunk.isSilentChunk(analysis: SilenceAnalysisResult): Boolean {
    if (!analysis.shouldApplyVad) return false
    val bytesPerSecond = (header.sampleRate * header.channels * (header.bitsPerSample / 8)).toLong()
    if (bytesPerSecond <= 0) return false

    val chunkStartSec = ((startOffset - header.dataOffset) / bytesPerSecond).toInt()
    val chunkEndSec   = ((endOffset   - header.dataOffset) / bytesPerSecond).toInt()
        .coerceAtMost(analysis.silentWindows.size)

    if (chunkStartSec >= chunkEndSec) return false

    // Chunk wird nur übersprungen wenn ALLE seine 1s-Fenster still sind
    return (chunkStartSec until chunkEndSec).all { sec ->
        analysis.silentWindows.getOrElse(sec) { false }
    }
}
