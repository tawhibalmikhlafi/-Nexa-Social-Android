package com.nexa.social.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.domain.model.Conversation
import com.nexa.social.domain.model.NotificationType
import com.nexa.social.feature.settings.SettingsViewModel
import com.nexa.social.data.remote.UserNote
import com.nexa.social.feature.presence.PresenceViewModel
import com.nexa.social.ui.components.OnlineAvatar
import com.nexa.social.ui.components.initialsOf

val DarkBackground = Color(0xFF0B0E14)
val SurfaceDark = Color(0xFF161A23)
val PrimaryPurple = Color(0xFF7B61FF)
val DarkPurpleBanner = Color(0xFF1E1B3A)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8B8E98)
val OnlineGreen = Color(0xFF00C853)
val BadgeRed = Color(0xFFFF5252)

data class QuickAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val bgColor: Color,
    val hasBadge: Boolean = false,
    val onClick: () -> Unit = {}
)

@Composable
fun MessagesScreen(
    onOpenConversation: (String) -> Unit,
    onOpenProfile: () -> Unit = {},
    onNotifications: () -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenGroups: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onNewMessage: () -> Unit = {},
    viewModel: MessagesViewModel = viewModel(factory = MessagesViewModel.Factory),
    presenceViewModel: PresenceViewModel = viewModel(factory = PresenceViewModel.Factory)
) {
    val search by viewModel.search.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val messageSettingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val notificationsEnabled by messageSettingsViewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val notificationCategories by messageSettingsViewModel.notificationCategories.collectAsStateWithLifecycle()
    var showMessageSettings by rememberSaveable { mutableStateOf(false) }
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val myNote by viewModel.myNote.collectAsStateWithLifecycle()
    val noteSaving by viewModel.noteSaving.collectAsStateWithLifecycle()
    val noteError by viewModel.noteError.collectAsStateWithLifecycle()
    var showNotesEditor by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var selectedConversationIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var bulkActionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var noteDraft by rememberSaveable { mutableStateOf("") }
    var noteVisibility by rememberSaveable { mutableStateOf("FOLLOWING") }
    var noteDuration by rememberSaveable { mutableStateOf(24) }
    var selectedNote by remember { mutableStateOf<UserNote?>(null) }
    var noteReply by rememberSaveable { mutableStateOf("") }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (showMessageSettings) {
            AlertDialog(
                onDismissRequest = { showMessageSettings = false },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("إعدادات الرسائل والنشاط", fontWeight = FontWeight.Bold)
                        Text(
                            "تحكم في تنبيهات المحادثات وتفاعلات حسابك",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("إعدادات الرسائل", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(
                            if (notificationsEnabled) "يمكنك تخصيص التنبيهات لكل نوع أدناه" else "إشعارات التطبيق متوقفة من الإعدادات العامة",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        MessagePreferenceRow(
                            title = "الرسائل الجديدة",
                            subtitle = "تنبيهات عند وصول رسالة",
                            checked = notificationCategories[NotificationType.MESSAGE] != false,
                            onCheckedChange = { messageSettingsViewModel.setNotificationCategory(NotificationType.MESSAGE, it) }
                        )
                        androidx.compose.material3.HorizontalDivider()
                        Text("إشعارات النشاط", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        listOf(
                            NotificationType.LIKE to "الإعجابات والتفاعلات",
                            NotificationType.COMMENT to "التعليقات",
                            NotificationType.REPLY to "الردود",
                            NotificationType.FOLLOW to "المتابعات",
                            NotificationType.STORY_REPLY to "الردود على القصص",
                            NotificationType.MENTION to "الإشارات إليك"
                        ).forEach { (type, label) ->
                            MessagePreferenceRow(
                                title = label,
                                checked = notificationCategories[type] != false,
                                onCheckedChange = { messageSettingsViewModel.setNotificationCategory(type, it) }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMessageSettings = false }) { Text("تم") }
                }
            )
        }
        if (selectedNote != null) {
            val note = selectedNote!!
            AlertDialog(
                onDismissRequest = { selectedNote = null; noteReply = "" },
                title = { Text(note.displayName + " · @" + note.username) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(note.note, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("❤️", "😂", "🔥", "👏", "😍").forEach { emoji ->
                                TextButton(onClick = { viewModel.interactWithNote(note.userId, "REACTION", emoji) }) { Text(emoji, fontSize = 20.sp) }
                            }
                        }
                        OutlinedTextField(value = noteReply, onValueChange = { noteReply = it.take(200) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("اكتب رداً على الملاحظة") }, trailingIcon = {
                            TextButton(enabled = noteReply.isNotBlank(), onClick = { viewModel.interactWithNote(note.userId, "REPLY", noteReply) { if (it) { noteReply = ""; selectedNote = null } } }) { Text("إرسال") }
                        })
                    }
                },
                confirmButton = { TextButton(onClick = { selectedNote = null }) { Text("إغلاق") } }
            )
        }
        if (showNotesEditor) {
            AlertDialog(
                onDismissRequest = { if (!noteSaving) { showNotesEditor = false; viewModel.clearNoteError() } },
                title = { Text("ملاحظتك") },
                text = {
                    Column {
                        Text("شارك فكرة قصيرة تظهر لأصدقائك في شاشة الرسائل لمدة 24 ساعة.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("من يمكنه مشاهدة الملاحظة؟", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf("FOLLOWING" to "الذين تتابعهم", "FOLLOWERS" to "المتابعين", "EVERYONE" to "الجميع").forEach { (value, label) ->
                                TextButton(onClick = { noteVisibility = value }) { Text(if (noteVisibility == value) "✓ " + label else label, fontSize = 10.sp) }
                            }
                        }
                        Text("مدة ظهور الملاحظة", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(1, 6, 12, 24, 48).forEach { hours ->
                                TextButton(onClick = { noteDuration = hours }) { Text((if (noteDuration == hours) "✓ " else "") + hours + "س", fontSize = 10.sp) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = noteDraft,
                            onValueChange = { noteDraft = it.take(60) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("بماذا تفكر؟") },
                            supportingText = { Text("${noteDraft.length}/60") },
                            maxLines = 3,
                            enabled = !noteSaving
                        )
                        if (noteError != null) {
                            Text(noteError.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !noteSaving,
                        onClick = {
                            viewModel.saveNote(noteDraft, noteVisibility, noteDuration) { success ->
                                if (success) showNotesEditor = false
                            }
                        }
                    ) { Text(if (noteSaving) "جارٍ الحفظ..." else "مشاركة") }
                },
                dismissButton = {
                    Row {
                        if (myNote.isNotBlank()) {
                            TextButton(enabled = !noteSaving, onClick = {
                                viewModel.saveNote("") { success -> if (success) { noteDraft = ""; showNotesEditor = false } }
                            }) { Text("حذف") }
                        }
                        TextButton(onClick = { showNotesEditor = false; viewModel.clearNoteError() }, enabled = !noteSaving) { Text("إلغاء") }
                    }
                }
            )
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNewMessage,
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "رسالة جديدة", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    TopBarSection(
                        unreadCount = unreadNotifications,
                        onOpenProfile = onOpenProfile,
                        onNotifications = onNotifications,
                        onSettings = { showMessageSettings = true }
                    )
                }
                item {
                    SearchBarSection(
                        search = search,
                        onSearchChange = viewModel::setSearch,
                        onSearchClick = onSearch
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = {
                            selectionMode = !selectionMode
                            selectedConversationIds = emptySet()
                            bulkActionMessage = null
                        }) { Text(if (selectionMode) "إلغاء التحديد" else "تحديد الدردشات") }
                        TextButton(onClick = {
                            showArchived = !showArchived
                            selectionMode = false
                            selectedConversationIds = emptySet()
                            bulkActionMessage = null
                        }) { Text(if (showArchived) "العودة للمحادثات" else "المؤرشفة") }
                        if (selectionMode && selectedConversationIds.isNotEmpty()) {
                            Text("${selectedConversationIds.size} محددة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                viewModel.archiveConversations(selectedConversationIds, archived = !showArchived) { ok ->
                                    bulkActionMessage = if (ok) "تمت أرشفة الدردشات المحددة" else "تعذرت أرشفة بعض الدردشات"
                                    if (ok) { selectionMode = false; selectedConversationIds = emptySet() }
                                }
                            }) { Text(if (showArchived) "إلغاء الأرشفة" else "أرشفة") }
                            TextButton(onClick = {
                                viewModel.deleteConversationsForMe(selectedConversationIds) { ok ->
                                    bulkActionMessage = if (ok) "حُذفت الدردشات من قائمتك" else "تعذر حذف بعض الدردشات"
                                    if (ok) { selectionMode = false; selectedConversationIds = emptySet() }
                                }
                            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    if (bulkActionMessage != null) {
                        Text(bulkActionMessage.orEmpty(), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                item {
                    NotesSection(
                        notes = notes,
                        currentUserId = viewModel.currentUserId,
                        onMyNoteClick = {
                            noteDraft = myNote
                            notes.firstOrNull { it.userId == viewModel.currentUserId }?.let { noteVisibility = it.visibility; noteDuration = it.durationHours }
                            viewModel.clearNoteError()
                            showNotesEditor = true
                        },
                        onNoteClick = { selectedNote = it }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }

                if (displayedConversations.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showArchived) "لا توجد دردشات مؤرشفة" else "لا توجد رسائل حالياً",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(displayedConversations, key = { it.id }) { conversation ->
                        ChatListItem(
                            conversation = conversation,
                            presenceViewModel = presenceViewModel,
                            selectionMode = selectionMode,
                            selected = conversation.id in selectedConversationIds,
                            onClick = {
                                if (selectionMode) {
                                    selectedConversationIds = if (conversation.id in selectedConversationIds) selectedConversationIds - conversation.id else selectedConversationIds + conversation.id
                                } else onOpenConversation(conversation.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopBarSection(
    unreadCount: Int,
    onOpenProfile: () -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "الرسائل",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "محادثاتك وأصدقاؤك في مكان واحد",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "إعدادات الرسائل والنشاط",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onNotifications) {
            Box {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = "الإشعارات",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .background(BadgeRed, CircleShape)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (unreadCount > 99) "99+" else unreadCount.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
fun SearchBarSection(
    search: String,
    onSearchChange: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    OutlinedTextField(
        value = search,
        onValueChange = onSearchChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("البحث في الرسائل أو الأصدقاء...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun MessagePreferenceRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun NotificationBanner(
    onDismiss: () -> Unit,
    onEnable: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("ابق على اتصال دائم", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("فعل إشعارات الرسائل لتصللك فوراً", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("تفعيل الإشعارات", color = Color.White, fontSize = 12.sp)
            }
        }
        
    }
}

@Composable
fun ChatListItem(
    conversation: Conversation,
    presenceViewModel: PresenceViewModel,
    onClick: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false
) {
    val participant = conversation.participant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            OnlineAvatar(
                userId = participant.id,
                imageUrl = participant.avatarUri,
                size = 50.dp,
                fallbackInitials = initialsOf(participant.displayName),
                presenceViewModel = presenceViewModel
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.unreadCount == 0) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Read",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = conversation.lastMessagePreview.ifBlank { "..." },
                    color = if (conversation.unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = TimeUtils.relativeFromNow(conversation.lastTimestamp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (conversation.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversation.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


@Composable
private fun NotesSection(
    notes: List<UserNote>,
    currentUserId: String?,
    onMyNoteClick: () -> Unit,
    onNoteClick: (UserNote) -> Unit
) {
    val ordered = notes.filterNot { it.userId == currentUserId } 
        .let { others -> listOfNotNull(notes.firstOrNull { it.userId == currentUserId }) + others }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("الملاحظات", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("تختفي بعد 24 ساعة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "my-note") {
                NoteAvatar(
                    title = ordered.firstOrNull { it.userId == currentUserId }?.displayName ?: "ملاحظتك",
                    note = ordered.firstOrNull { it.userId == currentUserId }?.note.orEmpty(),
                    avatarUrl = ordered.firstOrNull { it.userId == currentUserId }?.avatarUrl,
                    isMine = true,
                    onClick = onMyNoteClick
                )
            }
            items(ordered.filterNot { it.userId == currentUserId }, key = { it.userId }) { entry ->
                NoteAvatar(
                    title = entry.displayName,
                    note = entry.note,
                    avatarUrl = entry.avatarUrl,
                    isMine = false,
                    onClick = { onNoteClick(entry) }
                )
            }
        }
    }
}

@Composable
private fun NoteAvatar(
    title: String,
    note: String,
    avatarUrl: String?,
    isMine: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.height(66.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            if (note.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(43.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = note,
                        color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (isMine) {
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(40.dp)
                        .clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ اكتب ملاحظة", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(if (isMine) 2.dp else 1.dp, if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(model = avatarUrl, contentDescription = title, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Text(title.take(1).ifBlank { "N" }, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = if (isMine) "ملاحظتك" else title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
