package com.nexa.social

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nexa.social.core.common.AppContainer
import com.nexa.social.core.common.DefaultAppContainer
import com.nexa.social.core.diagnostics.CrashReporter
import com.nexa.social.core.notifications.NotificationChannels
import com.nexa.social.core.ui.buildImageLoader

class NexaApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        CrashReporter.install(this)
        super.onCreate()
        container = DefaultAppContainer(this)
        NotificationChannels.create(this)
        container.presenceTracker.register()
        container.syncCoordinator.start()
    }

    override fun newImageLoader(): ImageLoader = buildImageLoader(this)
}
