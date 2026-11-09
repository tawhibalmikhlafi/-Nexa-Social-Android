package com.nexa.social.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexa.social.core.ui.nexaApplication
import com.nexa.social.core.storage.PreferenceStorage
import com.nexa.social.core.common.Resource
import kotlinx.coroutines.flow.first
import com.nexa.social.data.remote.UserNote
import com.nexa.social.data.remote.SupabaseNotesDataSource
import com.nexa.social.domain.model.Conversation
import com.nexa.social.domain.repository.MessageRepository
import com.nexa.social.domain.repository.NotificationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessagesViewModel(
    messageRepository: MessageRepository,
    notificationRepository: NotificationRepository,
    private val notesDataSource: SupabaseNotesDataSource?,
    private val preferenceStorage: PreferenceStorage
) : ViewModel() {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    val conversations: StateFlow<List<Conversation>> =
        combine(messageRepository.observeConversations(), _search, preferenceStorage.hiddenConversationIds) { list, query, hidden ->
            val visible = list.filterNot { it.id in hidden }
            if (query.isBlank()) visible
            else visible.filter {
                it.participant.displayName.contains(query, ignoreCase = true) ||
                    it.participant.username.contains(query, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> =
        notificationRepository.observeNotifications()
            .map { list -> list.count { !it.isRead } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _notes = MutableStateFlow<List<UserNote>>(emptyList())
    val notes: StateFlow<List<UserNote>> = _notes.asStateFlow()
    private val _myNote = MutableStateFlow("")
    val myNote: StateFlow<String> = _myNote.asStateFlow()
    private val _noteSaving = MutableStateFlow(false)
    val noteSaving: StateFlow<Boolean> = _noteSaving.asStateFlow()
    private val _noteError = MutableStateFlow<String?>(null)
    val noteError: StateFlow<String?> = _noteError.asStateFlow()
    val currentUserId: String? get() = notesDataSource?.currentUserId()

    init {
        refreshNotes()
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                refreshNotes()
            }
        }
    }

    fun setSearch(value: String) { _search.value = value }

    fun archiveConversations(ids: Set<String>, archived: Boolean = true, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val results = ids.map { id -> runCatching { messageRepository.setArchived(id, archived) }.isSuccess }
            onComplete(results.isNotEmpty() && results.all { it })
        }
    }

    fun deleteConversationsForMe(ids: Set<String>, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val results = ids.map { id ->
                runCatching { messageRepository.deleteConversationForMe(id) }.getOrNull() is Resource.Success
            }
            val success = results.isNotEmpty() && results.all { it }
            if (success) {
                val hidden = preferenceStorage.hiddenConversationIds.first()
                preferenceStorage.setHiddenConversationIds(hidden + ids)
            }
            onComplete(success)
        }
    }

    fun refreshNotes() {
        val source = notesDataSource ?: return
        viewModelScope.launch {
            val loaded = source.loadNotes()
            _notes.value = loaded
            _myNote.value = loaded.firstOrNull { it.userId == source.currentUserId() }?.note.orEmpty()
        }
    }

    fun saveNote(value: String, visibility: String = "FOLLOWING", durationHours: Int = 24, onComplete: (Boolean) -> Unit = {}) {
        val source = notesDataSource
        if (source == null) {
            _noteError.value = "ميزة الملاحظات تحتاج اتصالاً بحسابك"
            onComplete(false)
            return
        }
        val trimmed = value.trim()
        if (trimmed.length > 60) {
            _noteError.value = "الملاحظة يجب ألا تتجاوز 60 حرفاً"
            onComplete(false)
            return
        }
        viewModelScope.launch {
            _noteSaving.value = true
            _noteError.value = null
            val ok = if (trimmed.isBlank()) source.clearNote() else source.saveNote(trimmed, visibility, durationHours)
            if (ok) {
                refreshNotes()
                _myNote.value = trimmed
            } else {
                _noteError.value = "تعذر حفظ الملاحظة. تحقق من الاتصال ثم حاول مجدداً."
            }
            _noteSaving.value = false
            onComplete(ok)
        }
    }

    fun interactWithNote(noteUserId: String, kind: String, content: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = notesDataSource?.addInteraction(noteUserId, kind, content) == true
            onComplete(ok)
        }
    }

    suspend fun getNoteInteractions(noteUserId: String) =
        notesDataSource?.loadInteractions(noteUserId).orEmpty()

    fun clearNoteError() { _noteError.value = null }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = nexaApplication()
                MessagesViewModel(
                    app.container.messageRepository,
                    app.container.notificationRepository,
                    app.container.notesDataSource,
                    app.container.preferenceStorage
                )
            }
        }
    }
}
