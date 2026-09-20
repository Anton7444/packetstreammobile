package com.example.packetstream_mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.Keep
import com.example.packetstream_mobile.MainActivity
import com.example.packetstream_mobile.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Keep
class PacketStreamWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, it) }
    }

    companion object {
        const val ACTION_OPEN_WIDGET = "io.packetstream.mobile.action.OPEN_WIDGET"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, PacketStreamWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateWidget(context, it) }
        }

        fun updateWidget(context: Context, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.packetstream_widget)
            val state = PacketStreamWidgetStore(context).read()
            val summary = state.summary
            val refreshVisibility = PacketStreamWidgetPresentation.refreshControl(state.status)
            views.setViewVisibility(
                R.id.widget_refresh,
                if (refreshVisibility.showIcon) View.VISIBLE else View.INVISIBLE,
            )
            views.setViewVisibility(
                R.id.widget_refresh_progress,
                if (refreshVisibility.showProgress) View.VISIBLE else View.GONE,
            )
            views.setTextViewText(
                R.id.widget_bandwidth,
                summary?.let { formatBytes(it.bandwidthBytes) } ?: "—",
            )
            views.setTextViewText(R.id.widget_balance, summary?.let { "$${it.balance}" } ?: "—")
            val statusMessage = statusText(state)
            if (statusMessage.isNullOrBlank()) {
                views.setViewVisibility(R.id.widget_status, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_status, View.VISIBLE)
                views.setTextViewText(R.id.widget_status, statusMessage)
            }

            val updatedText = summary?.let { formatUpdated(it.fetchedAtMillis) }
            if (updatedText.isNullOrBlank() || state.status == WidgetStatus.NO_SESSION || state.status == WidgetStatus.SESSION_EXPIRED) {
                views.setViewVisibility(R.id.widget_updated, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_updated, View.VISIBLE)
                views.setTextViewText(R.id.widget_updated, updatedText)
            }

            val refreshIntent = Intent(context, PacketStreamWidgetRefreshReceiver::class.java).apply {
                action = PacketStreamWidgetRefreshReceiver.ACTION_REFRESH_WIDGET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingRefreshIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, pendingRefreshIntent)
            views.setOnClickPendingIntent(R.id.widget_refresh_container, pendingRefreshIntent)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_WIDGET
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    OPEN_REQUEST_CODE,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }

        private fun statusText(state: WidgetState): String? = when (state.status) {
            WidgetStatus.NO_SESSION -> "Sign in to update"
            WidgetStatus.READY -> null
            WidgetStatus.REFRESHING -> "Refreshing…"
            WidgetStatus.OFFLINE -> "Offline · showing last update"
            WidgetStatus.SESSION_EXPIRED -> "Session expired · open app"
        }

        fun formatBytes(bytes: Long, precision: Int = 4): String {
            if (bytes < 0) return "0 Bytes"
            val adjusted = bytes * 1.074
            return when {
                adjusted < 1024 -> "${Math.round(adjusted)} Bytes"
                adjusted < 1048576 -> "${formatPrecision(adjusted / 1024, precision)} KB"
                adjusted < 1073741824 -> "${formatPrecision(adjusted / 1048576, precision)} MB"
                else -> "${formatPrecision(adjusted / 1073741824, precision)} GB"
            }
        }

        private fun formatPrecision(value: Double, precision: Int): String {
            if (value == 0.0) return "0"
            val digitsBeforeDecimal = if (value >= 1.0) Math.floor(Math.log10(value)).toInt() + 1 else 0
            val decimals = Math.max(0, precision - digitsBeforeDecimal)
            return String.format(Locale.US, "%.${decimals}f", value)
        }

        private fun formatUpdated(millis: Long): String {
            val formatter = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
            return "Updated ${formatter.format(Date(millis))}"
        }

        private const val OPEN_REQUEST_CODE = 50_001
    }
}
