package com.nexa.social.feature.gatherings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.ui.components.AccountRequiredBanner
import com.nexa.social.ui.components.NexaChoiceChip
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing
import java.util.Calendar
import java.util.Locale

@Composable
fun CreateGatheringScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateGatheringViewModel = viewModel(factory = CreateGatheringViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var virtual by rememberSaveable { mutableStateOf(false) }
    var capacityText by rememberSaveable { mutableStateOf("") }
    var tagsText by rememberSaveable { mutableStateOf("") }
    var dayOffset by rememberSaveable { mutableIntStateOf(3) }
    var hour by rememberSaveable { mutableIntStateOf(20) }
    val dayChoices = listOf(1, 3, 7, 14, 30)
    val hourChoices = listOf(17, 18, 19, 20, 21, 22)
    val error = failureMessage(state.failure)

    val startsAt = java.util.Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    LaunchedEffect(state.createdId) {
        state.createdId?.let(onCreated)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.create_gathering_title), onBack = onBack)
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
                label = stringResource(R.string.gathering_field_title)
            )
            NexaTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.gathering_field_description),
                singleLine = false,
                imeAction = ImeAction.Default
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.gathering_virtual_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.gathering_virtual_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = virtual, onCheckedChange = { virtual = it })
            }
            NexaTextField(
                value = location,
                onValueChange = { location = it },
                label = if (virtual) {
                    stringResource(R.string.gathering_field_virtual_location)
                } else {
                    stringResource(R.string.gathering_field_location)
                }
            )
            NexaTextField(
                value = capacityText,
                onValueChange = { capacityText = it.filter(Char::isDigit) },
                label = stringResource(R.string.gathering_field_capacity),
                keyboardType = KeyboardType.Number
            )
            NexaTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = stringResource(R.string.gathering_field_tags)
            )

            Text(
                text = stringResource(R.string.gathering_when),
                style = MaterialTheme.typography.titleSmall
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                items(dayChoices) { days ->
                    NexaChoiceChip(
                        label = stringResource(R.string.gathering_days, days),
                        selected = dayOffset == days,
                        onClick = { dayOffset = days }
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                items(hourChoices) { choice ->
                    NexaChoiceChip(
                        label = String.format(Locale.getDefault(), "%02d:00", choice),
                        selected = hour == choice,
                        onClick = { hour = choice }
                    )
                }
            }
            Text(
                text = com.nexa.social.core.common.TimeUtils.dateTime(startsAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (error != null) {
                AccountRequiredBanner(text = error)
            }

            NexaPrimaryButton(
                text = stringResource(R.string.gathering_create_action),
                loading = state.saving,
                onClick = {
                    viewModel.create(
                        title = title,
                        description = description,
                        location = location,
                        isVirtual = virtual,
                        startsAt = startsAt,
                        durationMinutes = 90,
                        capacity = capacityText.toIntOrNull() ?: 0,
                        tags = tagsText.split(',', '#')
                    )
                }
            )
            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }
}
