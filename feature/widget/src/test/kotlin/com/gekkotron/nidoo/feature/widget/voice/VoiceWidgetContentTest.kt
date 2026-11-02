package com.gekkotron.nidoo.feature.widget.voice

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceWidgetContentTest {
    private val voice: Action = actionStartActivity(ComponentName("test", "test.Voice"))
    private val settings: Action = actionStartActivity(ComponentName("test", "test.Settings"))
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun enabled_shows_the_mic_only() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(60.dp, 60.dp))
        provideComposable { VoiceWidgetContent(enabled = true, openVoice = voice, openSettings = settings) }
        onNode(hasContentDescription("Say a command")).assertExists()
        onNode(hasText("Voice off")).assertDoesNotExist()
    }

    @Test
    fun disabled_says_voice_is_off() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(60.dp, 60.dp))
        provideComposable { VoiceWidgetContent(enabled = false, openVoice = voice, openSettings = settings) }
        onNode(hasText("Voice off")).assertExists()
        onNode(hasContentDescription("Voice assistant off. Open Settings to turn it on.")).assertExists()
    }
}
