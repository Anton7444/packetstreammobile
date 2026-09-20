package com.example.packetstream_mobile.widget

import android.content.Context
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.util.concurrent.atomic.AtomicBoolean

/** Runs one widget refresh without starting Flutter or an Activity. */
object PacketStreamWidgetRefresh {
    val isRunning: Boolean
        get() = inFlight.get()

    fun run(context: Context): RefreshOutcome {
        if (!inFlight.compareAndSet(false, true)) return RefreshOutcome.TEMPORARY_FAILURE
        val applicationContext = context.applicationContext
        val store = PacketStreamWidgetStore(applicationContext)
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val previousState = store.read()
            WidgetLog.e("encrypted session available=${store.hasEncryptedCookie()} cookie decrypt succeeded=${previousState.cookieHeader != null}")
            store.saveStatus(WidgetStatus.REFRESHING)
            PacketStreamWidgetProvider.updateAll(applicationContext)
            var cookieHeader = previousState.cookieHeader
            if (cookieHeader.isNullOrBlank() && store.canImportWebViewCookies()) {
                val cookieFromManager = runCatching {
                    android.webkit.CookieManager.getInstance().getCookie("https://app.packetstream.io/dashboard")
                        ?.takeIf { it.isNotBlank() }
                        ?: android.webkit.CookieManager.getInstance().getCookie("https://app.packetstream.io")
                }.getOrNull()?.takeIf { it.isNotBlank() }
                if (cookieFromManager != null) {
                    cookieHeader = cookieFromManager
                }
            }
            val result = cookieHeader?.let {
                WidgetLog.e("HTTP request starting path=/dashboard")
                PacketStreamWidgetClient(userAgent = runCatching { WebSettings.getDefaultUserAgent(applicationContext) }.getOrDefault("PacketStreamWidget/Android")).fetch(it, System.currentTimeMillis())
            } ?: WidgetFetchResult.MissingSession
            // Sign-out can clear the store while a request is in flight. Do not
            // write a result from the old session back into the widget.
            val currentState = store.read()
            val hadPreviousSession = previousState.cookieHeader != null
            val hasCurrentSession = currentState.cookieHeader != null
            if (hadPreviousSession && !hasCurrentSession) {
                val terminalStatus = PacketStreamWidgetRefreshPolicy.raceTerminalStatus(hasCurrentSession)
                WidgetLog.i("session race hadPreviousSession=true hasCurrentSession=false terminal=$terminalStatus")
                store.saveStatus(terminalStatus)
                return RefreshOutcome.NO_SESSION
            }
            WidgetLog.d("session race hadPreviousSession=$hadPreviousSession hasCurrentSession=$hasCurrentSession")
            when (result) {
                is WidgetFetchResult.Success -> {
                    runCatching {
                        val cookieManager = CookieManager.getInstance()
                        CookieHeader.requestPairs(result.cookieHeader).forEach { pair ->
                            cookieManager.setCookie("https://app.packetstream.io", pair)
                        }
                        cookieManager.flush()
                    }
                    if (cookieHeader != null) {
                        store.saveSession(result.cookieHeader, result.summary)
                    } else {
                        store.saveSummary(result.summary)
                    }
                }
                WidgetFetchResult.MissingSession -> {
                    if (previousState.status != WidgetStatus.SESSION_EXPIRED) {
                        store.saveStatus(WidgetStatus.NO_SESSION)
                    }
                }
                WidgetFetchResult.SessionExpired -> {
                    store.markSessionExpired()
                    store.saveStatus(WidgetStatus.SESSION_EXPIRED)
                }
                is WidgetFetchResult.TemporaryFailure -> {
                    WidgetLog.e("OFFLINE selected reason=temporary_failure message=${WidgetLog.safeMessage(result.message)}")
                    store.saveStatus(WidgetStatus.OFFLINE)
                }
                is WidgetFetchResult.ParserFailure -> {
                    WidgetLog.e("OFFLINE selected reason=parser_failure message=${WidgetLog.safeMessage(result.message)}")
                    store.saveStatus(WidgetStatus.OFFLINE)
                }
            }
            when (result) {
                is WidgetFetchResult.TemporaryFailure -> WidgetLog.w("refresh result=temporary_failure message=${WidgetLog.safeMessage(result.message)}")
                is WidgetFetchResult.ParserFailure -> WidgetLog.w("refresh result=parser_failure message=${WidgetLog.safeMessage(result.message)}")
                WidgetFetchResult.MissingSession -> WidgetLog.i("refresh result=missing_session")
                WidgetFetchResult.SessionExpired -> WidgetLog.i("refresh result=session_expired")
                is WidgetFetchResult.Success -> WidgetLog.i("refresh result=success")
            }
            return when (result) {
                is WidgetFetchResult.Success -> RefreshOutcome.SUCCESS
                WidgetFetchResult.MissingSession -> RefreshOutcome.NO_SESSION
                WidgetFetchResult.SessionExpired -> RefreshOutcome.SESSION_EXPIRED
                is WidgetFetchResult.TemporaryFailure -> RefreshOutcome.TEMPORARY_FAILURE
                is WidgetFetchResult.ParserFailure -> RefreshOutcome.PARSER_FAILURE
            }
        } catch (error: Exception) {
            // Keep the previous summary visible if storage or networking is unavailable.
            WidgetLog.w("refresh exception class=${error.javaClass.simpleName} message=${WidgetLog.safeMessage(error.message)}")
            WidgetLog.e("OFFLINE selected reason=caught_exception")
            runCatching { store.saveStatus(WidgetStatus.OFFLINE) }
            return RefreshOutcome.TEMPORARY_FAILURE
        } finally {
            runCatching {
                val terminalStatus = PacketStreamWidgetRefreshPolicy.finalTerminalStatus(store.read())
                if (terminalStatus != null) {
                    WidgetLog.w("final guard converted REFRESHING to $terminalStatus")
                    store.saveStatus(terminalStatus)
                }
                WidgetLog.e("final persisted WidgetStatus=${store.read().status}")
            }
            val remaining = MIN_VISIBLE_REFRESH_MILLIS -
                (SystemClock.elapsedRealtime() - startedAt)
            if (remaining > 0) {
                runCatching { Thread.sleep(remaining) }
            }
            runCatching { PacketStreamWidgetProvider.updateAll(applicationContext) }
            inFlight.set(false)
        }
    }

    private val inFlight = AtomicBoolean(false)

    private const val MIN_VISIBLE_REFRESH_MILLIS = 450L
}

enum class RefreshOutcome { SUCCESS, NO_SESSION, SESSION_EXPIRED, TEMPORARY_FAILURE, PARSER_FAILURE }
