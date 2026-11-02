package com.gekkotron.nidoo

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.gekkotron.nidoo.connection.HistoryWiring
import com.gekkotron.nidoo.connection.PushWiring
import com.gekkotron.nidoo.connection.WidgetMirror
import com.gekkotron.nidoo.shared.NidooShared
import com.gekkotron.nidoo.feature.widget.WidgetComponent
import com.gekkotron.nidoo.feature.widget.WidgetComponentProvider
import com.gekkotron.nidoo.feature.widget.work.WidgetWorkerFactory
import com.gekkotron.nidoo.presence.PresenceWorkerFactory
import com.gekkotron.nidoo.push.AlertChannels
import com.gekkotron.nidoo.push.PushComponent
import com.gekkotron.nidoo.push.PushComponentProvider
import com.gekkotron.nidoo.push.PushWorkerFactory
import com.gekkotron.nidoo.transport.ActiveTransport
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NidooApplication : Application(), PushComponentProvider, WidgetComponentProvider, Configuration.Provider, SingletonImageLoader.Factory {
    @Inject
    lateinit var transport: ActiveTransport

    @Inject
    lateinit var component: PushComponent

    @Inject
    lateinit var channels: AlertChannels

    @Inject
    lateinit var workerFactory: PushWorkerFactory

    @Inject
    lateinit var presenceWorkerFactory: PresenceWorkerFactory

    @Inject
    lateinit var presenceGeofenceManager: com.gekkotron.nidoo.presence.PresenceGeofenceManager

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    lateinit var pushWiring: PushWiring

    @Inject
    lateinit var historyWiring: HistoryWiring

    @Inject
    lateinit var widgets: WidgetComponent

    @Inject
    lateinit var widgetMirror: WidgetMirror

    override fun pushComponent(): PushComponent = component

    override fun widgetComponent(): WidgetComponent = widgets

    /** On-demand WorkManager with the push and widget worker factories; the default initializer is removed from the manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(
            DelegatingWorkerFactory().apply {
                addFactory(workerFactory)
                addFactory(presenceWorkerFactory)
                addFactory(WidgetWorkerFactory { widgets.refresher })
            },
        ).build()

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override fun onCreate() {
        super.onCreate()
        Log.i("NidooApplication", "KMP shared ${NidooShared.VERSION}")
        channels.ensure()
        pushWiring.start()
        historyWiring.start()
        widgetMirror.start()
        presenceGeofenceManager.start()
        // Connected only in the foreground; ActiveTransport closes 30 s after onStop (spec §3.3).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = transport.setForeground(true)
            override fun onStop(owner: LifecycleOwner) = transport.setForeground(false)
        })
    }
}
