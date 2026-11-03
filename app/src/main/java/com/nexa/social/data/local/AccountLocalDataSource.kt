package com.nexa.social.data.local

import com.nexa.social.core.common.Validators
import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.domain.model.LocalCredential
import com.nexa.social.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A local development account binding its public profile to its local credentials. */
data class LocalAccountRecord(
    val user: User,
    val credential: LocalCredential
)

/**
 * In-memory cached, file backed store for accounts created on this device.
 * Only salted password hashes are persisted, never plain text.
 */
class AccountLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "accounts.json"
    }

    private val _records = MutableStateFlow(store.readList<LocalAccountRecord>(FILE))
    val records: StateFlow<List<LocalAccountRecord>> = _records.asStateFlow()

    fun getById(userId: String): LocalAccountRecord? = _records.value.firstOrNull { it.user.id == userId }

    /** Case-insensitive lookup using the canonical username form. */
    fun findByUsername(username: String): LocalAccountRecord? {
        val canonical = Validators.canonicalUsername(username)
        return _records.value.firstOrNull { Validators.canonicalUsername(it.credential.username) == canonical }
    }

    fun isUsernameTaken(username: String): Boolean = findByUsername(username) != null

    suspend fun add(record: LocalAccountRecord) {
        _records.value = _records.value + record
        persist()
    }

    suspend fun updateUser(user: User) {
        _records.value = _records.value.map { record ->
            if (record.user.id == user.id) record.copy(user = user) else record
        }
        persist()
    }

    private suspend fun persist() {
        val snapshot = _records.value
        withContext(Dispatchers.IO) { store.writeList(FILE, snapshot) }
    }
}
