package com.module.notelycompose.platform

actual class PlatformAudioPlayer {
    private var mediaPlayer: android.media.MediaPlayer? = null

    actual suspend fun prepare(filePath: String): Int {
        mediaPlayer?.release()
        try {
            val player = android.media.MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
            }
            mediaPlayer = player
            return player.duration
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }

    actual fun play() {
        mediaPlayer?.start()
    }

    actual fun pause() {
        mediaPlayer?.pause()
    }

    actual fun stop() {
        mediaPlayer?.stop()
    }

    actual fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    actual fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    actual fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.takeIf { isInValidStateForPlayback() }?.currentPosition ?: 0
        } catch (e: IllegalStateException) {
            // MediaPlayer is in invalid state, return 0
            0
        }
    }

    actual fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    /**
     * Checks if MediaPlayer is in a valid state for operations
     * This helps prevent IllegalStateException crashes
     */
    private fun isInValidStateForPlayback(): Boolean {
        val player = mediaPlayer ?: return false
        return try {
            // Try to access a property that would throw if in invalid state
            player.isPlaying
            true
        } catch (e: IllegalStateException) {
            false
        }
    }
}