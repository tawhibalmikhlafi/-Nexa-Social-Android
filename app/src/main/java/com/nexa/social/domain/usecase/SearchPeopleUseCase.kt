package com.nexa.social.domain.usecase

import com.nexa.social.core.common.Validators
import com.nexa.social.domain.model.User
import com.nexa.social.domain.repository.SearchRepository

/** Normalizes the query (removes a leading @, ignores case) then searches people by prefix. */
class SearchPeopleUseCase(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(query: String): List<User> {
        val canonical = Validators.canonicalUsername(query).trim()
        if (canonical.isBlank()) return emptyList()
        return searchRepository.searchPeople(canonical)
    }
}
