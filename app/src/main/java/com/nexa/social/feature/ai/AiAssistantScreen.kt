package com.nexa.social.feature.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.R
import com.nexa.social.core.ui.failureMessage
import com.nexa.social.domain.model.AiTask
import com.nexa.social.ui.components.NexaCard
import com.nexa.social.ui.components.NexaChoiceChipRow
import com.nexa.social.ui.components.NexaPrimaryButton
import com.nexa.social.ui.components.NexaTextField
import com.nexa.social.ui.components.NexaTopBar
import com.nexa.social.ui.components.ServerPendingBanner
import com.nexa.social.ui.theme.NexaSpacing

@Composable
fun AiAssistantScreen(
    onBack: () -> Unit,
    viewModel: AiViewModel = viewModel(factory = AiViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var task by remember { mutableStateOf(AiTask.PROOFREAD) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NexaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NexaSpacing.md)
    ) {
        NexaTopBar(title = stringResource(R.string.ai_title), onBack = onBack)
        ServerPendingBanner(text = stringResource(R.string.ai_not_configured))

        Text(text = stringResource(R.string.ai_task_label), style = MaterialTheme.typography.titleMedium)
        NexaChoiceChipRow(
            options = AiTask.entries,
            selected = task,
            labelOf = { aiTaskLabel(it) },
            onSelect = { task = it }
        )

        NexaTextField(
            value = input,
            onValueChange = { input = it },
            label = stringResource(R.string.ai_input_label),
            singleLine = false,
            imeAction = ImeAction.Default
        )

        NexaPrimaryButton(
            text = stringResource(R.string.ai_run),
            enabled = input.isNotBlank(),
            onClick = { viewModel.run(task, input) }
        )

        when (val current = state) {
            AiUiState.Idle -> Unit
            AiUiState.Loading -> Text(text = stringResource(R.string.state_loading))
            is AiUiState.Error -> {
                val message = failureMessage(current.reason)
                Text(
                    text = message.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is AiUiState.Success -> NexaCard {
                Text(text = current.result.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(NexaSpacing.sm))
                Text(
                    text = if (current.result.isLocalTool) {
                        stringResource(R.string.ai_local_tool_label)
                    } else {
                        current.result.providerLabel
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(NexaSpacing.xxxl))
    }
}

@Composable
private fun aiTaskLabel(task: AiTask): String = when (task) {
    AiTask.IMPROVE_WRITING -> stringResource(R.string.ai_task_improve)
    AiTask.SUGGEST_TITLE -> stringResource(R.string.ai_task_title)
    AiTask.PROOFREAD -> stringResource(R.string.ai_task_proofread)
    AiTask.SUMMARIZE -> stringResource(R.string.ai_task_summarize)
    AiTask.TRANSLATE -> stringResource(R.string.ai_task_translate)
    AiTask.SUGGEST_TAGS -> stringResource(R.string.ai_task_tags)
    AiTask.REWRITE_STYLE -> stringResource(R.string.ai_task_rewrite)
}
