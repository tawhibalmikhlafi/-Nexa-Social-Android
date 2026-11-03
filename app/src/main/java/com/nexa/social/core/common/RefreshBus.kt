package com.nexa.social.core.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * App-wide signal used by pull-to-refresh. Remote data sources subscribe to it so a single
 * gesture can refresh every live feed without each screen knowing which poller to poke.
 */
object RefreshBus {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    fun requestRefresh() {
        _requests.tryEmit(Unit)
    }
}
