package com.module.notelycompose.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavFileParserTest {

    // ---------------------------------------------------------------------------
    // Helper to build minimal in-memory WAV bytes and write to a temp file.
    // ---------------------------------------------------------------------------

    private fun buildWav(
        audioFormat: Short = 1,
        channels: Short = 1,
        sampleRate: Int = 16_000,
        bitsPerSample: Short = 16,
        extraChunks: ByteArray = ByteArray(0),
        dataSamples: ByteArray = ByteArray(32)  // 16 silent samples
    ): ByteArray {
        val fmtSize = 16
        val dataSize = dataSamples.size

        val totalSize = 4 +                          // "WAVE"
            8 + fmtSize +                            // "fmt " chunk
            extraChunks.size +                       // optional pre-data chunks
            8 + dataSize                             // "data" chunk

        val buf = ByteBuffer.allocate(12 + totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buf.put("RIFF".toByteArray())
        buf.putInt(totalSize)
        buf.put("WAVE".toByteArray())

        // fmt chunk
        buf.put("fmt ".toByteArray())
        buf.putInt(fmtSize)
        buf.putShort(audioFormat)
        buf.putShort(channels)
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * channels * (bitsPerSample / 8))  // byteRate
        buf.putShort((channels * (bitsPerSample / 8)).toShort())  // blockAlign
        buf.putShort(bitsPerSample)

        // optional extra chunks (e.g. LIST, JUNK)
        buf.put(extraChunks)

        // data chunk
        buf.put("data".toByteArray())
        buf.putInt(dataSize)
        buf.put(dataSamples)

        return buf.array()
    }

    private fun buildChunk(id: String, payload: ByteArray): ByteArray {
        val size = payload.size
        // RIFF pads chunks to even byte boundary
        val paddedSize = size + (size and 1)
        val buf = ByteBuffer.allocate(8 + paddedSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(id.toByteArray())
        buf.putInt(size)
        buf.put(payload)
        if ((size and 1) == 1) buf.put(0)
        return buf.array()
    }

    private fun wavBytesToTempFile(bytes: ByteArray): File {
        val f = File.createTempFile("wavtest_", ".wav")
        f.deleteOnExit()
        f.writeBytes(bytes)
        return f
    }

    private fun parseBytes(bytes: ByteArray): WavInfo {
        val f = wavBytesToTempFile(bytes)
        return RandomAccessFile(f, "r").use { WavFileParser.parse(it) }
    }

    // ---------------------------------------------------------------------------
    // Happy-path tests
    // ---------------------------------------------------------------------------

    @Test
    fun parse_simpleWav_returnsCorrectOffsets() {
        val wav = buildWav(channels = 1, sampleRate = 16_000, bitsPerSample = 16)
        val info = parseBytes(wav)

        assertEquals(1, info.channels)
        assertEquals(16_000, info.sampleRate)
        assertEquals(16, info.bitsPerSample)
        // Standard 44-byte layout: RIFF(12) + fmt(8+16) + data(8) = 44
        assertEquals(44L, info.dataOffset)
        assertEquals(32L, info.dataSize)
    }

    @Test
    fun parse_withListInfoChunk_skipsPrefix() {
        val listPayload = "INFO".toByteArray() + buildChunk("INAM", "Test".toByteArray())
        val listChunk = buildChunk("LIST", listPayload)
        val wav = buildWav(extraChunks = listChunk)
        val info = parseBytes(wav)

        // data offset must be > 44 because of the extra LIST chunk
        assertTrue("dataOffset should be > 44 but was ${info.dataOffset}", info.dataOffset > 44)
        assertEquals(32L, info.dataSize)
        assertEquals(1, info.channels)
    }

    @Test
    fun parse_withJunkChunk_skipsPrefix() {
        // Odd-sized JUNK payload → parser must add padding byte
        val junkPayload = ByteArray(7) { 0 }  // 7 bytes → odd, needs 1 padding byte
        val junkChunk = buildChunk("JUNK", junkPayload)
        val wav = buildWav(extraChunks = junkChunk)
        val info = parseBytes(wav)

        assertTrue("dataOffset should be > 44 but was ${info.dataOffset}", info.dataOffset > 44)
        // Chunk header (8) + 7 payload + 1 pad = 16 extra bytes → dataOffset = 44 + 16 = 60
        assertEquals(60L, info.dataOffset)
        assertEquals(32L, info.dataSize)
    }

    // ---------------------------------------------------------------------------
    // Error / rejection tests
    // ---------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun parse_nonPcm_throws() {
        val wav = buildWav(audioFormat = 3)  // IEEE float
        parseBytes(wav)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_noRiff_throws() {
        val bytes = ByteArray(44) { 0 }
        parseBytes(bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_noDataChunk_throws() {
        // Build a file with only RIFF + fmt, no data chunk
        val fmtChunk = buildChunk("fmt ", ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(1)       // PCM
            putShort(1)       // mono
            putInt(16_000)    // sampleRate
            putInt(32_000)    // byteRate
            putShort(2)       // blockAlign
            putShort(16)      // bitsPerSample
        }.array())

        val totalSize = 4 + fmtChunk.size
        val buf = ByteBuffer.allocate(12 + totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(totalSize)
        buf.put("WAVE".toByteArray())
        buf.put(fmtChunk)

        parseBytes(buf.array())
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_channels0_throws() {
        val wav = buildWav(channels = 0)
        parseBytes(wav)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_sampleRate0_throws() {
        val wav = buildWav(sampleRate = 0)
        parseBytes(wav)
    }
}
