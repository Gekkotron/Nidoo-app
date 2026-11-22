package com.gekkotron.nidoo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.gekkotron.nidoo.di.KoinBridgeEntryPoint
import com.gekkotron.nidoo.di.startNidooKoin
import dagger.hilt.android.EntryPointAccessors
import org.junit.rules.ExternalResource
import org.koin.core.context.stopKoin

/**
 * HiltTestApplication never runs NidooApplication.onCreate, so nothing starts Koin with this test's Hilt graph. Place it
 * after HiltAndroidRule and before the activity rule: the Koin-built screens (onboarding) must share Hilt's singletons.
 */
class KoinFromHiltRule : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        startNidooKoin(app, EntryPointAccessors.fromApplication(app, KoinBridgeEntryPoint::class.java))
    }

    override fun after() {
        stopKoin()
    }
}
