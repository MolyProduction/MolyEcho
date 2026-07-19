package com.module.notelycompose.platform

enum class Theme(val displayName: String) {
    LIGHT("Hell"),
    DARK("Dunkel"),
    SYSTEM("System")
}

expect class PlatformUtils {
    fun shareText(text: String)
    fun shareImageWithText(imageBytes: ByteArray, text: String)
    fun shareRecording(path: String)
    fun exportRecordingWithFilePicker(
        sourcePath: String,
        fileName: String,
        onResult: (Boolean, String?) -> Unit
    )
    fun requestStoragePermission(): Boolean
    fun exportTextWithFilePicker(
        text: String,
        fileName: String,
        onResult: (Boolean, String?) -> Unit
    )
    fun exportTextAsPDFWithFilePicker(
        text: String,
        fileName: String,
        textSize: Float,
        onResult: (Boolean, String?) -> Unit
    )
    fun copyTextToClipboard(
        text: String,
        onResult: (Boolean, String?) -> Unit
    )
}
