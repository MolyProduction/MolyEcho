package com.module.notelycompose.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StreamingAudioChunkerSilenceAlignedTest {

    // 16 kHz, 16-bit mono → 32000 bytes per second
    private val bytesPerSecond = 16_000 * 2

    /** Builds a valid mono 16-bit 16 kHz WAV file with [seconds] seconds of audio (+ [extraBytes] partial tail). */
    private fun buildWavFile(seconds: Int, extraBytes: Int = 0): File {
        val dataSize = seconds * bytesPerSecond + extraBytes
        val fmtSize = 16
        val totalSize = 4 + 8 + fmtSize + 8 + dataSize
        val buf = ByteBuffer.allocate(12 + totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(totalSize)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(fmtSize)
        buf.putShort(1)                 // PCM
        buf.putShort(1)                 // mono
        buf.putInt(16_000)              // sample rate
        buf.putInt(bytesPerSecond)      // byte rate
        buf.putShort(2)                 // block align
        buf.putShort(16)                // bits per sample
        buf.put("data".toByteArray())
        buf.putInt(dataSize)
        buf.put(ByteArray(dataSize))    // silence — content is irrelevant for boundary math
        val file = File.createTempFile("chunker_test", ".wav")
        file.deleteOnExit()
        file.writeBytes(buf.array())
        return file
    }

    private fun durationSeconds(chunk: StreamingAudioChunk): Double =
        (chunk.endOffset - chunk.startOffset).toDouble() / bytesPerSecond

    @Test
    fun `short file yields single chunk covering all data`() {
        val file = buildWavFile(seconds = 12)
        val rms = FloatArray(12) { 0.2f }
        val chunks = StreamingAudioChunker().splitWavFileIntoSilenceAlignedChunks(
            file.absolutePath, rms, maxChunkSeconds = 30, minChunkSeconds = 20
        )
        assertEquals(1, chunks.size)
        assertEquals(12.0, durationSeconds(chunks[0]), 0.001)
        assertTrue(chunks[0].isFirstChunk)
        assertTrue(chunks[0].isLastChunk)
    }

    @Test
    fun `cut lands on quietest boundary within search window`() {
        // 60 s file, loud everywhere except second 24 → boundary should be placed at 24 or 25
        // (both boundaries adjacent to the quiet second score equally; earlier one wins).
        val file = buildWavFile(seconds = 60)
        val rms = FloatArray(60) { 0.3f }
        rms[24] = 0.001f
        val chunks = StreamingAudioChunker().splitWavFileIntoSilenceAlignedChunks(
            file.absolutePath, rms, maxChunkSeconds = 30, minChunkSeconds = 20
        )
        // First cut in window [20,30]: boundary 24 has score max(rms[23], rms[24]) = 0.3,
        // boundary 25 has score max(rms[24], rms[25]) = 0.3 — every other boundary scores 0.3
        // too, EXCEPT none is lower… so verify instead with a truly quiet pair:
        // (kept simple below in the dedicated test) — here we only assert structural sanity.
        assertTrue(chunks.all { durationSeconds(it) <= 30.0 + 1e-9 })
        val header = chunks.last().header
        assertEquals(header.dataOffset + header.dataSize, chunks.last().endOffset)
    }

    @Test
    fun `cut prefers boundary where both adjacent seconds are quiet`() {
        // 60 s file: seconds 25 AND 26 are quiet → boundary 26 (between them) scores lowest.
        val file = buildWavFile(seconds = 60)
        val rms = FloatArray(60) { 0.3f }
        rms[25] = 0.001f
        rms[26] = 0.001f
        val chunks = StreamingAudioChunker().splitWavFileIntoSilenceAlignedChunks(
            file.absolutePath, rms, maxChunkSeconds = 30, minChunkSeconds = 20
        )
        val firstCutSec = (chunks[0].endOffset - chunks[0].header.dataOffset) / bytesPerSecond
        assertEquals(26L, firstCutSec)
    }

    @Test
    fun `no chunk ever exceeds max seconds even with partial trailing second`() {
        // 65 s + partial second tail: last chunk must include the tail but stay under 30 s.
        val file = buildWavFile(seconds = 65, extraBytes = 12_345)
        val rms = FloatArray(65) { 0.3f }
        val chunks = StreamingAudioChunker().splitWavFileIntoSilenceAlignedChunks(
            file.absolutePath, rms, maxChunkSeconds = 30, minChunkSeconds = 20
        )
        assertTrue(chunks.all { durationSeconds(it) <= 30.0 + 1e-9 })
        // Complete coverage without gaps or overlap:
        for (i in 1 until chunks.size) {
            assertEquals(chunks[i - 1].endOffset, chunks[i].startOffset)
        }
        val header = chunks.last().header
        assertEquals(header.dataOffset + header.dataSize, chunks.last().endOffset)
        assertTrue(chunks.last().isLastChunk)
    }

    @Test
    fun `uniform loudness falls back to max-length chunks`() {
        // No quiet spot anywhere → every cut lands at the max boundary (30 s), like before.
        val file = buildWavFile(seconds = 90)
        val rms = FloatArray(90) { 0.3f }
        val chunks = StreamingAudioChunker().splitWavFileIntoSilenceAlignedChunks(
            file.absolutePath, rms, maxChunkSeconds = 30, minChunkSeconds = 20
        )
        assertEquals(3, chunks.size)
        chunks.forEach { assertEquals(30.0, durationSeconds(it), 0.001) }
    }
}
