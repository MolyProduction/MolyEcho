package com.module.notelycompose.shareimport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MimeTypeApiCheckTest {
    @Test
    fun opus_requires_modern_api() {
        assertTrue(mimeTypeRequiresModernApi("audio/opus"))
    }

    @Test
    fun ogg_requires_modern_api() {
        assertTrue(mimeTypeRequiresModernApi("audio/ogg"))
    }

    @Test
    fun mp3_does_not_require_modern_api() {
        assertFalse(mimeTypeRequiresModernApi("audio/mpeg"))
    }

    @Test
    fun aac_does_not_require_modern_api() {
        assertFalse(mimeTypeRequiresModernApi("audio/aac"))
    }

    @Test
    fun video_does_not_require_modern_api_check() {
        assertFalse(mimeTypeRequiresModernApi("video/mp4"))
    }

    @Test
    fun null_mime_type_is_safe() {
        assertFalse(mimeTypeRequiresModernApi(null))
    }
}
