package com.nexa.social.core.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.nexa.social.domain.repository.PresenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Starts the heartbeat when the app enters the foreground and stops it in the background.
 * A final ping is sent on the way out so the user does not appear online forever.
 */
class PresenceTracker(
    private val application: Application,
    private val presenceRepository: PresenceRepository,
) : Application.ActivityLifecycleCallbacks {

    private val startedCount = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun register() {
        application.registerActivityLifecycleCallbacks(this)
        if (startedCount.get() > 0) presenceRepository.startHeartbeat()
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedCount.incrementAndGet() == 1) {
            presenceRepository.startHeartbeat()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (startedCount.decrementAndGet() == 0) {
            presenceRepository.stopHeartbeat()
            scope.launch { runCatching { presenceRepository.pingNow() } }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
