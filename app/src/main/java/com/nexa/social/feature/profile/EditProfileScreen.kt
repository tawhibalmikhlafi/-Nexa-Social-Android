
package com.nexa.social.feature.profile
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.core.ui.rememberRemoteMode
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaAvatar
import com.nexa.social.ui.components.NexaCover
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.rememberImagePicker
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel =
        viewModel(factory = EditProfileViewModel.Factory)
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val remoteMode = rememberRemoteMode()
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.edit_profile_saved)
    val photoSavedMessage = stringResource(R.string.edit_profile_photo_saved)
    val coverSavedMessage = stringResource(R.string.edit_profile_cover_saved)

    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var placeOfOrigin by rememberSaveable { mutableStateOf("") }
    var currentResidence by rememberSaveable { mutableStateOf("") }
    var birthDate by rememberSaveable { mutableStateOf<String?>(null) }
    var skills by remember { mutableStateOf(emptyList<String>()) }
    var hobbies by remember { mutableStateOf(emptyList<String>()) }
    var showBirthDatePicker by remember { mutableStateOf(false) }
    var avatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var coverUri by rememberSaveable { mutableStateOf<String?>(null) }
    var originalAvatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var originalCoverUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showCoverMenu by remember { mutableStateOf(false) }
    var showCoverPreview by remember { mutableStateOf(false) }
    var coverPreviewPage by remember { mutableStateOf(0) }
    var shareCoverToProfile by rememberSaveable { mutableStateOf(false) }
    var coverCaption by rememberSaveable { mutableStateOf("") }
    var coverSelectedForPost by rememberSaveable { mutableStateOf(false) }
    var prefilled by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        val current = user
        if (!prefilled && current != null) {
            displayName = current.displayName
            username = current.username
            bio = current.bio
            placeOfOrigin = current.placeOfOrigin.orEmpty()
            currentResidence = current.currentResidence.orEmpty()
            birthDate = current.birthDate
            skills = current.skills.orEmpty()
            hobbies = current.hobbies.orEmpty()
            avatarUri = current.avatarUri
            coverUri = current.coverUri
            originalAvatarUri = current.avatarUri
            originalCoverUri = current.coverUri
            prefilled = true
        }
    }

    LaunchedEffect(saved) {
        if (saved) {
            val message = when {
                avatarUri != originalAvatarUri -> photoSavedMessage
                coverUri != originalCoverUri -> coverSavedMessage
                else -> savedMessage
            }
            scope.launch { snackbarHost?.showSnackbar(message) }
            delay(1600)
            viewModel.consumeSaved()
            onBack()
        }
    }

    val errorMessage = error?.let { stringResource(it) }

    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrBlank()) {
            snackbarHost?.showSnackbar(errorMessage)
            viewModel.consumeError()
        }
    }

    val pickAvatar = rememberImagePicker { uri ->
        uri?.let { avatarUri = it.toString() }
    }

    val pickCover = rememberImagePicker { uri ->
        uri?.let {
            coverUri = it.toString()
            coverSelectedForPost = true
            shareCoverToProfile = false
            coverCaption = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(
            title = stringResource(R.string.edit_profile_title),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                NexaCover(
                    seed = (user?.id ?: "cover").hashCode().toLong(),
                    coverUri = coverUri,
                    height = 170.dp
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(NexaSpacing.sm)
                ) {
                    IconButton(
                        onClick = { showCoverMenu = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "خيارات صورة الغلاف",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = showCoverMenu,
                        onDismissRequest = { showCoverMenu = false }
                    ) {
                        if (!coverUri.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("عرض الصورة") },
                                leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                                onClick = {
                                    showCoverMenu = false
                                    coverPreviewPage = 0
                                    showCoverPreview = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("إضافة صورة") },
                            leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null) },
                            onClick = {
                                showCoverMenu = false
                                pickCover()
                            }
                        )
                        if (!coverUri.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("حذف الصورة") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showCoverMenu = false
                                    coverUri = null
                                    coverSelectedForPost = false
                                    shareCoverToProfile = false
                                    coverCaption = ""
                                }
                            )
                        }
                    }
                }
            }

            if (coverSelectedForPost && coverUri != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(NexaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = shareCoverToProfile,
                            onCheckedChange = { shareCoverToProfile = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "مشاركة الصورة في الملف الشخصي",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "عند التفعيل ستُحفظ كغلاف وتُنشر أيضاً كمنشور",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (shareCoverToProfile) {
                        NexaTextField(
                            value = coverCaption,
                            onValueChange = { coverCaption = it },
                            label = "اكتب نصاً مع الصورة (اختياري)",
                            singleLine = false,
                            imeAction = ImeAction.Default
                        )
                    }
                }
            }

            if (showCoverPreview && !coverUri.isNullOrBlank()) {
                val previewUris = listOfNotNull(coverUri, originalCoverUri?.takeIf { it != coverUri })
                val displayedCover = previewUris.getOrNull(coverPreviewPage.coerceIn(0, previewUris.lastIndex))
                    ?: coverUri
                var horizontalDrag by remember(showCoverPreview) { mutableStateOf(0f) }
                Dialog(onDismissRequest = { showCoverPreview = false }) {
                    androidx.compose.material3.Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(NexaSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                        ) {
                            AsyncImage(
                                model = displayedCover,
                                contentDescription = if (coverPreviewPage == 0) "الغلاف الحالي المختار" else "الغلاف السابق",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .pointerInput(previewUris) {
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { _, dragAmount ->
                                                horizontalDrag += dragAmount
                                            },
                                            onDragEnd = {
                                                if (horizontalDrag < -50f && previewUris.size > 1) {
                                                    coverPreviewPage = 1
                                                } else if (horizontalDrag > 50f) {
                                                    coverPreviewPage = 0
                                                }
                                                horizontalDrag = 0f
                                            },
                                            onDragCancel = { horizontalDrag = 0f }
                                        )
                                    },
                                contentScale = ContentScale.Fit
                            )
                            if (previewUris.size > 1) {
                                Text(
                                    text = if (coverPreviewPage == 0) "الغلاف الجديد  •  اسحب لليسار لعرض السابق"
                                    else "الغلاف السابق  •  اسحب لليمين للعودة للجديد",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { showCoverPreview = false },
                                modifier = Modifier.align(Alignment.End)
                            ) { Text("إغلاق") }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaAvatar(
                    displayName = displayName.ifBlank { "N" },
                    avatarUri = avatarUri,
                    size = 72.dp
                )

                Spacer(
                    modifier = Modifier.padding(
                        start = NexaSpacing.md
                    )
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                ) {
                    NexaSecondaryButton(
                        text = stringResource(R.string.edit_profile_pick_image),
                        onClick = pickAvatar
                    )
                    if (avatarUri != null) {
                        NexaSecondaryButton(
                            text = stringResource(R.string.edit_profile_remove_image),
                            onClick = { avatarUri = null }
                        )
                    }
                }
            }

            NexaTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = stringResource(R.string.label_display_name)
            )

            NexaTextField(
                value = bio,
                onValueChange = { bio = it },
                label = stringResource(R.string.edit_profile_bio),
                singleLine = false,
                imeAction = ImeAction.Default
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(NexaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
                ) {
                    Text("معلومات عنك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "أضف تفاصيل تساعد الآخرين على التعرف عليك. يمكنك ترك أي حقل فارغاً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NexaTextField(
                        value = placeOfOrigin,
                        onValueChange = { placeOfOrigin = it },
                        label = "مكان المنشأ"
                    )
                    NexaTextField(
                        value = currentResidence,
                        onValueChange = { currentResidence = it },
                        label = "مكان الإقامة الحالية"
                    )
                    OutlinedButton(
                        onClick = { showBirthDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Cake, contentDescription = null)
                        Spacer(Modifier.width(NexaSpacing.sm))
                        Text(
                            birthDate?.let { runCatching {
                                LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("ar")))
                            }.getOrDefault(it) } ?: "تاريخ الميلاد"
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(NexaSpacing.sm))
                        Column {
                            Text("تاريخ الانضمام", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                user?.createdAt?.takeIf { it > 0L }?.let {
                                    SimpleDateFormat("dd MMMM yyyy", Locale("ar")).format(Date(it))
                                } ?: "سيظهر بعد إنشاء الحساب",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("تلقائي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            ProfileTagEditor(
                title = "المهارات",
                placeholder = "مثلاً: التصميم، البرمجة",
                items = skills,
                onAdd = { value -> skills = (skills + value).distinct() },
                onRemove = { value -> skills = skills - value },
                icon = Icons.Filled.Star
            )
            ProfileTagEditor(
                title = "الهوايات",
                placeholder = "مثلاً: القراءة، التصوير",
                items = hobbies,
                onAdd = { value -> hobbies = (hobbies + value).distinct() },
                onRemove = { value -> hobbies = hobbies - value },
                icon = Icons.Filled.Favorite
            )

            NexaTextField(
                value = username,
                onValueChange = { username = it.removePrefix("@").filter { ch -> ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_' }.take(20) },
                label = stringResource(R.string.label_username),
                singleLine = true,
                imeAction = ImeAction.Next
            )
            Text(
                text = stringResource(R.string.edit_profile_username_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!remoteMode) {
                LocalDataBanner(
                    text = stringResource(R.string.notice_local_account)
                )
            }

            if (saving) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (uploadProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { uploadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(
                                R.string.edit_profile_upload_progress,
                                uploadProgress
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = NexaSpacing.xs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.edit_profile_saving),
                                modifier = Modifier.padding(start = NexaSpacing.sm),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            NexaPrimaryButton(
                text = if (saving) {
                    "جارٍ الحفظ..."
                } else {
                    stringResource(R.string.action_save)
                },
                enabled = displayName.isNotBlank() && !saving && !saved,
                onClick = {
                    viewModel.save(
                        displayName = displayName,
                        username = username,
                        bio = bio,
                        avatarUri = avatarUri,
                        coverUri = coverUri,
                        shareCoverToProfile = shareCoverToProfile,
                        coverCaption = coverCaption,
                        placeOfOrigin = placeOfOrigin,
                        currentResidence = currentResidence,
                        birthDate = birthDate,
                        skills = skills,
                        hobbies = hobbies
                    )
                }
            )

            Spacer(Modifier.height(NexaSpacing.lg))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDatePickerDialog(
    title: String,
    selectedDate: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val initialMillis = remember(selectedDate) {
        selectedDate?.let { runCatching {
            LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull() }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onSelect(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                } ?: onDismiss()
            }) { Text("تأكيد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    ) {
        DatePicker(state = pickerState, title = { Text(title, modifier = Modifier.padding(NexaSpacing.md)) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTagEditor(
    title: String,
    placeholder: String,
    items: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    var input by rememberSaveable(title) { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        )
    ) {
        Column(
            modifier = Modifier.padding(NexaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(NexaSpacing.sm))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(40) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors()
                )
                FilledIconButton(
                    onClick = {
                        val value = input.trim()
                        if (value.isNotBlank() && items.none { it.equals(value, ignoreCase = true) }) onAdd(value)
                        input = ""
                    },
                    enabled = input.isNotBlank()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "إضافة")
                }
            }
            if (items.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(NexaSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(NexaSpacing.xs)
                ) {
                    items.forEach { item ->
                        AssistChip(onClick = { onRemove(item) }, label = { Text("$item  ×") })
                    }
                }
            }
        }
    }
}

