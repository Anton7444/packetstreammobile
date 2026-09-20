package com.example.packetstream_mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class PacketStreamWidgetClientTest {
    @Test fun `cookie replacement addition and deletion`() {
        assertEquals("sid=new; theme=dark; other=x", CookieHeader.merge("sid=old; theme=dark", listOf("sid=new; Path=/", "other=x; Secure")))
        assertEquals("theme=dark", CookieHeader.merge("sid=old; theme=dark", listOf("sid=gone; Max-Age=0; Path=/")))
        assertEquals("theme=dark", CookieHeader.merge("sid=old; theme=dark", listOf("sid=gone; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")))
    }

    @Test fun `request cookie header is split into individual cookies`() {
        assertEquals(listOf("sid=new", "theme=dark"), CookieHeader.requestPairs("sid=new; theme=dark"))
    }

    @Test fun `session disappearance during refresh has terminal state`() {
        assertEquals(WidgetStatus.NO_SESSION, PacketStreamWidgetRefreshPolicy.raceTerminalStatus(false))
        assertEquals(WidgetStatus.NO_SESSION, PacketStreamWidgetRefreshPolicy.finalTerminalStatus(WidgetState(null, null, WidgetStatus.REFRESHING)))
        assertEquals(null, PacketStreamWidgetRefreshPolicy.finalTerminalStatus(WidgetState(null, null, WidgetStatus.NO_SESSION)))
    }

    @Test fun `auth failures and login redirect expire session`() {
        assertIs<WidgetFetchResult.SessionExpired>(client(FakeConnection(401)).fetch("sid=x", 1))
        assertIs<WidgetFetchResult.SessionExpired>(client(FakeConnection(403)).fetch("sid=x", 1))
        assertIs<WidgetFetchResult.SessionExpired>(client(FakeConnection(302, location = "/login")).fetch("sid=x", 1))
    }

    @Test fun `network and parser failures are explicit`() {
        val network = PacketStreamWidgetClient(connectionFactory = { throw java.net.SocketTimeoutException("timed out") })
        assertIs<WidgetFetchResult.TemporaryFailure>(network.fetch("sid=x", 1))
        assertIs<WidgetFetchResult.ParserFailure>(client(FakeConnection(200, body = "bad")).fetch("sid=x", 1))
    }

    @Test fun `successful same origin redirect propagates cookies`() {
        val first = FakeConnection(302, location = "/dashboard/", setCookies = listOf("sid=new; Path=/"))
        val second = FakeConnection(200, body = validHtml())
        val connections = ArrayDeque(listOf(first, second))
        assertIs<WidgetFetchResult.Success>(PacketStreamWidgetClient(connectionFactory = { connections.removeFirst() }).fetch("sid=old", 1))
        assertEquals("sid=new", second.sentProperties["Cookie"])
    }

    @Test fun `external redirect and redirect limit are rejected`() {
        assertIs<WidgetFetchResult.TemporaryFailure>(client(FakeConnection(302, location = "https://evil.example/")).fetch("sid=x", 1))
        val connections = ArrayDeque(listOf(FakeConnection(302, location = "/dashboard/"), FakeConnection(302, location = "/dashboard/"), FakeConnection(302, location = "/dashboard/"), FakeConnection(302, location = "/dashboard/")))
        assertIs<WidgetFetchResult.TemporaryFailure>(PacketStreamWidgetClient(connectionFactory = { connections.removeFirst() }).fetch("sid=x", 1))
    }

    private fun client(connection: FakeConnection) = PacketStreamWidgetClient(connectionFactory = { connection })
    private fun validHtml() = "<div class=\"metric-card-balance\"><h2 class=\"default-font\">${'$'}1.00</h2></div><div class=\"metric-card-sold\"><p class=\"card-subtitle\">Last 14 Days</p></div><script>const rd = \"{\\\"exitnode\\\":[]}\";</script>"
    private inline fun <reified T> assertIs(value: Any?) { assertTrue("Expected ${T::class.simpleName}, got $value", value is T) }

    private class FakeConnection(private val code: Int, private val location: String? = null, private val body: String = "", private val setCookies: List<String> = emptyList()) : HttpURLConnection(URL("https://app.packetstream.io/dashboard")) {
        val sentProperties = mutableMapOf<String, String>()
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getResponseCode() = code
        override fun getHeaderField(name: String): String? = if (name == "Location") location else null
        override fun getHeaderFields(): MutableMap<String, MutableList<String>> = mutableMapOf("Set-Cookie" to setCookies.toMutableList())
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun setRequestProperty(key: String, value: String) { sentProperties[key] = value }
    }
}
