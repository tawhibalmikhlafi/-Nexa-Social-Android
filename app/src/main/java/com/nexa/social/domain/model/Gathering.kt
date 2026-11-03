package com.nexa.social.domain.model

/**
 * A real-world or virtual gathering created from Nexa. Gatherings are stored on this device
 * until the server-backed events service is connected; RSVPs therefore represent local intent only.
 */
data class Gathering(
    val id: String,
    val title: String,
    val description: String,
    val hostId: String,
    val hostName: String,
    val location: String,
    val isVirtual: Boolean,
    val startsAt: Long,
    val durationMinutes: Int = 60,
    val capacity: Int = 0,
    val attendeeIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val isMine: Boolean = false
) {
    fun isPast(now: Long = System.currentTimeMillis()): Boolean = startsAt <= now

    fun isFull(): Boolean = capacity > 0 && attendeeIds.size >= capacity

    fun isAttending(userId: String?): Boolean =
        userId != null && attendeeIds.contains(userId)

    fun seatsLeft(): Int? =
        if (capacity <= 0) null else (capacity - attendeeIds.size).coerceAtLeast(0)

    fun endsAt(): Long = startsAt + durationMinutes * 60_000L
}
