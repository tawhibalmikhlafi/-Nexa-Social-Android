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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    var showBanner by rememberSaveable { mutableStateOf(true) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                        onNotifications = onNotifications
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
                    QuickActionsSection(
                        onNewMessage = onNewMessage,
                        onOpenFriends = onOpenFriends,
                        onOpenSaved = onOpenSaved,
                        onOpenGroups = onOpenGroups
                    )
                }
                if (showBanner) {
                    item {
                        NotificationBanner(
                            onDismiss = { showBanner = false },
                            onEnable = onNotifications
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }

                if (conversations.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد رسائل حالياً",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(conversations, key = { it.id }) { conversation ->
                        ChatListItem(
                            conversation = conversation,
                            presenceViewModel = presenceViewModel,
                            onClick = { onOpenConversation(conversation.id) }
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
    onNotifications: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenProfile)
            ) {
                AsyncImage(
                    model = "https://i.pravatar.cc/150?img=11",
                    contentDescription = "Profile",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(OnlineGreen, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
            
            IconButton(onClick = onNotifications) {
                Box {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(BadgeRed, CircleShape)
                                .align(Alignment.TopEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(unreadCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            IconButton(onClick = onOpenProfile) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "الرسائل",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "تواصل مع أصدقائك بكل سهولة",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onSearchClick),
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
fun QuickActionsSection(
    onNewMessage: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenGroups: () -> Unit
) {
    val actions = listOf(
        QuickAction("رسالة جديدة", "ابدأ محادثة", Icons.Outlined.Edit, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant, onClick = onNewMessage),
        QuickAction("الطلبات", "الأصدقاء والطلبات", Icons.Outlined.Security, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.surfaceVariant, onClick = onOpenFriends),
        QuickAction("الرسائل المميزة", "المحفوظات", Icons.Outlined.Star, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant, onClick = onOpenSaved),
        QuickAction("المجموعات", "قنواتك ومجموعاتك", Icons.Outlined.People, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant, true, onClick = onOpenGroups)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        actions.forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = action.onClick)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(action.bgColor, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(action.icon, contentDescription = action.title, tint = action.iconColor)
                    if (action.hasBadge) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(BadgeRed, CircleShape)
                                .align(Alignment.TopEnd)
                                .offset(x = (-4).dp, y = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(action.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(action.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
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
        
        IconButton(onClick = onEnable, modifier = Modifier.size(24.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ChatListItem(
    conversation: Conversation,
    presenceViewModel: PresenceViewModel,
    onClick: () -> Unit
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
