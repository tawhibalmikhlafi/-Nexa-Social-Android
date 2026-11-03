package com.nexa.social.domain.repository

import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.Gathering
import kotlinx.coroutines.flow.Flow

/** Device-local gatherings with RSVP. No remote events service is required for the local build. */
interface GatheringRepository {
    fun observeGatherings(): Flow<List<Gathering>>
    fun observeGathering(id: String): Flow<Gathering?>
    fun observeUpcoming(): Flow<List<Gathering>>

    suspend fun createGathering(
        title: String,
        description: String,
        location: String,
        isVirtual: Boolean,
        startsAt: Long,
        durationMinutes: Int,
        capacity: Int,
        tags: List<String>
    ): Resource<Gathering>

    suspend fun toggleAttendance(id: String): Resource<Gathering>
    suspend fun deleteGathering(id: String): Boolean
    suspend fun refresh() {}
}
