package com.example.packetstream_mobile.widget

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class WidgetSummary(val bandwidthBytes: Long, val balance: String, val fetchedAtMillis: Long)

class DashboardParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

object PacketStreamDashboardParser {
    private val balanceElement = Regex("metric-card-balance[\\s\\S]*?<h2[^>]*class=\"[^\"]*default-font[^\"]*\"[^>]*>[\\s\\S]*?</h2>", RegexOption.IGNORE_CASE)
    private val balanceValue = Regex("\\$([0-9,]+(?:\\.[0-9]+)?)")
    private val soldPeriod = Regex("metric-card-sold[\\s\\S]*?Last\\s+14\\s+Days", RegexOption.IGNORE_CASE)
    private val reportAssignment = Regex("(?:(?:const|var|let)\\s+rd|window\\.reportData)\\s*=", RegexOption.IGNORE_CASE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }

    fun parse(html: String, nowMillis: Long): WidgetSummary {
        WidgetLog.e("PARSER_BUILD_V3_ENTERED")
        var stage = "balance element"
        try {
            val balanceMatch = balanceElement.find(html)
            WidgetLog.e("balance element matched=${balanceMatch != null}")
            if (balanceMatch == null) throw DashboardParseException("balance element stage failed")
            stage = "balance text"
            val balance = balanceValue.find(balanceMatch.value)?.groupValues?.get(1)
            WidgetLog.e("balance text extracted=${balance != null}")
            if (balance == null) throw DashboardParseException("balance text stage failed")
            if (!soldPeriod.containsMatchIn(html)) throw DashboardParseException("dashboard period label missing")

            stage = "reportData assignment"
            val assignment = reportAssignment.find(html)
            WidgetLog.e("reportData assignment matched=${assignment != null}")
            if (assignment == null) throw DashboardParseException("reportData assignment stage failed")
            stage = "reportData object"
            val extracted = extractAssignedValue(html, assignment.range.last + 1)
            WidgetLog.e("reportData object extracted=true")
            stage = "reportData JSON"
            val exitnodeArray = extractNamedArray(extracted, "exitnode")
            WidgetLog.e("reportData JSON parsed=true")
            stage = "exitnode"
            val rows = extractObjects(exitnodeArray)
            WidgetLog.e("exitnode parsed=true")
            WidgetLog.e("exitnode row count=${rows.size}")
            stage = "rows"
            val total = sumRows(rows, nowMillis)
            stage = "summary"
            val summary = WidgetSummary(total, balance.replace(",", ""), nowMillis)
            WidgetLog.e("final summary parsed=true")
            return summary
        } catch (error: DashboardParseException) {
            WidgetLog.e("parser stage=$stage failed class=${error.javaClass.simpleName} message=${WidgetLog.safeMessage(error.message)}")
            throw error
        } catch (error: Throwable) {
            WidgetLog.e("parser stage=$stage failed class=${error.javaClass.simpleName} message=${WidgetLog.safeMessage(error.message)}")
            throw DashboardParseException("Parser stage failed: $stage", error)
        }
    }

    private fun sumRows(rows: List<String>, nowMillis: Long): Long {
        val today = utcDay(nowMillis)
        var total = 0L
        for ((index, row) in rows.withIndex()) {
            val bandwidth = extractNamedObject(row, "bandwidth")
            val up = numberField(bandwidth, "up")
            val down = numberField(bandwidth, "down")
            val timestamp = stringField(row, "timestamp")
            if (up < 0 || down < 0 || timestamp.isBlank()) throw DashboardParseException("row $index fields invalid")
            val date = try { dateFormat.parse(timestamp.substring(0, 10)) ?: throw ParseException(timestamp, 0) }
            catch (error: Exception) { throw DashboardParseException("row $index timestamp invalid", error) }
            val day = ((today - utcDay(date.time)) / DAY_MILLIS).toInt()
            if (day in 0..13) total = try { Math.addExact(total, Math.addExact(up, down)) } catch (e: ArithmeticException) { throw DashboardParseException("bandwidth total overflows", e) }
        }
        return total
    }

    private fun extractNamedArray(json: String, name: String): String {
        val marker = "\"$name\""; val key = json.indexOf(marker)
        if (key < 0) throw DashboardParseException("$name array missing")
        val start = json.indexOf('[', key + marker.length)
        if (start < 0) throw DashboardParseException("$name array missing")
        return extractBalanced(json, start, '[', ']')
    }

    private fun extractNamedObject(json: String, name: String): String {
        val marker = "\"$name\""; val key = json.indexOf(marker)
        if (key < 0) throw DashboardParseException("$name object missing")
        val start = json.indexOf('{', key + marker.length)
        if (start < 0) throw DashboardParseException("$name object missing")
        return extractBalanced(json, start, '{', '}')
    }

    private fun extractObjects(array: String): List<String> {
        val objects = mutableListOf<String>(); var index = 1
        while (index < array.lastIndex) {
            while (index < array.lastIndex && (array[index].isWhitespace() || array[index] == ',')) index++
            if (index >= array.lastIndex) break
            if (array[index] != '{') throw DashboardParseException("exitnode row is not an object")
            val row = extractBalanced(array, index, '{', '}'); objects += row; index += row.length
        }
        return objects
    }

    private fun stringField(json: String, name: String): String {
        val match = Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(json)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun numberField(json: String, name: String): Long =
        Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: -1L

    private fun extractBalanced(source: String, start: Int, open: Char, close: Char): String {
        var depth = 0; var inString = false; var escaped = false
        for (index in start until source.length) {
            val c = source[index]
            if (inString) { if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false; continue }
            if (c == '"') inString = true else if (c == open) depth++ else if (c == close && --depth == 0) return source.substring(start, index + 1)
        }
        throw DashboardParseException("JSON structure is unterminated")
    }

    private fun extractAssignedValue(source: String, start: Int): String {
        var index = start
        while (index < source.length && source[index].isWhitespace()) index++
        if (index >= source.length) throw DashboardParseException("reportData value missing")
        return if (source[index] == '"') decodeJavascriptString(source, index) else extractBalancedObject(source, index)
    }

    private fun decodeJavascriptString(source: String, start: Int): String {
        val result = StringBuilder(); var index = start + 1
        while (index < source.length) {
            val c = source[index++]
            if (c == '"') return result.toString()
            if (c != '\\' || index >= source.length) { result.append(c); continue }
            when (val escaped = source[index++]) {
                '"', '\\', '/' -> result.append(escaped)
                'b' -> result.append('\b'); 'f' -> result.append('\u000C'); 'n' -> result.append('\n')
                'r' -> result.append('\r'); 't' -> result.append('\t')
                'u' -> { if (index + 4 > source.length) throw DashboardParseException("unicode escape invalid"); result.append(source.substring(index, index + 4).toIntOrNull(16)?.toChar() ?: throw DashboardParseException("unicode escape invalid")); index += 4 }
                else -> result.append(escaped)
            }
        }
        throw DashboardParseException("reportData string unterminated")
    }

    private fun extractBalancedObject(source: String, start: Int): String {
        return extractBalanced(source, start, '{', '}')
    }

    private fun utcDay(millis: Long): Long { val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US); c.timeInMillis = millis; c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c.timeInMillis }
    private const val DAY_MILLIS = 86_400_000L
}
