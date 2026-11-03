package com.nexa.social.feature.wellbeing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.LocalSnackbarHostState
import com.nexa.social.domain.model.WellbeingPlan
import com.nexa.social.ui.components.LocalDataBanner
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaChoiceChip
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.theme.NexaSpacing
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WellbeingScreen(
    onBack: () -> Unit,
    viewModel: WellbeingViewModel = viewModel(factory = WellbeingViewModel.Factory)
) {
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var intention by rememberSaveable { mutableStateOf("") }
    val savedMessage = stringResource(R.string.wellbeing_saved)

    LaunchedEffect(plan.dailyIntention) {
        if (intention.isBlank()) intention = plan.dailyIntention
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = stringResource(R.string.wellbeing_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NexaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.wellbeing_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            NexaCard {
                Text(
                    text = stringResource(R.string.wellbeing_intention_label),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(NexaSpacing.sm))
                NexaTextField(
                    value = intention,
                    onValueChange = { intention = it },
                    label = stringResource(R.string.wellbeing_intention_label),
                    placeholder = stringResource(R.string.wellbeing_intention_hint),
                    imeAction = ImeAction.Done
                )
                Spacer(Modifier.height(NexaSpacing.sm))
                NexaPrimaryButton(
                    text = stringResource(R.string.wellbeing_save),
                    onClick = {
                        viewModel.saveIntention(intention)
                        scope.launch { snackbarHost?.showSnackbar(savedMessage) }
                    }
                )
            }

            WellbeingToggle(
                icon = Icons.Filled.SelfImprovement,
                title = stringResource(R.string.wellbeing_focus),
                description = stringResource(R.string.wellbeing_focus_desc),
                checked = plan.focusMode,
                onCheckedChange = viewModel::setFocusMode
            )
            WellbeingToggle(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.wellbeing_quiet),
                description = stringResource(
                    R.string.wellbeing_quiet_desc,
                    clock(plan.quietStartHour),
                    clock(plan.quietEndHour)
                ),
                checked = plan.quietHoursEnabled,
                onCheckedChange = viewModel::setQuietHours
            )
            WellbeingToggle(
                icon = Icons.Filled.NotificationsActive,
                title = stringResource(R.string.wellbeing_reminders),
                description = stringResource(R.string.wellbeing_reminders_desc),
                checked = plan.mindfulReminders,
                onCheckedChange = viewModel::setMindfulReminders
            )
            WellbeingToggle(
                icon = Icons.Filled.AutoAwesome,
                title = stringResource(R.string.wellbeing_reflection),
                description = stringResource(R.string.wellbeing_reflection_desc),
                checked = plan.weeklyReflection,
                onCheckedChange = viewModel::setWeeklyReflection
            )

            Text(
                text = stringResource(R.string.wellbeing_goal),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.wellbeing_goal_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(NexaSpacing.sm)) {
                items(WellbeingPlan.goalChoices) { minutes ->
                    NexaChoiceChip(
                        label = stringResource(R.string.wellbeing_minutes, minutes),
                        selected = plan.dailyGoalMinutes == minutes,
                        onClick = { viewModel.setGoal(minutes) }
                    )
                }
            }

            if (plan.focusMode) {
                LocalDataBanner(text = stringResource(R.string.wellbeing_focus_active))
            }
            LocalDataBanner(text = stringResource(R.string.wellbeing_local_note))
            Spacer(Modifier.height(NexaSpacing.xxxl))
        }
    }
}

@Composable
private fun WellbeingToggle(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    NexaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(NexaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun clock(hour: Int): String =
    String.format(Locale.getDefault(), "%02d:00", hour)
