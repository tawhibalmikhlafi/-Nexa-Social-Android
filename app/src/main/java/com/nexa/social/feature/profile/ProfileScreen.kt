package com.nexa.social.feature.profile

import androidx.compose.foundation.background
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.navigation.DeepLinkRouter
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.feature.presence.PresenceViewModel
import com.nexa.social.core.ui.rememberRemoteMode
import com.nexa.social.domain.model.Post
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaCover
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.OnlineAvatar
import com.nexa.social.ui.components.initialsOf
import com.nexa.social.ui.share.sharePost
import com.nexa.social.ui.theme.NexaSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    targetUserId: String?,
    onBack: (() -> Unit)?,
    onEditProfile: () -> Unit,
    onCreateAccount: () -> Unit,
    onSettings: () -> Unit = {},
    onOpenLabs: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenPost: (String) -> Unit = {},
    onOpenFollowers: (String) -> Unit = {},
    onOpenFollowing: (String) -> Unit = {},
    onOpenConversation: (String) -> Unit = {},
    onCreateStory: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(targetUserId),
        key = targetUserId ?: "self"
    ),
    presenceViewModel: PresenceViewModel = viewModel(factory = PresenceViewModel.Factory)
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val posts by viewModel.posts.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val savedPosts by viewModel.savedPosts.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isGuest by viewModel.isGuest.collectAsStateWithLifecycle()
    val isOwnProfile by viewModel.isOwnProfile.collectAsStateWithLifecycle()
    val isFollowing by viewModel.isFollowing.collectAsStateWithLifecycle()
    val followRequestPending by viewModel.isFollowRequestPending.collectAsStateWithLifecycle()
    val followersCount by viewModel.followersCount.collectAsStateWithLifecycle()
    val followingCount by viewModel.followingCount.collectAsStateWithLifecycle()
    val followBusy by viewModel.followBusy.collectAsStateWithLifecycle()
    val followError by viewModel.followError.collectAsStateWithLifecycle()
    val incomingFollowRequests by viewModel.incomingFollowRequests.collectAsStateWithLifecycle()
    val followRequestsLoading by viewModel.followRequestsLoading.collectAsStateWithLifecycle()
    val followRequestError by viewModel.followRequestError.collectAsStateWithLifecycle()
    val followRequestActionId by viewModel.followRequestActionId.collectAsStateWithLifecycle()
    val canMessage by viewModel.canMessage.collectAsStateWithLifecycle()
    val messageBusy by viewModel.messageBusy.collectAsStateWithLifecycle()
    val messageError by viewModel.messageError.collectAsStateWithLifecycle()
    val remoteMode = rememberRemoteMode()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var fullImageUri by remember { mutableStateOf<String?>(null) }
    var fullImageTitle by remember { mutableStateOf("") }

    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val shareFailedMessage = stringResource(R.string.post_share_failed)
    val followErrorMessage = stringResource(R.string.follow_action_failed)
    val messageErrorMessage = stringResource(R.string.profile_message_failed)
    val profileLinkCopied = stringResource(R.string.profile_link_copied)
    val usernameCopiedMessage = stringResource(R.string.profile_username_copied)
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(followError) {
        if (followError) {
            snackbarHost?.showSnackbar(followErrorMessage)
            viewModel.consumeFollowError()
        }
    }

    LaunchedEffect(messageError) {
        if (messageError) {
            snackbarHost?.showSnackbar(messageErrorMessage)
            viewModel.consumeMessageError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.conversationOpened.collect { conversationId ->
            onOpenConversation(conversationId)
        }
    }

    val current = user
    val showOwnerTools = viewModel.isSelf || isOwnProfile

    if (!showOwnerTools && selectedTab > 1) {
        selectedTab = 0
    }

    if (current == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (onBack != null) {
                com.nexa.social.ui.components.NexaTopBar(title = stringResource(R.string.nav_profile), onBack = onBack)
            }
            if (viewModel.isSelf && isGuest) {
                NexaEmptyState(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.profile_guest_title),
                    body = stringResource(R.string.profile_guest_body),
                    action = {
                        NexaPrimaryButton(
                            text = stringResource(R.string.profile_guest_action),
                            onClick = onCreateAccount
                        )
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    com.nexa.social.ui.components.NexaLoadingState(label = stringResource(R.string.nav_profile))
                }
            }
        }
        return
    }

    val copyProfileLink: () -> Unit = {
        clipboard.setText(AnnotatedString(DeepLinkRouter.userLink(current.id)))
        scope.launch { snackbarHost?.showSnackbar(profileLinkCopied) }
    }

    val copyUsername: () -> Unit = {
        clipboard.setText(AnnotatedString(current.username))
        scope.launch { snackbarHost?.showSnackbar(usernameCopiedMessage) }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val avatarGradient = remember(current.id) {
        Brush.linearGradient(listOf(primaryColor, secondaryColor))
    }
    
    val darkCardBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    val neonPurple = Color(0xFF9D4EDD)
    val neonBlue = Color(0xFF3A86FF)

    val totalLikes = remember(posts) { posts.sumOf { it.likeCount } }

    // Full-screen viewer shared by profile avatar and cover image.
    fullImageUri?.let { imageUri ->
        Dialog(onDismissRequest = { fullImageUri = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = fullImageTitle,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp, bottom = 86.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { fullImageUri = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "إغلاق", tint = Color.White)
                    }
                    Text(
                        text = fullImageTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                androidx.compose.material3.Button(
                    onClick = {
                        runCatching {
                            val extension = if (imageUri.substringBefore('?').substringAfterLast('.', "").lowercase() in setOf("png", "webp", "gif")) {
                                imageUri.substringBefore('?').substringAfterLast('.').lowercase()
                            } else "jpg"
                            val fileName = "Nexa-${if (fullImageTitle.contains("غلاف")) "cover" else "profile"}-${System.currentTimeMillis()}.$extension"
                            val request = DownloadManager.Request(Uri.parse(imageUri))
                                .setTitle(fullImageTitle)
                                .setDescription("جارٍ حفظ الصورة في مجلد الصور")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setAllowedOverMetered(true)
                                .setAllowedOverRoaming(false)
                                .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Nexa/$fileName")
                            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                        }.onSuccess {
                            Toast.makeText(context, "بدأ حفظ الصورة في Pictures/Nexa", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "تعذر حفظ الصورة؛ قد تكون الصورة غير متاحة للتنزيل", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("حفظ الصورة")
                }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = NexaSpacing.xxxl),
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
    ) {
        // 1. Top Bar Header
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.title_settings))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.nav_profile), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = copyProfileLink) {
                    Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.profile_copy_link))
                }
            }
        }

        if (showOwnerTools && remoteMode &&
            (incomingFollowRequests.isNotEmpty() || followRequestsLoading || followRequestError)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexaSpacing.md),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NexaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("طلبات المتابعة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    if (incomingFollowRequests.isEmpty()) "لا توجد طلبات معلّقة"
                                    else "${incomingFollowRequests.size} طلب بانتظار موافقتك",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = viewModel::refreshIncomingFollowRequests) {
                                Text("تحديث")
                            }
                        }
                        if (followRequestsLoading) {
                            Text("جارٍ تحميل الطلبات…", style = MaterialTheme.typography.bodySmall)
                        }
                        if (followRequestError) {
                            Text(
                                "تعذر تحميل الطلبات أو تنفيذ الإجراء. تحقق من الاتصال وحاول مجددًا.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        incomingFollowRequests.take(8).forEach { request ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(request.requester.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("@${request.requester.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.respondToFollowRequest(request.id, approve = false) },
                                    enabled = followRequestActionId == null
                                ) { Text("رفض") }
                                Button(
                                    onClick = { viewModel.respondToFollowRequest(request.id, approve = true) },
                                    enabled = followRequestActionId == null
                                ) { Text(if (followRequestActionId == request.id) "..." else "قبول") }
                            }
                        }
                        if (incomingFollowRequests.size > 8) {
                            Text("يُعرض أحدث 8 طلبات. حدّث القائمة بعد معالجة الطلبات.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // 2. Profile Header (Cover, Avatar, Name, Bio, Details)
        item(span = { GridItemSpan(maxLineSpan) }) {
            val coverHeight = 176.dp
            val avatarSize = 112.dp
            val avatarOverlap = 56.dp
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(coverHeight + avatarOverlap)
                ) {
                    NexaCover(
                        seed = current.id.hashCode().toLong(),
                        coverUri = current.coverUri,
                        height = coverHeight,
                        corner = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    )
                    if (!current.coverUri.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                                .clickable {
                                    fullImageUri = current.coverUri
                                    fullImageTitle = "صورة الغلاف"
                                }
                        )
                    }

                    if (showOwnerTools) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(NexaSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                        ) {
                            CoverActionChip(
                                icon = Icons.Filled.Edit,
                                label = stringResource(R.string.profile_edit_cover),
                                onClick = onEditProfile
                            )
                            CoverActionChip(
                                icon = Icons.Filled.Add,
                                label = stringResource(R.string.profile_add_story),
                                onClick = onCreateStory,
                                highlighted = true
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = NexaSpacing.lg)
                            .size(avatarSize)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(width = 3.dp, brush = avatarGradient, shape = CircleShape)
                                .clickable { current.avatarUri?.takeIf { it.isNotBlank() }?.let { fullImageUri = it; fullImageTitle = "الصورة الشخصية" } },
                            contentAlignment = Alignment.Center
                        ) {
                            OnlineAvatar(
                                userId = current.id,
                                imageUrl = current.avatarUri,
                                size = avatarSize - 10.dp,
                                showPresence = !showOwnerTools,
                                fallbackInitials = initialsOf(current.displayName),
                                presenceViewModel = presenceViewModel
                            )
                        }
                        if (showOwnerTools) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, avatarGradient, CircleShape)
                                    .clickable { onEditProfile() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = stringResource(R.string.profile_edit_photo),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexaSpacing.lg)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = NexaSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = current.displayName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = copyUsername)
                                    .padding(vertical = NexaSpacing.xxs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                            ) {
                                Text(
                                    text = "@${current.username}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.profile_copy_username),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (showOwnerTools) {
                            Spacer(Modifier.width(NexaSpacing.sm))
                            OutlinedButton(
                                onClick = onEditProfile,
                                contentPadding = PaddingValues(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(NexaSpacing.xs))
                                Text(
                                    text = stringResource(R.string.profile_edit),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (current.bio.isNotBlank()) {
                        Spacer(Modifier.height(NexaSpacing.sm))
                        Text(
                            text = current.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(NexaSpacing.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(NexaSpacing.xxs)) {
                        if (!current.city.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)) {
                                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = current.city, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (current.createdAt > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                val dateStr = remember(current.createdAt) {
                                    SimpleDateFormat("dd MMMM yyyy", Locale("ar")).format(Date(current.createdAt))
                                }
                                Text(text = stringResource(R.string.profile_joined, dateStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    val hasPersonalDetails = !current.placeOfOrigin.isNullOrBlank() ||
                        !current.currentResidence.isNullOrBlank() ||
                        !current.birthDate.isNullOrBlank() ||
                        current.skills.orEmpty().isNotEmpty() || current.hobbies.orEmpty().isNotEmpty()
                    if (hasPersonalDetails) {
                        Spacer(Modifier.height(NexaSpacing.sm))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(NexaSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                            ) {
                                Text("معلومات شخصية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (!current.placeOfOrigin.isNullOrBlank()) {
                                    ProfileDetailRow("مكان المنشأ", current.placeOfOrigin, Icons.Filled.Place)
                                }
                                if (!current.currentResidence.isNullOrBlank()) {
                                    ProfileDetailRow("مكان الإقامة", current.currentResidence, Icons.Filled.Home)
                                }
                                if (!current.birthDate.isNullOrBlank()) {
                                    val birthLabel = remember(current.birthDate) {
                                        runCatching {
                                            LocalDate.parse(current.birthDate.orEmpty())
                                                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("ar")))
                                        }.getOrDefault(current.birthDate.orEmpty())
                                    }
                                    ProfileDetailRow("تاريخ الميلاد", birthLabel, Icons.Filled.Cake)
                                }
                                if (current.skills.orEmpty().isNotEmpty()) {
                                    Text("المهارات", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs), verticalArrangement = Arrangement.spacedBy(NexaSpacing.xs)) {
                                        current.skills.orEmpty().forEach { skill ->
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(skill, modifier = Modifier.padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                                if (current.hobbies.orEmpty().isNotEmpty()) {
                                    Text("الهوايات", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs), verticalArrangement = Arrangement.spacedBy(NexaSpacing.xs)) {
                                        current.hobbies.orEmpty().forEach { hobby ->
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                                            ) {
                                                Text(hobby, modifier = Modifier.padding(horizontal = NexaSpacing.sm, vertical = NexaSpacing.xs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(NexaSpacing.md))
                }
            }
        }

        // 3. Stats
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexaSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
            ) {
                StatBox(icon = Icons.Filled.Person, value = if (!isOwnProfile && followersCount == 0 && current.followersCount > 0) "—" else followersCount.toString(), label = stringResource(R.string.profile_followers), iconColor = neonBlue, modifier = Modifier.weight(1f), onClick = { onOpenFollowers(current.id) })
                StatBox(icon = Icons.Filled.Person, value = if (!isOwnProfile && followingCount == 0 && current.followingCount > 0) "—" else followingCount.toString(), label = stringResource(R.string.profile_following), iconColor = neonPurple, modifier = Modifier.weight(1f), onClick = { onOpenFollowing(current.id) })
                StatBox(icon = Icons.Filled.GridView, value = posts.size.toString(), label = stringResource(R.string.profile_posts), iconColor = neonPurple, modifier = Modifier.weight(1f))
                StatBox(icon = Icons.Filled.Favorite, value = totalLikes.toString(), label = stringResource(R.string.profile_stats_likes), iconColor = Color(0xFFF72585), modifier = Modifier.weight(1f))
            }
        }

        // 4. Action Buttons (Follow / Message)
        if (!showOwnerTools && !isGuest) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (followRequestPending) {
                            NexaSecondaryButton(text = stringResource(R.string.action_follow_requested), onClick = viewModel::toggleFollow, enabled = !followBusy)
                        } else if (isFollowing) {
                            NexaSecondaryButton(text = stringResource(R.string.action_unfollow), onClick = viewModel::toggleFollow, enabled = !followBusy)
                        } else {
                            NexaPrimaryButton(text = stringResource(R.string.action_follow), onClick = viewModel::toggleFollow, enabled = !followBusy, loading = followBusy)
                        }
                    }
                    if (canMessage) {
                        Box(modifier = Modifier.weight(1f)) {
                            NexaSecondaryButton(text = stringResource(R.string.profile_message), onClick = viewModel::startConversation, icon = Icons.AutoMirrored.Filled.Chat, enabled = !messageBusy)
                        }
                    }
                }
            }
        }

        // 5. Shortcuts
        if (showOwnerTools) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.sm)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                        LargeShortcutCard(title = stringResource(R.string.profile_subscriptions), icon = Icons.Filled.AutoAwesome, modifier = Modifier.weight(1f), onClick = onOpenLabs, color = neonPurple)
                        LargeShortcutCard(title = stringResource(R.string.profile_analytics), icon = Icons.AutoMirrored.Filled.Launch, modifier = Modifier.weight(1f), onClick = onOpenStudio, color = neonBlue)
                    }
                    Spacer(Modifier.height(NexaSpacing.sm))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                        LargeShortcutCard(title = stringResource(R.string.profile_friends), icon = Icons.Filled.Person, modifier = Modifier.weight(1f), onClick = onOpenFriends, color = neonBlue)
                        LargeShortcutCard(title = stringResource(R.string.profile_saved_tab), icon = Icons.Filled.Bookmark, modifier = Modifier.weight(1f), onClick = onOpenSaved, color = neonPurple)
                    }
                    Spacer(Modifier.height(NexaSpacing.sm))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                        LargeShortcutCard(title = stringResource(R.string.title_settings), icon = Icons.Filled.Settings, modifier = Modifier.weight(1f), onClick = onSettings, color = neonBlue)
                        LargeShortcutCard(
                            title = stringResource(R.string.post_share),
                            icon = Icons.Filled.Share,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!sharePost(context, current.id, current.displayName)) {
                                    scope.launch { snackbarHost?.showSnackbar(shareFailedMessage) }
                                }
                            },
                            color = neonPurple
                        )
                    }
                    if (!remoteMode) {
                        Spacer(Modifier.height(NexaSpacing.sm))
                        LocalDataBanner(text = stringResource(R.string.notice_local_account))
                    }
                }
            }
        }

        // 6. Tabs
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = NexaSpacing.lg,
                modifier = Modifier.padding(vertical = NexaSpacing.sm)
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.profile_posts), fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.profile_photos), fontWeight = FontWeight.Bold) })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(stringResource(R.string.profile_no_videos), fontWeight = FontWeight.Bold) }) 
                if (showOwnerTools) {
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text(stringResource(R.string.profile_saved_tab), fontWeight = FontWeight.Bold) })
                    Tab(
                        selected = false,
                        onClick = onOpenFriends,
                        text = { Text(stringResource(R.string.profile_friends), fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        // 7. Tab Content (Fixed spacing & empty state so no large black space appears)
        when (selectedTab) {
            0 -> {
                if (showOwnerTools) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.xs)
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(darkCardBg)
                                .border(1.dp, neonPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable(onClick = onCreatePost),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = neonPurple, modifier = Modifier.size(24.dp))
                                Text(stringResource(R.string.create_new_post), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                val isLockedPrivateProfile = !showOwnerTools && current.isPrivateAccount && !isFollowing
                if (isLockedPrivateProfile) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = NexaSpacing.xl, horizontal = NexaSpacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("هذا الحساب خاص", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("تابع هذا الحساب وأرسل طلب متابعة لعرض منشوراته بعد الموافقة.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                } else if (posts.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.padding(vertical = NexaSpacing.xl)) {
                            NexaEmptyState(
                                icon = Icons.Filled.Person,
                                title = stringResource(R.string.profile_no_posts),
                                body = stringResource(R.string.home_empty_body)
                            )
                        }
                    }
                } else {
                    items(posts, key = { it.id }) { post ->
                        ProfilePostGridItem(
                            post = post,
                            onOpenPost = { onOpenPost(post.id) },
                            onLike = { viewModel.toggleLike(post.id) },
                            onComment = { onOpenPost(post.id) }
                        )
                    }
                }
            }
            1 -> {
                if (photos.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.padding(vertical = NexaSpacing.xl)) {
                            NexaEmptyState(
                                icon = Icons.Filled.GridView,
                                title = stringResource(R.string.profile_empty_photos),
                                body = stringResource(R.string.create_add_image)
                            )
                        }
                    }
                } else {
                    items(photos, key = { "photo_${it.id}" }) { post ->
                        val imageUrl = post.imageUris.firstOrNull() ?: post.imageUri
                        if (!imageUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = NexaSpacing.xs)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenPost(post.id) }
                            ) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
            2 -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.padding(vertical = NexaSpacing.xl)) {
                        NexaEmptyState(
                            icon = Icons.Filled.PlayArrow,
                            title = stringResource(R.string.profile_no_videos),
                            body = stringResource(R.string.profile_videos_body)
                        )
                    }
                }
            }
            3 -> {
                if (savedPosts.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.padding(vertical = NexaSpacing.xl)) {
                            NexaEmptyState(
                                icon = Icons.Filled.Bookmark,
                                title = stringResource(R.string.saved_empty_title),
                                body = stringResource(R.string.saved_empty_body)
                            )
                        }
                    }
                } else {
                    items(savedPosts, key = { "saved_${it.id}" }) { post ->
                        ProfilePostGridItem(
                            post = post,
                            onOpenPost = { onOpenPost(post.id) },
                            onLike = { viewModel.toggleLike(post.id) },
                            onComment = { onOpenPost(post.id) }
                        )
                    }
                }
            }
        }

        // 8. Bottom Sections (Activity & Interactions)
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(horizontal = NexaSpacing.lg, vertical = NexaSpacing.md)) {
                if (showOwnerTools && notifications.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.profile_activity_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = stringResource(R.string.profile_activity_all), style = MaterialTheme.typography.labelMedium, color = neonPurple, modifier = Modifier.clickable { onOpenNotifications() })
                    }
                    Spacer(Modifier.height(NexaSpacing.xs))
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = darkCardBg)) {
                        Column(modifier = Modifier.padding(NexaSpacing.md)) {
                            notifications.take(2).forEach { notif ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                                ) {
                                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = neonPurple, modifier = Modifier.size(18.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = notif.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = TimeUtils.relativeFromNow(notif.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(NexaSpacing.md))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.profile_interaction), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = stringResource(R.string.profile_activity_all), style = MaterialTheme.typography.labelMedium, color = neonPurple, modifier = Modifier.clickable { onOpenNotifications() })
                }
                Spacer(Modifier.height(NexaSpacing.xs))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = darkCardBg)) {
                    Column(modifier = Modifier.padding(NexaSpacing.md)) {
                        if (notifications.isNotEmpty()) {
                            val latestNotif = notifications.first()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                            ) {
                                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = latestNotif.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(text = TimeUtils.relativeFromNow(latestNotif.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            Text(stringResource(R.string.no_interactions_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    val background = if (highlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        Color.Black.copy(alpha = 0.55f)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun StatBox(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable(enabled = onClick != {}, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun LargeShortcutCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    color: Color
) {
    Card(
        modifier = modifier
            .aspectRatio(1.5f)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ProfilePostGridItem(
    post: Post,
    onOpenPost: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit
) {
    val imageUrl = post.imageUris.firstOrNull() ?: post.imageUri
    Box(
        modifier = Modifier
            .padding(horizontal = NexaSpacing.xs)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpenPost() }
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(text = post.text, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val reaction = post.myReaction
                if (reaction != null) {
                    Text(
                        text = reaction,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable { onLike() }
                    )
                } else {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Like",
                        tint = if (post.likedByMe) Color.Red else Color.White,
                        modifier = Modifier.size(16.dp).clickable { onLike() }
                    )
                }
                Text(text = post.likeCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Comment",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp).clickable { onComment() }
                )
                Text(text = post.commentCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


@Composable
private fun ProfileDetailRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
