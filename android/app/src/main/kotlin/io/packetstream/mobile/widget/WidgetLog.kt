package io.packetstream.mobile.widget

import android.util.Log

object WidgetLog {
    fun d(message: String) = runCatching { Log.d("PacketStreamWidget", message) }
    fun i(message: String) = runCatching { Log.i("PacketStreamWidget", message) }
    fun w(message: String) = runCatching { Log.w("PacketStreamWidget", message) }
    fun e(message: String) = runCatching { Log.e("PacketStreamWidget", message) }
    fun safeMessage(message: String?): String = (message ?: "no message")
        .replace(Regex("(?i)(cookie|authorization|token)\\s*[:=]\\s*\\S+"), "$1=<redacted>")
}
