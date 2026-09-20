package io.packetstream.mobile.widget

object PacketStreamWidgetRefreshPolicy {
    fun raceTerminalStatus(hasCurrentSession: Boolean): WidgetStatus =
        if (hasCurrentSession) WidgetStatus.OFFLINE else WidgetStatus.NO_SESSION

    fun finalTerminalStatus(state: WidgetState): WidgetStatus? =
        if (state.status != WidgetStatus.REFRESHING) null else raceTerminalStatus(state.cookieHeader != null)
}
