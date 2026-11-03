package com.nexa.social.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nexa.social.core.common.RefreshBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Small handle so screens can observe the indicator and trigger the gesture. */
@Stable
class NexaRefreshHandle internal constructor(
    val isRefreshing: Boolean,
    val refresh: () -> Unit
)

/**
 * Wraps any scrollable content with the platform pull-to-refresh gesture.
 * [onRefresh] runs inside a coroutine and the indicator stays visible for a short
 * minimum period so quick repository calls still give visual feedback.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NexaPullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullRefreshState(refreshing, onRefresh)
    Box(modifier = modifier.pullRefresh(state)) {
        content()
        PullRefreshIndicator(
            refreshing = refreshing,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

/** Creates the refreshing state used by [NexaPullToRefresh]. */
@Composable
fun rememberNexaRefresh(onRefresh: suspend () -> Unit = { RefreshBus.requestRefresh() }): NexaRefreshHandle {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentRefresh by rememberUpdatedState(onRefresh)
    val trigger: () -> Unit = {
        if (!refreshing) {
            scope.launch {
                refreshing = true
                try {
                    currentRefresh()
                    delay(650)
                } finally {
                    refreshing = false
                }
            }
        }
    }
    return remember(refreshing, trigger) {
        NexaRefreshHandle(isRefreshing = refreshing, refresh = trigger)
    }
}
