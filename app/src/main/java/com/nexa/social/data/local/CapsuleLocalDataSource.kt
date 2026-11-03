package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore

/** On-disk representation. Only ciphertext is persisted; the plaintext lives in memory only. */
data class StoredCapsule(
    val id: String,
    val title: String,
    val cipherText: String,
    val iv: String,
    val createdAt: Long,
    val unlockAt: Long,
    val authorId: String,
    val authorName: String,
    val openedAt: Long? = null
)

class CapsuleLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val FILE = "capsules.json"
    }

    fun readCapsules(): List<StoredCapsule> = store.readList(FILE)

    fun writeCapsules(capsules: List<StoredCapsule>) = store.writeList(FILE, capsules)
}
