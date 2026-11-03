package com.nexa.social.data.local

import com.nexa.social.core.storage.JsonFileStore
import com.nexa.social.domain.model.BlockedUser
import com.nexa.social.domain.model.HiddenContent
import com.nexa.social.domain.model.MutedUser
import com.nexa.social.domain.model.ReportRecord

/** Persists local safety state: blocks, mutes, hidden content and reports. */
class SafetyLocalDataSource(private val store: JsonFileStore) {

    private companion object {
        const val BLOCKED_FILE = "blocked_users.json"
        const val MUTED_FILE = "muted_users.json"
        const val HIDDEN_FILE = "hidden_content.json"
        const val REPORTS_FILE = "reports.json"
    }

    fun readBlocked(): List<BlockedUser> = store.readList(BLOCKED_FILE)
    fun writeBlocked(list: List<BlockedUser>) = store.writeList(BLOCKED_FILE, list)

    fun readMuted(): List<MutedUser> = store.readList(MUTED_FILE)
    fun writeMuted(list: List<MutedUser>) = store.writeList(MUTED_FILE, list)

    fun readHidden(): List<HiddenContent> = store.readList(HIDDEN_FILE)
    fun writeHidden(list: List<HiddenContent>) = store.writeList(HIDDEN_FILE, list)

    fun readReports(): List<ReportRecord> = store.readList(REPORTS_FILE)
    fun writeReports(list: List<ReportRecord>) = store.writeList(REPORTS_FILE, list)
}
