package com.example.packetstream_mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketStreamWidgetIntervalsTest {
    @Test
    fun onlyTheSettingsFrequenciesAreAccepted() {
        assertTrue(PacketStreamWidgetIntervals.isAllowed(15L))
        assertTrue(PacketStreamWidgetIntervals.isAllowed(720L))
        assertFalse(PacketStreamWidgetIntervals.isAllowed(17L))
        assertEquals(60L, PacketStreamWidgetIntervals.normalize(17L))
    }
}
