package com.module.notelycompose.utils

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WavInfo(
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val dataOffset: Long,
    val dataSize: Long
)

object WavFileParser {
    private const val RIFF_HEADER_BYTES = 12
    private const val SUBCHUNK_HEADER_BYTES = 8
    private const val MAX_HEADER_SCAN_BYTES = 1_048_576L  // 1 MB Safety-Limit
    private const val WAVE_FORMAT_PCM = 1

    /**
     * Scannt den RIFF/WAVE-Container und liefert Format-Daten + die exakte Position
     * und Länge des `data`-Sub-Chunks. Unterstützt beliebige Pre-`data`-Chunks wie
     * `LIST`/`INFO`, `bext`, `id3 `, `JUNK`.
     *
     * Wirft IllegalArgumentException bei:
     *  - nicht-RIFF/WAVE-Container
     *  - fehlendem oder zu spätem `fmt `/`data` (> 1 MB)
     *  - Nicht-PCM-Format
     *  - ungültigen Format-Parametern (channels, bitsPerSample, sampleRate)
     */
    fun parse(file: RandomAccessFile): WavInfo {
        // 1. RIFF-Kontainer validieren.
        file.seek(0)
        val riffHeader = ByteArray(RIFF_HEADER_BYTES)
        require(file.read(riffHeader) == RIFF_HEADER_BYTES) { "WAV header too short" }
        val riffId = String(riffHeader, 0, 4)
        val waveId = String(riffHeader, 8, 4)
        require(riffId == "RIFF" && waveId == "WAVE") { "Not a RIFF/WAVE container" }

        // 2. Sub-Chunks iterieren, bis `fmt ` UND `data` gefunden.
        var pos = RIFF_HEADER_BYTES.toLong()
        var channels = -1
        var sampleRate = -1
        var bitsPerSample = -1
        var dataOffset = -1L
        var dataSize = -1L

        val subHeader = ByteArray(SUBCHUNK_HEADER_BYTES)
        while (pos < MAX_HEADER_SCAN_BYTES) {
            file.seek(pos)
            if (file.read(subHeader) != SUBCHUNK_HEADER_BYTES) break
            val id = String(subHeader, 0, 4)
            val size = ByteBuffer.wrap(subHeader, 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

            when (id) {
                "fmt " -> {
                    require(size >= 16) { "fmt chunk too short: $size" }
                    val fmtBody = ByteArray(16)
                    require(file.read(fmtBody) == 16) { "Cannot read fmt body" }
                    val fmtBuf = ByteBuffer.wrap(fmtBody).order(ByteOrder.LITTLE_ENDIAN)
                    val audioFormat = fmtBuf.short.toInt()
                    require(audioFormat == WAVE_FORMAT_PCM) {
                        "Only PCM WAV files are supported (audioFormat=$audioFormat)"
                    }
                    channels = fmtBuf.short.toInt()
                    sampleRate = fmtBuf.int
                    fmtBuf.int   // byteRate skip
                    fmtBuf.short // blockAlign skip
                    bitsPerSample = fmtBuf.short.toInt()
                }
                "data" -> {
                    dataOffset = pos + SUBCHUNK_HEADER_BYTES
                    dataSize = size
                    break
                }
                // LIST, bext, id3 , JUNK, PAD, … überspringen
            }
            // Sub-Chunks sind auf gerade Byte-Grenzen gepadded (RIFF-Spec).
            pos += SUBCHUNK_HEADER_BYTES + size + (size and 1L)
        }

        require(dataOffset >= 0 && dataSize >= 0) { "No data chunk found within $MAX_HEADER_SCAN_BYTES bytes" }
        require(channels in 1..2) { "Unsupported channel count: $channels" }
        require(bitsPerSample >= 8) { "Unsupported bitsPerSample: $bitsPerSample" }
        require(sampleRate > 0) { "Unsupported sampleRate: $sampleRate" }

        return WavInfo(channels, sampleRate, bitsPerSample, dataOffset, dataSize)
    }
}
