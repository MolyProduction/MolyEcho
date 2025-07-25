package com.module.notelycompose.notes.ui.detail

fun textSizeSelectedFormats(
    formatOption: FormatOptionTextFormat,
    bodyTextSize: Float,
    onSelectFormatOption: (size: Float) -> Unit
) {
    when (formatOption) {
        FormatOptionTextFormat.Title -> onSelectFormatOption(24f)
        FormatOptionTextFormat.Heading -> onSelectFormatOption(20f)
        FormatOptionTextFormat.Subheading -> onSelectFormatOption(16f)
        FormatOptionTextFormat.Body -> {
            onSelectFormatOption(bodyTextSize)
        }
    }
}
