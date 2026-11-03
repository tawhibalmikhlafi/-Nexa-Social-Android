package com.nexa.social.data.repository

import com.nexa.social.core.common.Resource
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.domain.model.PrivacySettings
import com.nexa.social.domain.repository.PrivacyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Offline / guest implementation. Keeps every privacy value in DataStore so the UI keeps
 * working without Supabase, and never pretends a server write happened.
 */
class LocalPrivacyRepository(
    private val prefs: PreferenceStorage,
) : PrivacyRepository {

    override fun observe(): Flow<PrivacySettings> = prefs.privacySettings

    override suspend fun load(): Resource<PrivacySettings> =
        Resource.Success(prefs.privacySettings.first())

    override suspend fun save(settings: PrivacySettings): Resource<PrivacySettings> {
        prefs.setPrivacySettings(settings)
        return Resource.Success(settings)
    }
}
