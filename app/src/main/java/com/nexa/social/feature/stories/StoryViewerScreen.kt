package com.nexa.social.feature.stories

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nexa.social.R
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.ui.components.*
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val NexaStoryPurple = Color(0xFF9B5CFF)
private val NexaStoryDeepPurple = Color(0xFF5425A8)
private val StoryDurationMillis = 6000

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryViewerScreen(
    authorId: String,
    onBack: () -> Unit,
    viewModel: StoriesViewModel = viewModel(
        factory = StoriesViewModel.Factory
    )
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val audience by viewModel.audience.collectAsStateWithLifecycle()
    val group = groups.firstOrNull { it.author.id == authorId }

    if (group == null || group.stories.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NexaSpacing.sm)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close)
                    )
                }
            }

            NexaEmptyState(
                icon = Icons.Filled.Close,
                title = stringResource(R.string.story_expired_title),
                body = stringResource(R.string.story_expired_body)
            )
        }
        return
    }

    val pagerState = rememberPagerState(
        pageCount = { group.stories.size }
    )

    val scope = rememberCoroutineScope()

    var reply by rememberSaveable(authorId) {
        mutableStateOf("")
    }

    var replyFailure by remember {
        mutableStateOf<FailureReason?>(null)
    }

    var replyFocused by remember {
        mutableStateOf(false)
    }

    var sendingReply by remember {
        mutableStateOf(false)
    }

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    var deletingStory by remember {
        mutableStateOf(false)
    }

    var showViewersSheet by remember {
        mutableStateOf(false)
    }

    var paused by rememberSaveable(authorId) {
        mutableStateOf(false)
    }

    val currentStory = group.stories.getOrNull(
        pagerState.currentPage
    )

    val progress = remember(currentStory?.id) {
        Animatable(0f)
    }

    // Mark other people's stories as viewed, and load the audience for my own stories.
    LaunchedEffect(currentStory?.id, currentStory?.isMine) {
        currentStory?.let { story ->
            if (story.isMine) {
                viewModel.loadAudience(story.id)
            } else {
                viewModel.markViewed(story)
            }
        }
    }

    // Refresh the audience whenever the sheet is opened.
    LaunchedEffect(showViewersSheet, currentStory?.id) {
        val story = currentStory
        if (showViewersSheet && story != null && story.isMine) {
            viewModel.loadAudience(story.id, force = true)
        }
    }

    // Real timed progress. Pause while replying, viewing viewers sheet, or manually paused.
    LaunchedEffect(
        currentStory?.id,
        replyFocused,
        paused,
        deletingStory,
        showViewersSheet
    ) {
        if (
            currentStory == null ||
            replyFocused ||
            paused ||
            deletingStory ||
            showViewersSheet
        ) {
            return@LaunchedEffect
        }

        val remaining = (
            StoryDurationMillis * (1f - progress.value)
        ).toInt().coerceAtLeast(1)

        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = remaining
            )
        )

        // Only advance if this story is still the selected story.
        if (
            currentStory.id ==
            group.stories.getOrNull(pagerState.currentPage)?.id
        ) {
            if (pagerState.currentPage < group.stories.lastIndex) {
                pagerState.animateScrollToPage(
                    pagerState.currentPage + 1
                )
            } else {
                onBack()
            }
        }
    }

    // Keep the page index valid if the current story is deleted.
    LaunchedEffect(group.stories.size) {
        if (
            group.stories.isNotEmpty() &&
            pagerState.currentPage >= group.stories.size
        ) {
            pagerState.scrollToPage(
                group.stories.lastIndex
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !sendingReply && !deletingStory && !showViewersSheet
        ) { page ->
            val story = group.stories[page]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF170D28),
                                Color.Black,
                                Color(0xFF100A1B)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (story.imageUri.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        NexaStoryDeepPurple,
                                        Color(0xFF211033),
                                        Color(0xFF08070D)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = story.text.orEmpty(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 28.dp,
                                    vertical = 64.dp
                                )
                        )
                    }
                } else {
                    AsyncImage(
                        model = Uri.parse(story.imageUri),
                        contentDescription = story.text,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!story.text.isNullOrBlank()) {
                        Text(
                            text = story.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.88f)
                                        )
                                    )
                                )
                                .padding(
                                    horizontal = 24.dp,
                                    vertical = 44.dp
                                )
                        )
                    }
                }
            }
        }

        // Readability overlays.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.82f),
                            Color.Black.copy(alpha = 0.24f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(210.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.38f),
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        // Tap zones stay below the header and reply controls.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 115.dp, bottom = 100.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        enabled = !sendingReply && !deletingStory && !showViewersSheet,
                        indication = null,
                        interactionSource = remember {
                            MutableInteractionSource()
                        }
                    ) {
                        scope.launch {
                            if (pagerState.currentPage > 0) {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage - 1
                                )
                            }
                        }
                    }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        enabled = !sendingReply && !deletingStory && !showViewersSheet,
                        indication = null,
                        interactionSource = remember {
                            MutableInteractionSource()
                        }
                    ) {
                        scope.launch {
                            if (
                                pagerState.currentPage <
                                group.stories.lastIndex
                            ) {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1
                                )
                            } else {
                                onBack()
                            }
                        }
                    }
            )
        }

        // Story progress indicators.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(
                    horizontal = NexaSpacing.md,
                    vertical = NexaSpacing.md
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                group.stories.forEachIndexed { index, story ->
                    val value = when {
                        index < pagerState.currentPage -> 1f
                        index > pagerState.currentPage -> 0f
                        currentStory?.id == story.id -> progress.value
                        else -> 0f
                    }

                    LinearProgressIndicator(
                        progress = { value },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape),
                        color = if (
                            index == pagerState.currentPage
                        ) {
                            NexaStoryPurple
                        } else {
                            Color.White
                        },
                        trackColor = Color.White.copy(alpha = 0.32f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NexaAvatar(
                    displayName = group.author.displayName,
                    avatarUri = group.author.avatarUri,
                    size = 42.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = group.author.displayName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )

                    currentStory?.let { story ->
                        Text(
                            text = TimeUtils.relativeFromNow(
                                story.createdAt
                            ),
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                IconButton(
                    onClick = { paused = !paused },
                    enabled = !replyFocused && !deletingStory && !showViewersSheet
                ) {
                    Icon(
                        imageVector = if (paused) {
                            Icons.Filled.PlayArrow
                        } else {
                            Icons.Filled.Pause
                        },
                        contentDescription = if (paused) {
                            "استئناف القصة"
                        } else {
                            "إيقاف القصة مؤقتًا"
                        },
                        tint = Color.White
                    )
                }

                if (currentStory?.isMine == true) {
                    IconButton(
                        onClick = {
                            showDeleteDialog = true
                        },
                        enabled = !deletingStory
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(
                                R.string.story_delete
                            ),
                            tint = Color.White
                        )
                    }
                }

                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(
                            R.string.action_close
                        ),
                        tint = Color.White
                    )
                }
            }
        }

        // Owner-only audience button backed by real story_views + story replies.
        if (currentStory != null && currentStory.isMine) {
            val audienceForStory = audience.takeIf { it.storyId == currentStory.id }
            val audienceCount = audienceForStory
                ?.takeIf { !it.isLoading && it.error == null }
                ?.members?.size

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            ) {
                Surface(
                    onClick = { showViewersSheet = true },
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E1432).copy(alpha = 0.9f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = NexaStoryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (audienceCount != null) {
                                stringResource(
                                    R.string.story_viewers_title_count,
                                    audienceCount
                                )
                            } else {
                                stringResource(R.string.story_viewers_title)
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (audienceForStory?.isLoading == true) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = NexaStoryPurple
                            )
                        }
                    }
                }
            }
        }

        // Reply composer for stories belonging to other users.
        if (currentStory != null && !currentStory.isMine) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .padding(
                        horizontal = NexaSpacing.md,
                        vertical = NexaSpacing.lg
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val error = failureMessage(replyFailure)

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Color(0xFF21162F).copy(alpha = 0.96f)
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 4.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = reply,
                        onValueChange = {
                            reply = it
                            replyFailure = null
                        },
                        placeholder = {
                            Text(
                                text = stringResource(
                                    R.string.story_reply_hint
                                ),
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = NexaStoryPurple,
                            focusedBorderColor = NexaStoryPurple,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged {
                                replyFocused = it.isFocused
                            }
                    )

                    if (sendingReply) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(22.dp),
                            color = NexaStoryPurple,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            enabled = reply.isNotBlank(),
                            onClick = {
                                val story = currentStory
                                if (
                                    story.isMine ||
                                    sendingReply ||
                                    reply.isBlank()
                                ) {
                                    return@IconButton
                                }

                                sendingReply = true
                                replyFailure = null

                                viewModel.replyToStory(
                                    story = story,
                                    text = reply.trim()
                                ) { failure ->
                                    sendingReply = false
                                    replyFailure = failure

                                    if (failure == null) {
                                        reply = ""
                                        replyFocused = false
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(
                                    R.string.story_reply_send
                                ),
                                tint = if (reply.isNotBlank()) {
                                    NexaStoryPurple
                                } else {
                                    Color.White.copy(alpha = 0.45f)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (deletingStory) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = NexaStoryPurple
                )
            }
        }
    }

    // Bottom Sheet: real story audience (viewers + reply interactions).
    val storyForSheet = currentStory
    if (showViewersSheet && storyForSheet != null && storyForSheet.isMine) {
        val sheetState = audience.takeIf { it.storyId == storyForSheet.id }
            ?: StoryAudienceUiState(storyId = storyForSheet.id, isLoading = true)
        StoryAudienceSheet(
            state = sheetState,
            isLocal = storyForSheet.isLocal,
            onRefresh = {
                viewModel.loadAudience(storyForSheet.id, force = true)
            },
            onDismiss = { showViewersSheet = false }
        )
    }

    // Confirm before deleting the current user's story.
    if (showDeleteDialog && currentStory?.isMine == true) {
        AlertDialog(
            onDismissRequest = {
                if (!deletingStory) {
                    showDeleteDialog = false
                }
            },
            title = {
                Text("حذف القصة؟")
            },
            text = {
                Text("هل تريد حذف هذه القصة؟ لا يمكن التراجع عن هذا الإجراء.")
            },
            confirmButton = {
                TextButton(
                    enabled = !deletingStory,
                    onClick = {
                        val story = currentStory
                        if (!story.isMine) {
                            showDeleteDialog = false
                            return@TextButton
                        }

                        deletingStory = true

                        viewModel.deleteStory(story.id) {
                            deletingStory = false
                            showDeleteDialog = false

                            if (group.stories.size <= 1) {
                                onBack()
                            } else {
                                scope.launch {
                                    val target = (
                                        pagerState.currentPage - 1
                                    ).coerceAtLeast(0)

                                    pagerState.animateScrollToPage(
                                        target
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (deletingStory) {
                            "جارٍ الحذف..."
                        } else {
                            "حذف"
                        },
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deletingStory,
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text("إلغاء")
                }
            },
            containerColor = Color(0xFF21162F),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}
