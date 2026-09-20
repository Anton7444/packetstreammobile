package com.example.packetstream_mobile.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PacketStreamWidgetScheduler {
    const val WORK_NAME = "packetstream_widget_refresh"

    fun apply(context: Context, enabled: Boolean, intervalMinutes: Long) {
        val applicationContext = context.applicationContext
        val workManager = WorkManager.getInstance(applicationContext)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val interval = PacketStreamWidgetIntervals.normalize(intervalMinutes)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<PacketStreamWidgetWorker>(
            interval,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
