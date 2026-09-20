package io.packetstream.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PacketStreamDashboardParserTest {
    private val now = 1_758_096_000_000L // 2025-09-17T00:00:00Z

    @Test
    fun `parses balance and sums only the last fourteen UTC days`() {
        val html = """
            <div class="metric-card metric-card-balance">
              <h2 class="metric-title">Balance</h2>
              <h2 class="default-font fw-600">${'$'}1,234.50</h2>
            </div>
            <div class="metric-card metric-card-sold">
              <p class="card-subtitle">Last 14 Days</p>
            </div>
            <script>
              const rd = "{\"exitnode\":[
                {\"bandwidth\":{\"up\":1000,\"down\":2000},\"timestamp\":\"2025-09-17T00:00:00Z\"},
                {\"bandwidth\":{\"up\":4000,\"down\":1000},\"timestamp\":\"2025-09-04T00:00:00Z\"},
                {\"bandwidth\":{\"up\":9000,\"down\":9000},\"timestamp\":\"2025-09-03T00:00:00Z\"}
              ]}";
            </script>
        """.trimIndent()

        val summary = PacketStreamDashboardParser.parse(html, now)

        assertEquals(8000L, summary.bandwidthBytes)
        assertEquals("1234.50", summary.balance)
        assertEquals(now, summary.fetchedAtMillis)
    }

    @Test
    fun `rejects a dashboard with the wrong period label`() {
        val html = validHtml().replace("Last 14 Days", "Last 7 Days")

        assertThrows(DashboardParseException::class.java) {
            PacketStreamDashboardParser.parse(html, now)
        }
    }

    @Test
    fun `rejects malformed report data`() {
        val html = validHtml().replace("const rd =", "const rd = \"not-json\" //")

        assertThrows(DashboardParseException::class.java) {
            PacketStreamDashboardParser.parse(html, now)
        }
    }

    @Test
    fun `parses dashboard when period label is uppercase LAST 14 DAYS`() {
        val html = validHtml().replace("Last 14 Days", "LAST 14 DAYS")

        val summary = PacketStreamDashboardParser.parse(html, now)

        assertEquals(0L, summary.bandwidthBytes)
        assertEquals("0.01", summary.balance)
    }

    @Test
    fun `parses dashboard when report data uses let instead of const`() {
        val html = validHtml().replace("const rd =", "let rd =")

        val summary = PacketStreamDashboardParser.parse(html, now)

        assertEquals(0L, summary.bandwidthBytes)
        assertEquals("0.01", summary.balance)
    }

    @Test
    fun `parses dashboard when report string contains escaped single quote`() {
        val html = validHtml().replace("{\"exitnode\":[]}", "{\"exitnode\":[],\"note\":\"user\\'s data\"}")

        val summary = PacketStreamDashboardParser.parse(html, now)

        assertEquals(0L, summary.bandwidthBytes)
        assertEquals("0.01", summary.balance)
    }

    @Test
    fun `parses harmless whitespace and attribute-order variations`() {
        val html = validHtml()
            .replace("class=\"metric-card-balance\"", "class = 'metric-card-balance extra'")
            .replace("class=\"default-font\"", "data-role=\"value\" class = 'fw-600 default-font'")
            .replace("Last 14 Days", "  LAST   14   DAYS  ")
            .replace("const rd =", "const   rd   =")

        val summary = PacketStreamDashboardParser.parse(html, now)

        assertEquals(0L, summary.bandwidthBytes)
        assertEquals("0.01", summary.balance)
    }

    private fun validHtml() = """
        <div class="metric-card-balance"><h2 class="metric-title">Balance</h2><h2 class="default-font">${'$'}0.01</h2></div>
        <div class="metric-card-sold"><p class="card-subtitle">Last 14 Days</p></div>
        <script>const rd = "{\"exitnode\":[]}";</script>
    """.trimIndent()
}
