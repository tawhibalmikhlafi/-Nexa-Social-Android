package com.nexa.social.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class GatheringTest {

    private fun gathering(
        startsAt: Long = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2),
        capacity: Int = 2,
        attendees: List<String> = listOf("host")
    ) = Gathering(
        id = "g1",
        title = "Test",
        description = "",
        hostId = "host",
        hostName = "Host",
        location = "Here",
        isVirtual = false,
        startsAt = startsAt,
        capacity = capacity,
        attendeeIds = attendees
    )

    @Test
    fun `past gatherings are detected from the start time`() {
        val past = gathering(startsAt = System.currentTimeMillis() - 1_000)
        assertTrue(past.isPast())
    }

    @Test
    fun `capacity is enforced when unlimited is not requested`() {
        val full = gathering(capacity = 1, attendees = listOf("host"))
        assertTrue(full.isFull())
        assertEquals(0, full.seatsLeft())

        val unlimited = gathering(capacity = 0, attendees = listOf("host"))
        assertFalse(unlimited.isFull())
        assertNull(unlimited.seatsLeft())
    }

    @Test
    fun `attendance respects the current user id`() {
        val item = gathering(attendees = listOf("host", "user-1"))
        assertTrue(item.isAttending("user-1"))
        assertFalse(item.isAttending("user-2"))
        assertFalse(item.isAttending(null))
    }
}

class TimeCapsuleTest {

    @Test
    fun `capsule stays locked until its unlock date`() {
        val now = 1_000_000L
        val capsule = TimeCapsule(
            id = "c1",
            title = "Later",
            message = "Hello",
            createdAt = now,
            unlockAt = now + TimeUnit.DAYS.toMillis(3),
            authorId = "user",
            authorName = "User"
        )
        assertTrue(capsule.isLocked(now))
        assertEquals(3, capsule.daysRemaining(now))
        assertFalse(capsule.isLocked(now + TimeUnit.DAYS.toMillis(3)))
    }
}
