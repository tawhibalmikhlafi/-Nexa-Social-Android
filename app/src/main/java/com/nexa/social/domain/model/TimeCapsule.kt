package com.nexa.social.domain.model

/**
 * A message sealed until a future date. The plaintext never touches disk: the local data source
 * stores only an AES/GCM payload and its IV, bound to the Android Keystore.
 */
data class TimeCapsule(
    val id: String,
    val title: String,
    val message: String,
    val createdAt: Long,
    val unlockAt: Long,
    val authorId: String,
    val authorName: String,
    val openedAt: Long? = null,
    val isEncrypted: Boolean = true
) {
    fun isLocked(now: Long = System.currentTimeMillis()): Boolean = now < unlockAt

    fun daysRemaining(now: Long = System.currentTimeMillis()): Int {
        val remaining = (unlockAt - now).coerceAtLeast(0L)
        return ((remaining + 86_399_999L) / 86_400_000L).toInt()
    }
}
