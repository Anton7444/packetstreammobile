package com.example.packetstream_mobile

import android.webkit.CookieManager
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import com.example.packetstream_mobile.widget.PacketStreamWidgetProvider
import com.example.packetstream_mobile.widget.PacketStreamWidgetScheduler
import com.example.packetstream_mobile.widget.PacketStreamWidgetStore
import com.example.packetstream_mobile.widget.PacketStreamWidgetIntervals
import com.example.packetstream_mobile.widget.WidgetSummary

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, WIDGET_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "syncWidgetSummary" -> syncWidgetSummary(call, result)
                    "clearWidgetSession" -> {
                        val store = PacketStreamWidgetStore(this)
                        val previousSchedule = store.readSchedule()
                        store.clear()
                        store.saveSchedule(false, previousSchedule.intervalMinutes)
                        PacketStreamWidgetScheduler.apply(this, false, previousSchedule.intervalMinutes)
                        PacketStreamWidgetProvider.updateAll(this)
                        result.success(null)
                    }
                    "getWidgetSchedule" -> {
                        val schedule = PacketStreamWidgetStore(this).readSchedule()
                        result.success(
                            mapOf(
                                "enabled" to schedule.enabled,
                                "intervalMinutes" to schedule.intervalMinutes,
                            ),
                        )
                    }
                    "getWidgetSummary" -> {
                        val summary = PacketStreamWidgetStore(this).read().summary
                        result.success(
                            summary?.let {
                                mapOf(
                                    "bytes" to it.bandwidthBytes,
                                    "balance" to it.balance,
                                    "fetchedAt" to it.fetchedAtMillis,
                                )
                            },
                        )
                    }
                    "setWidgetSchedule" -> setWidgetSchedule(call, result)
                    else -> result.notImplemented()
                }
            }
    }

    private fun syncWidgetSummary(call: MethodCall, result: MethodChannel.Result) {
        val bytes = call.argument<Number>("bytes")?.toLong()
        val balance = call.argument<String>("balance")
        val fetchedAt = call.argument<Number>("fetchedAt")?.toLong()
        if (bytes == null || bytes < 0 || balance.isNullOrBlank() || fetchedAt == null || fetchedAt < 0) {
            result.error("INVALID_SUMMARY", "Widget summary is invalid", null)
            return
        }
        runCatching { CookieManager.getInstance().flush() }
        val cookieHeader = CookieManager.getInstance().getCookie("https://app.packetstream.io/dashboard")
            ?.takeIf { it.isNotBlank() }
            ?: CookieManager.getInstance().getCookie(DASHBOARD_ORIGIN).orEmpty()
        val summary = WidgetSummary(bytes, balance, fetchedAt)
        val store = PacketStreamWidgetStore(this)
        val current = store.read().summary
        if (current == null || summary.fetchedAtMillis >= current.fetchedAtMillis) {
            if (cookieHeader.isBlank()) store.saveSummary(summary) else store.saveSession(cookieHeader, summary)
        }
        PacketStreamWidgetProvider.updateAll(this)
        result.success(null)
    }

    private fun setWidgetSchedule(call: MethodCall, result: MethodChannel.Result) {
        val enabled = call.argument<Boolean>("enabled")
        val intervalMinutes = call.argument<Number>("intervalMinutes")?.toLong()
        if (enabled == null || intervalMinutes == null || !PacketStreamWidgetIntervals.isAllowed(intervalMinutes)) {
            result.error("INVALID_SCHEDULE", "Widget schedule is invalid", null)
            return
        }
        try {
            val store = PacketStreamWidgetStore(this)
            val previous = store.readSchedule()
            store.saveSchedule(enabled, intervalMinutes)
            try {
                PacketStreamWidgetScheduler.apply(this, enabled, intervalMinutes)
            } catch (error: Exception) {
                store.saveSchedule(previous.enabled, previous.intervalMinutes)
                throw error
            }
            result.success(null)
        } catch (error: Exception) {
            result.error("SCHEDULE_FAILED", error.javaClass.simpleName, null)
        }
    }

    private companion object {
        const val WIDGET_CHANNEL = "io.packetstream.mobile/widget"
        const val DASHBOARD_ORIGIN = "https://app.packetstream.io"
    }
}
