package com.nexa.social.data.remote

import com.google.gson.JsonObject
import com.nexa.social.core.supabase.string
import com.nexa.social.domain.model.SessionDeviceKind
import com.nexa.social.domain.model.UserSession
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Wire shape returned by the `get_my_sessions` RPC. */
internal data class SessionDto(
    val id: String,
    val userId: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val refreshedAt: String?,
    val notAfter: String?,
    val userAgent: String?,
    val ip: String?,
) {
    fun toDomain(isCurrent: Boolean): UserSession {
        val device = describeAgent(userAgent)
        return UserSession(
            id = id,
            userId = userId,
            deviceName = device.name,
            deviceKind = device.kind,
            ipAddress = ip?.takeIf { it.isNotBlank() }?.let(::maskIp),
            createdAt = parseTimestamp(createdAt),
            refreshedAt = parseTimestamp(refreshedAt),
            lastActiveAt = parseTimestamp(updatedAt),
            expiresAt = parseTimestamp(notAfter),
            isCurrent = isCurrent,
        )
    }

    companion object {
        fun fromJson(obj: JsonObject): SessionDto? {
            val id = obj.string("id") ?: return null
            return SessionDto(
                id = id,
                userId = obj.string("user_id"),
                createdAt = obj.string("created_at"),
                updatedAt = obj.string("updated_at"),
                refreshedAt = obj.string("refreshed_at"),
                notAfter = obj.string("not_after"),
                userAgent = obj.string("user_agent"),
                ip = obj.string("ip"),
            )
        }
    }
}

internal data class DeviceDescription(
    val name: String,
    val kind: SessionDeviceKind,
)

/**
 * Maps the recorded `user_agent` to a human label. It deliberately does not infer a phone model,
 * marketing name or location because those are not present in the value.
 */
internal fun describeAgent(userAgent: String?): DeviceDescription {
    val raw = userAgent.orEmpty().trim()
    if (raw.isEmpty()) return DeviceDescription("جهاز غير معروف", SessionDeviceKind.OTHER)

    val ua = raw.lowercase()
    return when {
        ua.startsWith("okhttp/") ->
            DeviceDescription("تطبيق Nexa على Android", SessionDeviceKind.ANDROID_APP)

        ua.contains("samsungbrowser/") ->
            DeviceDescription("متصفح Samsung Internet", SessionDeviceKind.BROWSER)

        ua.contains("edg/") || ua.contains("edga/") || ua.contains("edgios/") ->
            DeviceDescription("متصفح Edge", SessionDeviceKind.BROWSER)

        ua.contains("opr/") || ua.contains("opera") ->
            DeviceDescription("متصفح Opera", SessionDeviceKind.BROWSER)

        ua.contains("firefox/") || ua.contains("fxios/") ->
            DeviceDescription(
                if (ua.contains("android")) "متصفح Firefox على Android" else "متصفح Firefox",
                SessionDeviceKind.BROWSER,
            )

        ua.contains("chrome/") || ua.contains("crios/") ->
            DeviceDescription(
                if (ua.contains("android")) "متصفح Chrome على Android" else "متصفح Chrome",
                SessionDeviceKind.CHROME,
            )

        ua.contains("safari/") && (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) ->
            DeviceDescription("متصفح Safari على iOS", SessionDeviceKind.BROWSER)

        ua.contains("safari/") ->
            DeviceDescription("متصفح Safari", SessionDeviceKind.BROWSER)

        ua.contains("android") ->
            DeviceDescription("متصفح على Android", SessionDeviceKind.BROWSER)

        ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod") ->
            DeviceDescription("متصفح على iOS", SessionDeviceKind.BROWSER)

        ua.contains("windows") ->
            DeviceDescription("متصفح على Windows", SessionDeviceKind.BROWSER)

        ua.contains("macintosh") || ua.contains("mac os") ->
            DeviceDescription("متصفح على macOS", SessionDeviceKind.BROWSER)

        ua.contains("linux") ->
            DeviceDescription("متصفح على Linux", SessionDeviceKind.BROWSER)

        else -> {
            val product = raw.substringBefore(' ')
                .substringBefore('/')
                .takeIf { token ->
                    token.length in 2..24 && token.all { it.isLetterOrDigit() || it in "-_." }
                }
            DeviceDescription(
                product?.let { "تطبيق أو متصفح: $it" } ?: "جهاز متصل",
                SessionDeviceKind.OTHER,
            )
        }
    }
}

/** Masks the host portion of an IP address so the screen stays useful without exposing it fully. */
internal fun maskIp(raw: String): String {
    val value = raw.substringBefore('%').trim()
    if (value.contains(':')) {
        val groups = value.split(':').filter { it.isNotEmpty() }
        return if (groups.size >= 4) groups.take(4).joinToString(":") + ":****" else value
    }
    val parts = value.split('.')
    return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.***" else value
}

private fun parseTimestamp(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .getOrNull()
}
