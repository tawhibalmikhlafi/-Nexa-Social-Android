package com.nexa.social.feature.pulse

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import java.time.Duration
import java.time.Instant
import com.nexa.social.domain.model.Community
import com.nexa.social.domain.model.UserSummary
import com.nexa.social.feature.worlds.categoryLabel
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaEmptyState
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.SectionHeader
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun PulseScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenWorld: (String) -> Unit,
    viewModel: PulseViewModel = viewModel(factory = PulseViewModel.Factory)
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val daily by viewModel.dailyState.collectAsStateWithLifecycle()
    val commentsState by viewModel.commentsState.collectAsStateWithLifecycle()
    val friendsState by viewModel.friendsState.collectAsStateWithLifecycle()
    var entryText by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<DailyPulseEntry?>(null) }
    var popularFirst by remember { mutableStateOf(false) }
    var showFriendChallengeDialog by remember { mutableStateOf(false) }
    var friendChallengeMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedVideoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedImageUri = it }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedVideoUri = it }
    LaunchedEffect(daily.myEntryId) {
        if (daily.myEntryId != null) {
            entryText = ""
            selectedImageUri = null
            selectedVideoUri = null
        }
    }
    LaunchedEffect(selectedEntry?.id) {
        commentText = ""
        selectedEntry?.id?.let(viewModel::loadComments)
    }
    LaunchedEffect(commentsState.comments, commentsState.submitting) {
        if (!commentsState.submitting && commentText.isNotBlank() &&
            commentsState.comments.any { it.body == commentText.trim() }) {
            commentText = ""
        }
    }

    val prompt = daily.prompt

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.pulse_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = NexaSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            item {
                NexaCard(modifier = Modifier.padding(horizontal = NexaSpacing.lg)) {
                    Text("⚡ نبض اليوم", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    when {
                        daily.loading -> Text("جارٍ تحميل تحدي اليوم…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        prompt == null -> Text(
                            daily.message ?: "لا يوجد تحدٍ منشور الآن. سيظهر التحدي هنا عندما ينشره فريق Nexa.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> {
                            val activePrompt = requireNotNull(prompt)
                            Text(activePrompt.title, style = MaterialTheme.typography.titleMedium)
                            if (activePrompt.description.isNotBlank()) {
                                Text(activePrompt.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val remaining = runCatching { Duration.between(Instant.now(), Instant.parse(activePrompt.endsAt)).toMinutes().coerceAtLeast(0) }.getOrNull()
                            Text(
                                remaining?.let { "ينتهي التحدي بعد ${it / 60} ساعة و${it % 60} دقيقة" } ?: "تحدٍ يومي من مجتمع Nexa",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(onClick = {
                                val shareText = "⚡ نبض Nexa اليوم: ${activePrompt.title}\n${activePrompt.description}\nشاركنا إجابتك داخل تطبيق Nexa Social."
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }, "مشاركة تحدّي نبض"))
                            }, modifier = Modifier.fillMaxWidth()) { Text("شارك التحدّي خارج Nexa") }
                            Text(
                                if (daily.myEntryId != null) "شاركت في تحدي اليوم — يمكنك استكشاف مشاركات المجتمع."
                                else "شارك بإجابة واحدة، واجعل نبض Nexa يبدأ منك.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (daily.myEntryId == null) {
                                OutlinedTextField(
                                    value = entryText,
                                    onValueChange = { if (it.length <= 1000) entryText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("اكتب مشاركتك") },
                                    minLines = 2,
                                    maxLines = 4
                                )
                                Button(
                                    onClick = { imagePicker.launch("image/*") },
                                    enabled = !daily.submitting && selectedVideoUri == null,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (selectedImageUri == null) "إرفاق صورة (حتى 5MB)" else "تغيير الصورة") }
                                Button(
                                    onClick = { videoPicker.launch("video/*") },
                                    enabled = !daily.submitting && selectedImageUri == null,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (selectedVideoUri == null) "إرفاق فيديو (حتى 50MB)" else "تغيير الفيديو") }
                                selectedImageUri?.let { uri ->
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "الصورة المرفقة",
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    )
                                    Text("تم اختيار صورة للمشاركة", color = MaterialTheme.colorScheme.primary)
                                }
                                selectedVideoUri?.let { uri ->
                                    Text("معاينة الفيديو", color = MaterialTheme.colorScheme.primary)
                                    PulseVideoPlayer(uri.toString())
                                }
                                Button(
                                    onClick = {
                                        viewModel.publish(entryText, selectedImageUri?.toString(), selectedVideoUri?.toString())
                                    },
                                    enabled = (entryText.isNotBlank() || selectedImageUri != null || selectedVideoUri != null) && !daily.submitting,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (daily.submitting) "جارٍ الرفع والنشر…" else "انشر مشاركتك")
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("مشاركتك منشورة", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { viewModel.deleteMyEntry() }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف مشاركتي")
                                    }
                                }
                            }
                        }
                    }
                    daily.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    friendsState.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    if (!daily.loading && daily.prompt != null) {
                        Text(
                            "المشاركات: ${daily.entries.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (daily.badges.isNotEmpty()) {
                item {
                    NexaCard(modifier = Modifier.padding(horizontal = NexaSpacing.lg)) {
                        Text("🏅 شاراتك في نبض", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        daily.badges.forEach { badge ->
                            Column(modifier = Modifier.padding(top = NexaSpacing.sm)) {
                                Text(
                                    "${if (badge.isEarned) "🏆" else "🔒"} ${badge.title} · ${if (badge.isEarned) "مكتسبة" else "غير مكتسبة بعد"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (badge.isEarned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(badge.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (friendsState.challenges.isNotEmpty()) {
                item {
                    NexaCard(modifier = Modifier.padding(horizontal = NexaSpacing.lg)) {
                        Text("تحديات الأصدقاء", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        friendsState.challenges.forEach { challenge ->
                            Column(modifier = Modifier.fillMaxWidth().padding(top = NexaSpacing.sm)) {
                                Text(
                                    if (challenge.isIncoming) "تحدي من ${challenge.challengerName}" else "تحدي أرسلته إلى ${challenge.challengedName}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (challenge.message.isNotBlank()) Text(challenge.message, style = MaterialTheme.typography.bodySmall)
                                if (challenge.isIncoming) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                                        TextButton(onClick = { viewModel.respondToFriendChallenge(challenge.id, true) }) { Text("قبول") }
                                        TextButton(onClick = { viewModel.respondToFriendChallenge(challenge.id, false) }) { Text("رفض") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = NexaSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Text("مشاركات المجتمع", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { popularFirst = !popularFirst }) {
                        Text(if (popularFirst) "الأحدث أولًا" else "الأكثر تفاعلًا")
                    }
                }
            }
            items(if (popularFirst) daily.entries.sortedWith(compareByDescending<DailyPulseEntry> { it.reactions }.thenByDescending { it.createdAt }) else daily.entries, key = { it.id }) { entry ->
                NexaCard(modifier = Modifier.padding(horizontal = NexaSpacing.lg).clickable { selectedEntry = entry }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("@${entry.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.toggleReaction(entry) }) {
                            Icon(
                                if (entry.likedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "إعجاب"
                            )
                        }
                        Text(entry.reactions.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                    if (entry.body.isNotBlank()) Text(entry.body, style = MaterialTheme.typography.bodyMedium)
                    Text("عرض التفاصيل", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    entry.imageUrl?.let { image ->
                        AsyncImage(
                            model = image,
                            contentDescription = "صورة مشاركة نبض Nexa",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        )
                    }
                    entry.videoUrl?.let { video -> PulseVideoPlayer(video) }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.pulse_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NexaSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.md)
                ) {
                    PulseStat(
                        value = snapshot.totalPosts.toString(),
                        label = stringResource(R.string.pulse_stat_posts),
                        modifier = Modifier.weight(1f)
                    )
                    PulseStat(
                        value = snapshot.activeToday.toString(),
                        label = stringResource(R.string.pulse_stat_active),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (snapshot.tags.isEmpty() && snapshot.creators.isEmpty()) {
                item {
                    NexaEmptyState(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.pulse_empty),
                        body = stringResource(R.string.pulse_empty_body)
                    )
                }
            } else {
                if (snapshot.tags.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.pulse_trending_tags),
                            modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = NexaSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                        ) {
                            items(snapshot.tags, key = { it.name }) { tag ->
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
                                        .padding(horizontal = NexaSpacing.md, vertical = NexaSpacing.sm)
                                ) {
                                    Text(
                                        text = "#${tag.name}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.pulse_tag_posts,
                                            tag.posts,
                                            tag.engagement
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (snapshot.creators.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.pulse_trending_creators),
                            modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                        )
                    }
                    items(snapshot.creators, key = { it.user.id }) { creator ->
                        CreatorRow(
                            creator = creator,
                            onClick = { onOpenProfile(creator.user.id) },
                            modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                        )
                    }
                }

                if (snapshot.worlds.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.pulse_worlds),
                            modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                        )
                    }
                    items(snapshot.worlds, key = { it.community.id }) { world ->
                        WorldRow(
                            world = world,
                            onClick = { onOpenWorld(world.community.id) },
                            modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                        )
                    }
                }
            }

            item {
                LocalDataBanner(
                    text = stringResource(R.string.pulse_local_note),
                    modifier = Modifier.padding(horizontal = NexaSpacing.lg)
                )
            }
        }
    }
    if (showFriendChallengeDialog) {
        AlertDialog(
            onDismissRequest = { showFriendChallengeDialog = false },
            title = { Text("تحدَّ أحد أصدقائك") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                    Text("تظهر هنا الحسابات التي تتابعك وتتابعها.")
                    OutlinedTextField(
                        value = friendChallengeMessage,
                        onValueChange = { if (it.length <= 240) friendChallengeMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("رسالة قصيرة (اختياري)") },
                        maxLines = 2
                    )
                    when {
                        friendsState.loading -> Text("جارٍ تحميل الأصدقاء…")
                        friendsState.friends.isEmpty() -> Text("لا يوجد أصدقاء بمتابعة متبادلة حالياً.")
                        else -> friendsState.friends.forEach { friend ->
                            TextButton(
                                onClick = {
                                    viewModel.sendFriendChallenge(friend.id, friendChallengeMessage)
                                    showFriendChallengeDialog = false
                                    friendChallengeMessage = ""
                                },
                                enabled = !friendsState.submitting,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${friend.displayName}  @${friend.username}")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFriendChallengeDialog = false }) { Text("إغلاق") } }
        )
    }
    selectedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text("مشاركة ${entry.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)) {
                    Text("@${entry.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.body.isNotBlank()) Text(entry.body)
                    entry.imageUrl?.let { image ->
                        AsyncImage(model = image, contentDescription = "صورة المشاركة",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)))
                    }
                    entry.videoUrl?.let { video -> PulseVideoPlayer(video) }
                    Text("❤ ${entry.reactions} إعجاب · ${if (commentsState.entryId == entry.id) commentsState.comments.size else "…"} تعليق", style = MaterialTheme.typography.labelMedium)
                    Text("التعليقات", style = MaterialTheme.typography.titleSmall)
                    when {
                        commentsState.entryId != entry.id || commentsState.loading -> Text("جارٍ تحميل التعليقات…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        commentsState.comments.isEmpty() && commentsState.message != null -> Text(commentsState.message!!, color = MaterialTheme.colorScheme.error)
                        commentsState.comments.isEmpty() -> Text("كن أول من يعلّق", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> commentsState.comments.takeLast(8).forEach { comment ->
                            Column(modifier = Modifier.fillMaxWidth().padding(top = NexaSpacing.xs)) {
                                Text(comment.displayName + "  @" + comment.username, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(comment.body, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    commentsState.message?.takeIf { commentsState.comments.isNotEmpty() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { if (it.length <= 500) commentText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("اكتب تعليقًا (حتى 500 حرف)") },
                        maxLines = 3
                    )
                    Button(
                        onClick = {
                            viewModel.addComment(entry.id, commentText)
                        },
                        enabled = commentText.isNotBlank() && !commentsState.submitting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (commentsState.submitting) "جارٍ النشر…" else "إضافة تعليق") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val shareText = buildString {
                        append("مشاركة من نبض Nexa بواسطة ${entry.displayName}")
                        if (entry.body.isNotBlank()) append("\n${entry.body}")
                        entry.imageUrl?.let { append("\n${it}") }
                    }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText)
                    }, "مشاركة نبض Nexa"))
                }) { Text("مشاركة") }
            },
            dismissButton = { TextButton(onClick = { selectedEntry = null }) { Text("إغلاق") } }
        )
    }
}

@Composable
private fun PulseStat(value: String, label: String, modifier: Modifier = Modifier) {
    NexaCard(modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CreatorRow(
    creator: PulseCreator,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexaCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NexaAvatar(
                displayName = creator.user.displayName,
                avatarUri = creator.user.avatarUri,
                size = 44.dp
            )
            Spacer(Modifier.size(NexaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = creator.user.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "@${creator.user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(
                    R.string.pulse_creator_meta,
                    creator.posts,
                    creator.engagement
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun WorldRow(
    world: PulseWorld,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val community: Community = world.community
    NexaCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = community.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = categoryLabel(community.category),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(
                        R.string.pulse_world_meta,
                        community.sampleMemberCount,
                        world.posts
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.pulse_world_open),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }


    }
}


@Composable
private fun PulseVideoPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
    )
}
