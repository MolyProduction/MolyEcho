package com.module.notelycompose

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Einzel-Kanal zwischen MainActivity (Sender) und AndroidShareImportCoordinator (Empfänger).
 * replay=1 stellt sicher, dass eine URI, die vor Koin-Initialisierung eintrifft,
 * nicht verloren geht.
 */
object ShareIntentBus {
    val incoming: MutableSharedFlow<Uri> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 1
    )
}
