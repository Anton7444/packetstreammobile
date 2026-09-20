package com.example.packetstream_mobile.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.Keep
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Handles the widget's manual refresh tap. Declared with android:exported="false" so only
 * this app's own PendingIntent can reach it — unlike PacketStreamWidgetProvider, which must
 * stay exported for the system to deliver APPWIDGET_UPDATE.
 */
@Keep
class PacketStreamWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH_WIDGET) return
        val applicationContext = context.applicationContext
        val store = PacketStreamWidgetStore(applicationContext)
        try {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                MANUAL_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PacketStreamWidgetWorker>().build(),
            )
            // The worker owns the terminal transition. This avoids a tap leaving a
            // spinner behind when WorkManager deduplicates or rejects the request.
        } catch (_: Exception) {
            store.saveStatus(WidgetStatus.OFFLINE)
            PacketStreamWidgetProvider.updateAll(applicationContext)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "io.packetstream.mobile.action.REFRESH_WIDGET"
        private const val MANUAL_WORK_NAME = "packetstream_widget_manual_refresh"
    }
}
