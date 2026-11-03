package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.domain.model.Gathering
import java.util.concurrent.TimeUnit

/** Persists device-local gatherings and seeds a small, clearly-labelled sample program. */
class GatheringLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "gatherings.json"
    }

    fun readGatherings(): List<Gathering> {
        if (!store.exists(FILE)) {
            store.writeList(FILE, seed())
        }
        return store.readList<Gathering>(FILE)
    }

    fun writeGatherings(gatherings: List<Gathering>) = store.writeList(FILE, gatherings)

    private fun seed(): List<Gathering> {
        val now = System.currentTimeMillis()
        fun inDays(days: Long, hour: Int): Long {
            val base = now + TimeUnit.DAYS.toMillis(days)
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = base
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            return calendar.timeInMillis
        }
        return listOf(
            Gathering(
                id = "sample_gathering_design",
                title = "Design Systems of 2030",
                description = "A virtual room about adaptive interfaces, ambient computing and accessible design systems.",
                hostId = "sample_layla",
                hostName = "ليلى حسن",
                location = "Nexa Virtual Room",
                isVirtual = true,
                startsAt = inDays(2, 20),
                durationMinutes = 75,
                capacity = 200,
                attendeeIds = listOf("sample_sara", "sample_khaled"),
                tags = listOf("design", "future"),
                createdAt = now - TimeUnit.DAYS.toMillis(3)
            ),
            Gathering(
                id = "sample_gathering_coffee",
                title = "قهوة وتقنية",
                description = "لقاء محلي غير رسمي لبناء المنتجات ومشاركة التجارب.",
                hostId = "sample_ahmed",
                hostName = "أحمد محمد",
                location = "الرياض · مساحة نكسا",
                isVirtual = false,
                startsAt = inDays(5, 18),
                durationMinutes = 120,
                capacity = 40,
                attendeeIds = listOf("sample_fatima", "sample_mohammed", "sample_layla"),
                tags = listOf("local", "tech"),
                createdAt = now - TimeUnit.DAYS.toMillis(2)
            ),
            Gathering(
                id = "sample_gathering_hackathon",
                title = "Smart Cities Hackathon",
                description = "A 48-hour sprint for civic technology teams. Bring a team or join one in the room.",
                hostId = "sample_sara",
                hostName = "سارة أحمد",
                location = "Nexa Virtual Room",
                isVirtual = true,
                startsAt = inDays(12, 16),
                durationMinutes = 180,
                capacity = 0,
                attendeeIds = emptyList(),
                tags = listOf("hackathon", "civic"),
                createdAt = now - TimeUnit.DAYS.toMillis(1)
            )
        )
    }
}
