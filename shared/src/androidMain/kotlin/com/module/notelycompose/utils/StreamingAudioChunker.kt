package com.module.notelycompose.utils

import com.module.notelycompose.core.debugPrintln
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streaming audio chunker that reads chunks directly from WAV files
 * without loading the entire file into memory
 */
class StreamingAudioChunker {

    companion object {
        // 10MB chunks (approximately 10 minutes of audio at 16kHz mono)
        const val CHUNK_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
        const val OVERLAP_SIZE_BYTES = 2 * 1024 * 1024 // 2MB overlap (20% overlap)

        // Minimum chunk size to ensure quality transcription
        const val MIN_CHUNK_SIZE_BYTES = 5 * 1024 * 1024 // 5MB minimum
    }

    // Reusable FloatArray to avoid repeated allocations
    private var reusableFloatArray: FloatArray? = null
    private var reusableByteArray: ByteArray? = null

    /**
     * Splits WAV file into overlapping chunks without loading entire file into memory
     *
     * @param filePath Path to the WAV file
     * @param chunkSizeBytes Size of each chunk in bytes (default: 10MB)
     * @param overlapSizeBytes Size of overlap between chunks in bytes (default: 2MB)
     * @return List of StreamingAudioChunk objects
     */
    fun splitWavFileIntoChunks(
        filePath: String,
        chunkSizeBytes: Int = CHUNK_SIZE_BYTES,
        overlapSizeBytes: Int = OVERLAP_SIZE_BYTES
    ): MutableList<StreamingAudioChunk> {
        val file = RandomAccessFile(filePath, "r")

        try {
            val header = WavFileParser.parse(file)
            val chunks = mutableListOf<StreamingAudioChunk>()

            // Calculate total chunks needed
            val totalChunks = (header.dataSize + chunkSizeBytes.toLong() - 1L) / chunkSizeBytes.toLong()

            debugPrintln { "Splitting WAV file: ${header.dataSize} bytes into ~$totalChunks chunks" }

            var currentOffset = header.dataOffset
            var chunkIndex = 0

            while (currentOffset < header.dataOffset + header.dataSize) {
                val remainingBytes = (header.dataOffset + header.dataSize) - currentOffset
                val currentChunkSize = minOf(chunkSizeBytes.toLong(), remainingBytes)

                // Calculate overlap for next chunk
                val overlapStart = if (chunkIndex > 0) {
                    currentOffset - overlapSizeBytes
                } else {
                    currentOffset
                }

                val overlapEnd = currentOffset + currentChunkSize

                chunks.add(StreamingAudioChunk(
                    chunkIndex = chunkIndex,
                    filePath = filePath,
                    startOffset = overlapStart,
                    endOffset = overlapEnd,
                    header = header,
                    isFirstChunk = chunkIndex == 0,
                    isLastChunk = currentOffset + currentChunkSize >= header.dataOffset + header.dataSize
                ))

                chunkIndex++
                currentOffset += (chunkSizeBytes - overlapSizeBytes)
            }

            debugPrintln { "Created ${chunks.size} streaming chunks" }
            chunks.forEachIndexed { index, chunk ->
                val durationSeconds = (chunk.endOffset - chunk.startOffset) / (header.sampleRate * header.channels * (header.bitsPerSample / 8.0))
                debugPrintln { "Chunk $index: ${chunk.startOffset}-${chunk.endOffset} (${chunk.endOffset - chunk.startOffset} bytes, ~${durationSeconds}s)" }
            }

            return chunks

        } finally {
            file.close()
        }
    }

    /**
     * Reads a specific chunk from the WAV file and converts it to FloatArray
     * Uses a reusable FloatArray to avoid repeated allocations
     *
     * @param chunk The streaming chunk to read
     * @return FloatArray containing the audio data for this chunk (reused array)
     */
    fun readChunkData(chunk: StreamingAudioChunk): FloatArray {
        val file = RandomAccessFile(chunk.filePath, "r")

        try {
            file.seek(chunk.startOffset)

            val chunkSize = (chunk.endOffset - chunk.startOffset).toInt()

            // Get or create reusable byte array
            val buffer = getReusableByteArray(chunkSize)
            val bytesRead = file.read(buffer, 0, chunkSize)

            if (bytesRead <= 0) {
                return FloatArray(0)
            }

            // Convert bytes to FloatArray using reusable array
            val result = convertBytesToFloatArrayReusable(buffer, bytesRead, chunk.header)

            // Clear the byte buffer from memory
            buffer.fill(0, 0, bytesRead)

            return result

        } finally {
            file.close()
        }
    }

    /**
     * Gets or creates a reusable ByteArray of the required size
     */
    private fun getReusableByteArray(requiredSize: Int): ByteArray {
        val current = reusableByteArray
        return if (current != null && current.size >= requiredSize) {
            current
        } else {
            val newArray = ByteArray(requiredSize)
            reusableByteArray = newArray
            newArray
        }
    }

    /**
     * Gets or creates a reusable FloatArray of the required size
     */
    private fun getReusableFloatArray(requiredSize: Int): FloatArray {
        val current = reusableFloatArray
        return if (current != null && current.size >= requiredSize) {
            current
        } else {
            val newArray = FloatArray(requiredSize)
            reusableFloatArray = newArray
            newArray
        }
    }

    /**
     * Clears reusable arrays from memory
     * Call this when done processing to free memory
     */
    fun clearReusableArrays() {
        reusableFloatArray = null
        reusableByteArray = null
    }

    /**
     * Gets the current size of reusable arrays for debugging
     */
    fun getReusableArraySizes(): Pair<Int, Int> {
        return Pair(
            reusableFloatArray?.size ?: 0,
            reusableByteArray?.size ?: 0
        )
    }

    /**
     * Converts byte array to FloatArray based on WAV format (reusable version)
     */
    private fun convertBytesToFloatArrayReusable(
        buffer: ByteArray,
        bytesRead: Int,
        header: WavInfo
    ): FloatArray {
        val bytesPerSample = header.bitsPerSample / 8
        val samplesCount = bytesRead / (header.channels * bytesPerSample)

        // Get or create reusable FloatArray
        val result = getReusableFloatArray(samplesCount)
        val byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until samplesCount) {
            val sample = when (header.channels) {
                1 -> {
                    // Mono
                    val sampleValue = byteBuffer.short
                    (sampleValue / 32767.0f).coerceIn(-1f..1f)
                }
                else -> {
                    // Stereo - average the channels
                    val left = byteBuffer.short
                    val right = byteBuffer.short
                    ((left + right) / 32767.0f / 2.0f).coerceIn(-1f..1f)
                }
            }

            result[i] = sample
        }

        return result
    }
}

/**
 * Represents a streaming audio chunk with file position information
 */
data class StreamingAudioChunk(
    val chunkIndex: Int,
    val filePath: String,
    val startOffset: Long,
    val endOffset: Long,
    val header: WavInfo,
    val isFirstChunk: Boolean,
    val isLastChunk: Boolean
) {
    val sizeBytes: Long get() = endOffset - startOffset
    val durationSeconds: Double get() = sizeBytes / (header.sampleRate * header.channels * (header.bitsPerSample / 8.0))
}

data class AudioChunk(
    val startSample: Int,
    val endSample: Int,
    val data: FloatArray
) {
    val durationMs: Long get() = ((endSample - startSample + 1) / 16.0).toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioChunk) return false

        if (startSample != other.startSample) return false
        if (endSample != other.endSample) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = startSample
        result = 31 * result + endSample
        result = 31 * result + data.contentHashCode()
        return result
    }
}


/**
 * Represents the transcription result for a single chunk
 */
data class ChunkTranscriptionResult(
    val chunk: AudioChunk,
    val text: String,
    val segments: List<TranscriptionSegment> = emptyList()
)

/**
 * Represents a single transcription segment with timing information
 */
data class TranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
