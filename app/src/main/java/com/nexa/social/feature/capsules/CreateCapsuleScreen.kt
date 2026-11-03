package com.nexa.social.feature.capsules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.common.TimeUtils
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaChoiceChip
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing
import java.util.concurrent.TimeUnit

@Composable
fun CreateCapsuleScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateCapsuleViewModel = viewModel(factory = CreateCapsuleViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableIntStateOf(30) }
    val error = failureMessage(state.failure)
    val unlockAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days.toLong())

    LaunchedEffect(state.created) {
        if (state.created) onCreated()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.capsule_create_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            NexaTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.capsule_field_title)
            )
            NexaTextField(
                value = message,
                onValueChange = { message = it },
                label = stringResource(R.string.capsule_field_message),
                singleLine = false,
                imeAction = ImeAction.Default
            )
            Text(
                text = stringResource(R.string.capsule_unlock_after),
                style = MaterialTheme.typography.titleSmall
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                items(viewModel.dayChoices) { choice ->
                    NexaChoiceChip(
                        label = if (choice >= 365) {
                            stringResource(R.string.capsule_one_year)
                        } else {
                            stringResource(R.string.capsule_days, choice)
                        },
                        selected = days == choice,
                        onClick = { days = choice }
                    )
                }
            }
            Row {
                Text(
                    text = stringResource(R.string.capsule_unlock_on, TimeUtils.date(unlockAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (error != null) {
                AccountRequiredBanner(text = error)
            }
            LocalDataBanner(text = stringResource(R.string.capsules_encrypted_note))
            NexaPrimaryButton(
                text = stringResource(R.string.capsule_create_action),
                loading = state.saving,
                onClick = { viewModel.create(title, message, days) }
            )
            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }
}
