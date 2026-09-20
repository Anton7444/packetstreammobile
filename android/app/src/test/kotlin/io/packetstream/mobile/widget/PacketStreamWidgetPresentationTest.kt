package io.packetstream.mobile.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketStreamWidgetPresentationTest {
    @Test
    fun refreshingUsesProgressAndTerminalStatesUseTheIcon() {
        val refreshing = PacketStreamWidgetPresentation.refreshControl(WidgetStatus.REFRESHING)
        assertFalse(refreshing.showIcon)
        assertTrue(refreshing.showProgress)

        listOf(
            WidgetStatus.NO_SESSION,
            WidgetStatus.READY,
            WidgetStatus.OFFLINE,
            WidgetStatus.SESSION_EXPIRED,
        ).forEach { status ->
            val terminal = PacketStreamWidgetPresentation.refreshControl(status)
            assertTrue(terminal.showIcon)
            assertFalse(terminal.showProgress)
        }
    }

    @Test
    fun formatBytesUsesTheActualByteCount() {
        org.junit.Assert.assertEquals("823 Bytes", PacketStreamWidgetProvider.formatBytes(823L))
        org.junit.Assert.assertEquals("469.7 KB", PacketStreamWidgetProvider.formatBytes(481000L))
        org.junit.Assert.assertEquals("459.7 MB", PacketStreamWidgetProvider.formatBytes(482000000L))
        org.junit.Assert.assertEquals("548.7 MB", PacketStreamWidgetProvider.formatBytes(575400000L))
        org.junit.Assert.assertEquals("1.322 GB", PacketStreamWidgetProvider.formatBytes(1420000000L))
    }
}
