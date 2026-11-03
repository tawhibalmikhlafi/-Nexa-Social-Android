package com.nexa.social.data.repository

import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.domain.model.ThemeConfig
import com.nexa.social.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow

class ThemeRepositoryImpl(
    private val prefs: PreferenceStorage
) : ThemeRepository {

    override val themeConfig: Flow<ThemeConfig> = prefs.themeConfig

    override suspend fun save(config: ThemeConfig) = prefs.saveTheme(config)
}
