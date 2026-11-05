package com.module.notelycompose.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.module.notelycompose.MainActivity
import com.module.notelycompose.dictation.DictationIntents
import de.molyecho.notlyvoice.android.R

/**
 * Schnellkachel „MolyDiktat": öffnet die App mit der DICTATE-Aktion.
 * Eigene Kachel neben [RecordingTileService]; deren Verhalten bleibt unverändert.
 */
class DictationTileService : TileService() {

    override fun onClick() {
        // Ohne CLEAR_TASK: eine laufende Aufnahme/Transkription darf nicht weggeräumt werden;
        // MainActivity entscheidet selbst, ob ein Diktat gestartet werden kann.
        val intent = Intent(this, MainActivity::class.java).apply {
            action = DictationIntents.ACTION_DICTATE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                // Wie RecordingTileService: vor Android 14 genügt startActivity().
                startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            icon = Icon.createWithResource(this@DictationTileService, R.drawable.ic_dictation_mic)
            label = getString(R.string.shortcut_dictation_short)
            updateTile()
        }
    }
}
