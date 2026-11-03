package com.nexa.social.domain.repository

import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.PrivacySettings
import kotlinx.coroutines.flow.Flow

interface PrivacyRepository {
    fun observe(): Flow<PrivacySettings>
    suspend fun load(): Resource<PrivacySettings>
    suspend fun save(settings: PrivacySettings): Resource<PrivacySettings>
}
