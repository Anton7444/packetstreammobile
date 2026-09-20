package com.example.packetstream_mobile.widget

import android.content.Context
import androidx.annotation.Keep
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

@Keep
class PacketStreamWidgetWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        WidgetLog.d("worker started id=$id attempt=$runAttemptCount")
        val outcome = PacketStreamWidgetRefresh.run(applicationContext)
        val result = when (outcome) {
            RefreshOutcome.SUCCESS, RefreshOutcome.NO_SESSION, RefreshOutcome.SESSION_EXPIRED,
            RefreshOutcome.PARSER_FAILURE -> Result.success()
            RefreshOutcome.TEMPORARY_FAILURE -> if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        WidgetLog.d("worker outcome=$outcome decision=$result attempt=$runAttemptCount")
        result
    }
}
