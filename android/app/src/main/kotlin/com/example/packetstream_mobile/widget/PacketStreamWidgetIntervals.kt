package com.example.packetstream_mobile.widget

object PacketStreamWidgetIntervals {
    const val DEFAULT_MINUTES = 60L
    val allowedMinutes = longArrayOf(15L, 30L, 60L, 180L, 360L, 720L)

    fun isAllowed(minutes: Long): Boolean = allowedMinutes.contains(minutes)

    fun normalize(minutes: Long): Long =
        if (isAllowed(minutes)) minutes else DEFAULT_MINUTES
}
