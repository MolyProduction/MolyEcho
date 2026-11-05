package com.module.notelycompose.dictation

/** Grund, warum ein Diktat keinen Text liefert (steuert den Benachrichtigungstext). */
enum class DictationFailure {
    NO_TEXT,
    MODEL_MISSING,
    FAILED
}
