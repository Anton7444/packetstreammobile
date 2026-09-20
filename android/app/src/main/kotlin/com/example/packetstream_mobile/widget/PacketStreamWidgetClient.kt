package com.example.packetstream_mobile.widget

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

sealed class WidgetFetchResult {
    data class Success(val summary: WidgetSummary, val cookieHeader: String) : WidgetFetchResult()
    data object MissingSession : WidgetFetchResult()
    data object SessionExpired : WidgetFetchResult()
    data class TemporaryFailure(val message: String) : WidgetFetchResult()
    data class ParserFailure(val message: String) : WidgetFetchResult()
}

class PacketStreamWidgetClient(
    private val connectionFactory: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
    private val userAgent: String = "PacketStreamWidget/Android",
) {
    fun fetch(cookieHeader: String, nowMillis: Long): WidgetFetchResult =
        if (cookieHeader.isBlank()) WidgetFetchResult.MissingSession else fetchFromUrl(DASHBOARD_URL, cookieHeader, nowMillis, 3)

    private fun fetchFromUrl(url: String, cookieHeader: String, nowMillis: Long, remaining: Int): WidgetFetchResult {
        val connection = try { connectionFactory(url) } catch (e: Exception) { return WidgetFetchResult.TemporaryFailure(e.javaClass.simpleName) }
        return try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            connection.setRequestProperty("Cookie", cookieHeader)
            val code = connection.responseCode
            WidgetLog.e("HTTP response path=${URI(url).path} code=$code")
            val setCookies = connection.headerFields.entries.firstOrNull { it.key?.equals("Set-Cookie", ignoreCase = true) == true }?.value.orEmpty()
            val nextCookies = CookieHeader.merge(cookieHeader, setCookies)
            val location = connection.getHeaderField("Location")
            if (code in 300..399) {
                if (isLoginLocation(location)) {
                    WidgetLog.i("redirect result=session_expired path=${location?.let { runCatching { URI(url).resolve(it).path }.getOrNull() }}")
                    WidgetFetchResult.SessionExpired
                }
                else if (remaining == 0) WidgetFetchResult.TemporaryFailure("Too many redirects")
                else {
                    val resolved = location?.let { runCatching { URI(url).resolve(it) }.getOrNull() }
                    if (resolved != null && isDashboardUrl(resolved)) {
                        WidgetLog.e("redirect path=${resolved.path}")
                        fetchFromUrl(resolved.toString(), nextCookies, nowMillis, remaining - 1)
                    }
                    else WidgetFetchResult.TemporaryFailure("Unexpected redirect")
                }
            } else if (code == 401 || code == 403) {
                WidgetLog.i("http result=session_expired status=$code")
                WidgetFetchResult.SessionExpired
            }
            else if (code != 200) WidgetFetchResult.TemporaryFailure("HTTP $code")
            else if (!isDashboardUrl(connection.url.toURI())) WidgetFetchResult.TemporaryFailure("Unexpected response origin")
            else {
                WidgetLog.e("parser started")
                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                WidgetLog.e(
                    "response body length=${html.length} " +
                        "contains metric-card-balance=${html.contains("metric-card-balance", ignoreCase = true)} " +
                        "contains reportData=${html.contains("reportData", ignoreCase = true)} " +
                        "contains exitnode=${html.contains("exitnode", ignoreCase = true)}",
                )
                val summary = try {
                    PacketStreamDashboardParser.parse(html, nowMillis)
                } catch (error: DashboardParseException) {
                    throw error
                } catch (error: Throwable) {
                    throw DashboardParseException("Dashboard parser failed", error)
                }
                WidgetLog.e("parser success")
                WidgetFetchResult.Success(summary, nextCookies)
            }
        } catch (e: DashboardParseException) {
            WidgetLog.e("parser failure message=${WidgetLog.safeMessage(e.message)}")
            WidgetFetchResult.ParserFailure(e.message ?: "Dashboard format changed")
        } catch (e: Exception) {
            WidgetLog.e("HTTP/request failure class=${e.javaClass.simpleName} message=${WidgetLog.safeMessage(e.message)}")
            WidgetFetchResult.TemporaryFailure("${e.javaClass.simpleName}: ${e.message ?: "no message"}")
        } catch (e: Throwable) {
            WidgetLog.e("parser failure class=${e.javaClass.simpleName} message=${WidgetLog.safeMessage(e.message)}")
            WidgetFetchResult.ParserFailure("Dashboard parser failed: ${e.javaClass.simpleName}")
        } finally { connection.disconnect() }
    }

    private fun isLoginLocation(location: String?): Boolean = location?.let { runCatching { URI(DASHBOARD_URL).resolve(it) }.getOrNull() }?.let { it.scheme == "https" && it.host == HOST && it.path?.startsWith("/login") == true } == true
    private fun isDashboardUrl(uri: URI): Boolean = uri.scheme == "https" && uri.host == HOST && (uri.path == "/dashboard" || uri.path == "/dashboard/")
    private companion object { const val HOST = "app.packetstream.io"; const val DASHBOARD_URL = "https://app.packetstream.io/dashboard" }
}

object CookieHeader {
    fun requestPairs(header: String): List<String> = header.split(';')
        .map(String::trim)
        .filter { pair -> pair.indexOf('=') > 0 }

    fun merge(existing: String, setCookies: List<String>): String {
        val values = linkedMapOf<String, String>()
        existing.split(';').map(String::trim).forEach { pair -> val i = pair.indexOf('='); if (i > 0) values[pair.substring(0, i)] = pair.substring(i + 1) }
        setCookies.forEach { header ->
            val first = header.substringBefore(';').trim(); val i = first.indexOf('='); if (i <= 0) return@forEach
            val name = first.substring(0, i); val value = first.substring(i + 1)
            val parsed = runCatching { java.net.HttpCookie.parse(header).firstOrNull() }.getOrNull()
            val deleted = parsed?.maxAge == 0L || parsed?.hasExpired() == true
            if (deleted || value.isEmpty()) values.remove(name) else values[name] = value
        }
        return values.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
