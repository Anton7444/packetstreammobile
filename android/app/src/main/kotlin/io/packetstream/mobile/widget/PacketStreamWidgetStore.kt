package io.packetstream.mobile.widget

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class WidgetStatus {
    NO_SESSION,
    READY,
    REFRESHING,
    OFFLINE,
    SESSION_EXPIRED,
}

data class WidgetState(
    val cookieHeader: String?,
    val summary: WidgetSummary?,
    val status: WidgetStatus,
)

data class WidgetSchedule(
    val enabled: Boolean,
    val intervalMinutes: Long,
)

class PacketStreamWidgetStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun saveSession(cookieHeader: String, summary: WidgetSummary) {
        preferences.edit()
            .putString(KEY_COOKIE, encrypt(cookieHeader))
            .putString(KEY_SUMMARY, encrypt(encodeSummary(summary)))
            .putString(KEY_STATUS, WidgetStatus.READY.name)
            .putBoolean(KEY_SESSION_EXPIRED, false)
            .putLong(KEY_STATUS_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun saveSummary(summary: WidgetSummary) {
        preferences.edit()
            .putString(KEY_SUMMARY, encrypt(encodeSummary(summary)))
            .putString(KEY_STATUS, WidgetStatus.READY.name)
            .putLong(KEY_STATUS_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun saveStatus(status: WidgetStatus) {
        preferences.edit()
            .putString(KEY_STATUS, status.name)
            .putLong(KEY_STATUS_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun saveSchedule(enabled: Boolean, intervalMinutes: Long) {
        preferences.edit()
            .putBoolean(KEY_BACKGROUND_ENABLED, enabled)
            .putLong(KEY_BACKGROUND_INTERVAL, PacketStreamWidgetIntervals.normalize(intervalMinutes))
            .apply()
    }

    fun readSchedule(): WidgetSchedule = WidgetSchedule(
        enabled = preferences.getBoolean(KEY_BACKGROUND_ENABLED, false),
        intervalMinutes = PacketStreamWidgetIntervals.normalize(
            preferences.getLong(
                KEY_BACKGROUND_INTERVAL,
                PacketStreamWidgetIntervals.DEFAULT_MINUTES,
            ),
        ),
    )

    fun clearCookie() {
        preferences.edit().remove(KEY_COOKIE).apply()
    }

    fun markSessionExpired() {
        preferences.edit()
            .remove(KEY_COOKIE)
            .putBoolean(KEY_SESSION_EXPIRED, true)
            .apply()
    }

    fun canImportWebViewCookies(): Boolean = !preferences.getBoolean(KEY_SESSION_EXPIRED, false)

    fun hasEncryptedCookie(): Boolean = preferences.getString(KEY_COOKIE, null) != null

    fun read(): WidgetState {
        val cookie = preferences.getString(KEY_COOKIE, null)?.let(::decrypt)
        val summary = preferences.getString(KEY_SUMMARY, null)?.let(::decrypt)?.let(::decodeSummary)
        val savedStatus = preferences.getString(KEY_STATUS, null)
            ?.let { name -> runCatching { WidgetStatus.valueOf(name) }.getOrNull() }
        val status = if (
            savedStatus == WidgetStatus.REFRESHING &&
            System.currentTimeMillis() - preferences.getLong(KEY_STATUS_UPDATED_AT, 0L) > REFRESH_STALE_MILLIS
        ) {
            if (cookie == null) WidgetStatus.NO_SESSION else WidgetStatus.OFFLINE
        } else {
            savedStatus ?: if (cookie == null) WidgetStatus.NO_SESSION else WidgetStatus.READY
        }
        return WidgetState(cookie, summary, status)
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_COOKIE)
            .remove(KEY_SUMMARY)
            .remove(KEY_STATUS)
            .remove(KEY_STATUS_UPDATED_AT)
            .remove(KEY_SESSION_EXPIRED)
            .apply()
    }

    private fun encodeSummary(summary: WidgetSummary): String =
        listOf(summary.bandwidthBytes, summary.balance, summary.fetchedAtMillis).joinToString(FIELD_SEPARATOR)

    private fun decodeSummary(value: String): WidgetSummary? {
        val fields = value.split(FIELD_SEPARATOR)
        if (fields.size != 3) return null
        val bytes = fields[0].toLongOrNull() ?: return null
        val timestamp = fields[2].toLongOrNull() ?: return null
        if (bytes < 0 || fields[1].isBlank() || timestamp < 0) return null
        return WidgetSummary(bytes, fields[1], timestamp)
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        if (payload.size <= GCM_IV_BYTES) return null
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = payload.copyOfRange(GCM_IV_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "packetstream_widget_secure"
        const val KEY_ALIAS = "packetstream_widget_aes"
        const val KEY_COOKIE = "cookie"
        const val KEY_SUMMARY = "summary"
        const val KEY_STATUS = "status"
        const val KEY_STATUS_UPDATED_AT = "status_updated_at"
        const val KEY_SESSION_EXPIRED = "session_expired"
        const val KEY_BACKGROUND_ENABLED = "background_enabled"
        const val KEY_BACKGROUND_INTERVAL = "background_interval_minutes"
        const val FIELD_SEPARATOR = "\u001f"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val REFRESH_STALE_MILLIS = 30_000L
    }
}
