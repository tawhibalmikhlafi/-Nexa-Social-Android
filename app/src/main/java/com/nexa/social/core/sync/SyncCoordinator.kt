package com.nexa.social.core.sync

import com.nexa.social.core.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Any repository that wants to resync when the network comes back implements this. */
interface Syncable {
    suspend fun onNetworkRestored()
}

/**
 * Runs only on the offline → online transition.
 * Runs once on startup when already online, then again on each offline → online transition.
 */
class SyncCoordinator(
    private val networkMonitor: NetworkMonitor,
    private val targets: List<Syncable>,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            networkMonitor.isOnline
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
