package com.module.notelycompose.dictation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Einstellungen eines Diktats; werden beim Start eingefroren. */
data class DictationOptions(
    val autoTranscribe: Boolean = true,
    val autoCopy: Boolean = true,
    val notifyReady: Boolean = true
)

/**
 * Prozessweiter Zustand eines laufenden „MolyDiktat" (Koin-Singleton).
 *
 * Ablauf: [request] (Intent) → [begin] (App-Root navigiert und friert Optionen ein)
 * → Aufnahme → Transkription → [finish]. Reine Logik, keine Android-Abhängigkeit.
 */
class DictationSession {
    private val _pendingStart = MutableStateFlow(false)
    val pendingStart: StateFlow<Boolean> = _pendingStart.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _options = MutableStateFlow(DictationOptions())
    val options: StateFlow<DictationOptions> = _options.asStateFlow()

    private val backgroundChannel = Channel<Unit>(Channel.CONFLATED)

    /** Einmalige Signale „App in den Hintergrund schieben". */
    val backgroundRequests: Flow<Unit> = backgroundChannel.receiveAsFlow()

    /** Meldet einen Diktat-Wunsch an. false, wenn bereits eines wartet oder läuft. */
    fun request(): Boolean {
        if (_active.value || _pendingStart.value) return false
        _pendingStart.value = true
        return true
    }

    /** Startet das wartende Diktat mit eingefrorenen Optionen. Ohne [request] wirkungslos. */
    fun begin(options: DictationOptions) {
        if (!_pendingStart.value) return
        _options.value = options
        _pendingStart.value = false
        _active.value = true
    }

    /** Hintergrund-Wunsch; nur bei aktivem Diktat mit Option „automatisch transkribieren". */
    fun requestBackground() {
        if (_active.value && _options.value.autoTranscribe) {
            backgroundChannel.trySend(Unit)
        }
    }

    /** Beendet ein laufendes Diktat. Ein bereits wartender neuer Start bleibt unberührt. */
    fun finish() {
        _active.value = false
        _options.value = DictationOptions()
    }

    /** Verwirft einen wartenden, noch nicht gestarteten Diktat-Wunsch. */
    fun clearIfNotStarted() {
        _pendingStart.value = false
    }
}
