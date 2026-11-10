package com.nexa.social.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.domain.model.Audience
import com.nexa.social.ui.components.*
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun parseTags(input: String): List<String> = input
    .split(Regex("[,\\s]+"))
    .map { it.trim().removePrefix("#") }
    .filter { it.isNotBlank() }
    .distinct()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    onPublished: () -> Unit,
    viewModel: CreatePostViewModel = viewModel(factory = CreatePostViewModel.Factory)
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val audience by viewModel.audience.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val publishing by viewModel.publishing.collectAsStateWithLifecycle()
    val remoteMode by viewModel.remoteMode.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    // Inherit the app-wide appearance and accent from NexaTheme settings.
    val colorScheme = MaterialTheme.colorScheme
    val NexaBackground = colorScheme.background
    val NexaSurface = colorScheme.surface
    val NexaSurfaceVariant = colorScheme.surfaceVariant
    val NexaPurple = colorScheme.primary
    val NexaLavender = colorScheme.tertiary
    val NexaText = colorScheme.onSurface
    val NexaMuted = colorScheme.onSurfaceVariant

    var text by rememberSaveable { mutableStateOf("") }
    var tagsText by rememberSaveable { mutableStateOf("") }
    var locationText by rememberSaveable { mutableStateOf("") }
    var imageUris by remember { mutableStateOf(emptyList<String>()) }
    var prefilled by remember { mutableStateOf(false) }

    // Interactive Sheets States
    var showLocationSheet by remember { mutableStateOf(false) }
    var showAdvancedSheet by remember { mutableStateOf(false) }

    // Advanced Options States
    var allowComments by remember { mutableStateOf(true) }
    var pinPost by remember { mutableStateOf(false) }
    var hideLikes by remember { mutableStateOf(false) }

    var tempLocationInput by remember { mutableStateOf("") }

    val mentionUsers by viewModel.mentionSuggestions.collectAsStateWithLifecycle()
    var showMentionSuggestions by remember { mutableStateOf(false) }

    val mentionQuery = if (showMentionSuggestions) {
        val lastAt = text.lastIndexOf('@')
        if (lastAt != -1) text.substring(lastAt + 1).trim() else ""
    } else ""

    val filteredUsers = mentionUsers

    LaunchedEffect(mentionQuery, showMentionSuggestions) {
        if (showMentionSuggestions && mentionQuery.isNotBlank()) {
            delay(250)
            viewModel.searchMentionUsers(mentionQuery)
        } else {
            viewModel.clearMentionSuggestions()
        }
    }

    LaunchedEffect(draft) {
        val current = draft
        if (!prefilled && current != null) {
            text = current.text
            tagsText = current.tags.joinToString(", ")
            imageUris = current.allImages()
            prefilled = true
        }
    }

    val draftSavedMessage = stringResource(R.string.create_draft_saved)
    val publishedMessage = stringResource(
        if (remoteMode) R.string.create_published_remote else R.string.create_published_local
    )
    val errorMessage = failureMessage(
        (status as? CreatePostStatus.Error)?.reason
    )
    val publishFailedFormat = stringResource(R.string.create_publish_failed_reason)
    val publishFailedMessage = errorMessage?.let { publishFailedFormat.format(it) }
    val isPublishing = publishing || status is CreatePostStatus.Published

    LaunchedEffect(status) {
        when (status) {
            CreatePostStatus.DraftSaved -> {
                snackbarHost?.showSnackbar(draftSavedMessage)
                viewModel.consumeStatus()
            }
            CreatePostStatus.Published -> {
                scope.launch { snackbarHost?.showSnackbar(publishedMessage) }
                delay(1600)
                viewModel.consumeStatus()
                onPublished()
            }
            is CreatePostStatus.Error -> {
                publishFailedMessage?.let { snackbarHost?.showSnackbar(it) }
            }
            else -> Unit
        }
    }

    val pickImage = rememberImagePicker { uri ->
        uri?.toString()?.let { picked -> imageUris = imageUris + picked }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NexaBackground)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // الشريط العلوي
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(NexaSurface)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPublished) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = NexaLavender
                    )
                }

                Text(
                    text = "إنشاء منشور",
                    color = NexaText,
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleLarge
                )

                Button(
                    onClick = {
                        val advancedTags = mutableListOf<String>()
                        if (pinPost) advancedTags.add("مثبت")
                        if (hideLikes) advancedTags.add("إخفاء_الإعجابات")
                        if (!allowComments) advancedTags.add("تعليقات_مغلقة")
                        val combinedTags = (parseTags(tagsText) + 
                            (if (locationText.isNotBlank()) listOf("موقع:$locationText") else emptyList<String>()) +
                            advancedTags).distinct()
                        viewModel.publish(text, imageUris, combinedTags)
                    },
                    enabled = (text.isNotBlank() || imageUris.isNotEmpty()) && !isPublishing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NexaPurple,
                        disabledContainerColor = colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("نشر", fontSize = 15.sp)
                }
            }

            if (!remoteMode) {
                LocalDataBanner(text = stringResource(R.string.notice_local_data_body))
            }

            // معلومات المشارك / الجمهور
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(NexaSurface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(NexaPurple)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    Column {
                        Text(
                            text = "شارك ك: Nexa User",
                            color = NexaText,
                            fontSize = 14.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Public,
                                contentDescription = null,
                                tint = NexaMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = audienceLabel(audience),
                                color = NexaMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                IconButton(onClick = {
                    val nextAudience = when(audience) {
                        Audience.PUBLIC_LOCAL -> Audience.FOLLOWERS
                        Audience.FOLLOWERS -> Audience.ONLY_ME
                        Audience.ONLY_ME -> Audience.PUBLIC_LOCAL
                    }
                    viewModel.setAudience(nextAudience)
                }) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "تغيير الجمهور",
                        tint = NexaLavender
                    )
                }
            }

            // بطاقة كتابة المنشور
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                NexaSurface,
                                NexaSurfaceVariant
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(16.dp)
            ) {
                if (showMentionSuggestions && filteredUsers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NexaSurfaceVariant, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredUsers.take(5).forEach { user ->
                            TextButton(onClick = {
                                text = text.substringBeforeLast("@") + "@${user.username} "
                                showMentionSuggestions = false
                            }) {
                                Text("@${user.username}", color = NexaLavender, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                NexaTextField(
                    value = text,
                    onValueChange = { value ->
                        if (value.length <= 500) {
                            text = value
                            showMentionSuggestions = value.endsWith("@") || (value.contains("@") && !value.endsWith(" "))
                        }
                    },
                    label = "ماذا في بالك؟...",
                    singleLine = false,
                    imeAction = ImeAction.Default
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { text += " 😊" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.SentimentSatisfied, contentDescription = "إيموجي", tint = NexaMuted, modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = {
                                text += "@"
                                showMentionSuggestions = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.AlternateEmail, contentDescription = "إشارة", tint = NexaMuted, modifier = Modifier.size(20.dp))
                        }
                    }

                    Text(
                        "${text.length}/500",
                        color = NexaMuted,
                        fontSize = 12.sp
                    )
                }
            }

            // إضافة الوسائط: هذا المسار يدعم اختيار الصور فعلياً
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickImage() },
                shape = RoundedCornerShape(20.dp),
                color = NexaSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(NexaPurple.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = NexaLavender, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إضافة صور", color = NexaText, fontSize = 14.sp, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (imageUris.isEmpty()) "اختر صوراً لإرفاقها بالمنشور" else "تم اختيار ${imageUris.size} صورة",
                            color = NexaMuted,
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Default.AddCircle, contentDescription = "إضافة صور", tint = NexaPurple, modifier = Modifier.size(26.dp))
                }
            }

            // معاينة الصور المختارة
            if (imageUris.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("معاينة الصور", color = NexaText, style = MaterialTheme.typography.titleSmall)
                    Text("${imageUris.size} مرفق", color = NexaMuted, fontSize = 12.sp)
                }
            }
            imageUris.forEach { uri ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NexaSurface)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NexaCover(
                        seed = uri.hashCode().toLong(),
                        coverUri = uri,
                        height = 160.dp
                    )
                    TextButton(
                        onClick = { imageUris = imageUris - uri },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("إزالة الصورة", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            // خيارات المنشور (الخصوصية، المتراجعون، الموقع، الوسوم)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexaSurface, RoundedCornerShape(18.dp))
            ) {
                ListItem(
                    headlineContent = { Text("الخصوصية", color = NexaText, fontSize = 14.sp) },
                    supportingContent = { Text("من يمكنه رؤية منشورك؟", color = NexaMuted, fontSize = 12.sp) },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null, tint = NexaLavender) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(audienceLabel(audience), color = NexaLavender, fontSize = 13.sp)
                            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = NexaMuted)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val next = when(audience) {
                                Audience.PUBLIC_LOCAL -> Audience.FOLLOWERS
                                Audience.FOLLOWERS -> Audience.ONLY_ME
                                Audience.ONLY_ME -> Audience.PUBLIC_LOCAL
                            }
                            viewModel.setAudience(next)
                        }
                )

                HorizontalDivider(color = NexaBackground, thickness = 1.dp)

                // الموقع (تفعيل الاختيار)
                ListItem(
                    headlineContent = { Text("الموقع", color = NexaText, fontSize = 14.sp) },
                    supportingContent = {
                        Text(
                            if (locationText.isBlank()) "إضافة موقع (اختياري)" else locationText,
                            color = NexaMuted,
                            fontSize = 12.sp
                        )
                    },
                    leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = NexaLavender) },
                    trailingContent = { Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = NexaMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            tempLocationInput = locationText
                            showLocationSheet = true
                        }
                )

                HorizontalDivider(color = NexaBackground, thickness = 1.dp)

                // الوسوم
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Tag, contentDescription = null, tint = NexaLavender)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الوسوم", color = NexaText, fontSize = 14.sp)
                            Text("إضافة وسوم لمزيد من الانتشار", color = NexaMuted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    NexaTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        label = "أدخل الوسوم مفصولة بفواصل (مثال: تقنية, برمجة)",
                        singleLine = true,
                        imeAction = ImeAction.Done
                    )
                }
            }

            // خيارات متقدمة (تفعيل النوافذ والخيارات)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedSheet = true },
                shape = RoundedCornerShape(16.dp),
                color = NexaSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = NexaLavender)
                        Column {
                            Text("خيارات متقدمة", color = NexaText, fontSize = 14.sp)
                            Text(
                                buildString {
                                    if (pinPost) append("• مثبت ")
                                    if (hideLikes) append("• إخفاء الإعجابات ")
                                    if (!allowComments) append("• تعليقات مغلقة")
                                    if (this.isEmpty()) append("التعليقات، التثبيت، الإعجابات")
                                },
                                color = NexaMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = NexaMuted
                    )
                }
            }

            // نشر بأمان
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexaSurface, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = NexaPurple,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        "نشر بأمان",
                        color = NexaText,
                        fontSize = 14.sp
                    )
                    Text(
                        "محتواك محمي وفق سياسات الأمان والخصوصية",
                        color = NexaMuted,
                        fontSize = 12.sp
                    )
                }
            }

            if (publishFailedMessage != null) {
                Text(
                    text = publishFailedMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (uploadProgress in 1..99) {
                Text(
                    text = stringResource(R.string.create_uploading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { uploadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NexaSecondaryButton(
                    text = stringResource(R.string.create_save_draft),
                    onClick = { viewModel.saveDraft(text, imageUris, parseTags(tagsText)) },
                    icon = Icons.Filled.Save,
                    enabled = !isPublishing,
                    modifier = Modifier.weight(1f)
                )
                NexaPrimaryButton(
                    text = stringResource(R.string.create_publish),
                    enabled = (text.isNotBlank() || imageUris.isNotEmpty()) && !isPublishing,
                    loading = isPublishing,
                    onClick = {
                        val advancedTags = mutableListOf<String>()
                        if (pinPost) advancedTags.add("مثبت")
                        if (hideLikes) advancedTags.add("إخفاء_الإعجابات")
                        if (!allowComments) advancedTags.add("تعليقات_مغلقة")
                        val combinedTags = (parseTags(tagsText) + 
                            (if (locationText.isNotBlank()) listOf("موقع:$locationText") else emptyList<String>()) +
                            advancedTags).distinct()
                        viewModel.publish(text, imageUris, combinedTags)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Bottom Sheet: Location
        if (showLocationSheet) {
            NexaBottomSheet(onDismiss = { showLocationSheet = false }) {
                SheetTitle("إضافة موقع المنشور")
                Spacer(Modifier.height(8.dp))
                NexaTextField(
                    value = tempLocationInput,
                    onValueChange = { tempLocationInput = it },
                    label = "اسم المدينة أو المكان",
                    singleLine = true,
                    imeAction = ImeAction.Done
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NexaSecondaryButton(
                        text = "إزالة الموقع",
                        onClick = {
                            locationText = ""
                            showLocationSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    NexaPrimaryButton(
                        text = "حفظ الموقع",
                        onClick = {
                            locationText = tempLocationInput.trim()
                            showLocationSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Bottom Sheet: Advanced Options
        if (showAdvancedSheet) {
            NexaBottomSheet(onDismiss = { showAdvancedSheet = false }) {
                SheetTitle("خيارات متقدمة للمنشور")
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("السماح بالتعليقات", color = NexaText, fontSize = 15.sp)
                        Text("الساق للآخرين بالتعليق على هذا المنشور", color = NexaMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = allowComments,
                        onCheckedChange = { allowComments = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NexaPurple)
                    )
                }

                HorizontalDivider(color = NexaBackground, thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تثبيت المنشور", color = NexaText, fontSize = 15.sp)
                        Text("عرض المنشور في أعلى الملف الشخصي", color = NexaMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = pinPost,
                        onCheckedChange = { pinPost = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NexaPurple)
                    )
                }

                HorizontalDivider(color = NexaBackground, thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("إخفاء عدد الإعجابات", color = NexaText, fontSize = 15.sp)
                        Text("عدم إظهار عدد الإعجابات للعامة", color = NexaMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = hideLikes,
                        onCheckedChange = { hideLikes = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NexaPurple)
                    )
                }

                Spacer(Modifier.height(16.dp))
                NexaPrimaryButton(
                    text = "تم",
                    onClick = { showAdvancedSheet = false }
                )
            }
        }
    }
}
