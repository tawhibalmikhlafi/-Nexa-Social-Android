package com.nexa.social.core.sync

import com.nexa.social.core.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Any repository that wants to resync when the network comes back implements this. */
interface Syncable {
    suspend fun onNetworkRestored()
}

/**
 * Runs only on the offline → online transition.
 * drop(1) skips the initial StateFlow value, so app start never triggers a sync.
 */
class SyncCoordinator(
    private val networkMonitor: NetworkMonitor,
    private val targets: List<Syncable>,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            networkMonitor.isOnline
                .drop(1)
                .collect { online ->
                    if (online) {
                        targets.forEach { target ->
                            runCatching { target.onNetworkRestored() }
                        }
                    }
                }
        }
    }
}
