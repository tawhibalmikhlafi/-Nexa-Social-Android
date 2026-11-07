package com.nexa.social.feature.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.core.ui.failureMessageRes
import com.nexa.social.ui.components.NexaCover
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaSecondaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.rememberImagePicker
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.launch

@Composable
fun CreateStoryScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: StoriesViewModel = viewModel(factory = StoriesViewModel.Factory)
) {
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    val publishedMessage = stringResource(R.string.story_created)

    val picker = rememberImagePicker { uri -> imageUri = uri?.toString() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(NexaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
    ) {
        NexaTopBar(title = stringResource(R.string.story_create_title), onBack = onBack)

        if (imageUri != null) {
            NexaCover(seed = imageUri!!.hashCode().toLong(), coverUri = imageUri, height = 320.dp)
            NexaSecondaryButton(
                text = stringResource(R.string.story_remove_image),
                onClick = { imageUri = null }
            )
        } else {
            NexaSecondaryButton(
                text = stringResource(R.string.story_add_image),
                onClick = picker,
                icon = Icons.Filled.Image
            )
        }

        NexaTextField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(R.string.story_caption_hint),
            singleLine = false,
            imeAction = ImeAction.Default
        )

        Spacer(Modifier.height(NexaSpacing.sm))
        NexaPrimaryButton(
            text = stringResource(R.string.story_publish),
            enabled = !imageUri.isNullOrBlank() || text.isNotBlank(),
            onClick = {
                viewModel.createStory(imageUri, text) { reason ->
                    if (reason == null) {
                        snackbarHost?.let { host -> scope.launch { host.showSnackbar(publishedMessage) } }
                        onCreated()
                    } else {
                        val message = context.getString(failureMessageRes(reason))
                        snackbarHost?.let { host -> scope.launch { host.showSnackbar(message) } }
                    }
                }
            }
        )
        Text(
            text = stringResource(R.string.story_expiry_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
