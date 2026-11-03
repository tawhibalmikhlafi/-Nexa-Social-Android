package com.nexa.social.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.core.ui.failureMessageRes
import com.nexa.social.domain.model.SpaceCategory
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.NexaChoiceChipRow
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.launch

@Composable
fun CreateSpaceScreen(
    communityId: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateSpaceViewModel = viewModel(factory = CreateSpaceViewModel.factory(communityId))
) {
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SpaceCategory.DISCUSSION) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NexaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
    ) {
        NexaTopBar(title = stringResource(R.string.space_create_title), onBack = onBack)
        AccountRequiredBanner(text = stringResource(R.string.space_requires_account))

        NexaTextField(value = title, onValueChange = { title = it }, label = stringResource(R.string.space_title_label))
        NexaTextField(
            value = description,
            onValueChange = { description = it },
            label = stringResource(R.string.space_description_label),
            singleLine = false
        )
        Text(text = stringResource(R.string.space_category_label), style = MaterialTheme.typography.titleMedium)
        NexaChoiceChipRow(
            options = SpaceCategory.entries,
            selected = category,
            labelOf = { spaceCategoryLabel(it) },
            onSelect = { category = it }
        )

        Spacer(Modifier.height(NexaSpacing.sm))
        NexaPrimaryButton(
            text = stringResource(R.string.action_save),
            enabled = title.trim().length >= 3,
            onClick = {
                viewModel.create(title, description, category) { reason, id ->
                    if (reason == null && id != null) {
                        onCreated(id)
                    } else if (reason != null) {
                        val message = context.getString(failureMessageRes(reason))
                        snackbarHost?.let { host -> scope.launch { host.showSnackbar(message) } }
                    }
                }
            }
        )
    }
}
