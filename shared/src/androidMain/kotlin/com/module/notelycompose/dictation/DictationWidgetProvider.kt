package com.module.notelycompose.dictation

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.module.notelycompose.MainActivity
import de.molyecho.notlyvoice.android.R

/** 1×1-Widget „MolyDiktat": ein Tipp öffnet die App und startet das Diktat. */
class DictationWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = DictationIntents.ACTION_DICTATE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_WIDGET,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_dictation).apply {
                setOnClickPendingIntent(R.id.widget_dictation_root, pendingIntent)
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private companion object {
        const val REQUEST_WIDGET = 20
    }
}
