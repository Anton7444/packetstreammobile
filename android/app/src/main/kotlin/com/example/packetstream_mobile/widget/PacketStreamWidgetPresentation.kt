package com.example.packetstream_mobile.widget

data class RefreshControlVisibility(
    val showIcon: Boolean,
    val showProgress: Boolean,
)

object PacketStreamWidgetPresentation {
    fun refreshControl(status: WidgetStatus): RefreshControlVisibility =
        if (status == WidgetStatus.REFRESHING) {
            RefreshControlVisibility(showIcon = false, showProgress = true)
        } else {
            RefreshControlVisibility(showIcon = true, showProgress = false)
        }
}
