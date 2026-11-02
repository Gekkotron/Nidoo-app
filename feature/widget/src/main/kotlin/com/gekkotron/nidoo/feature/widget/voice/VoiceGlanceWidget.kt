package com.gekkotron.nidoo.feature.widget.voice

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.gekkotron.nidoo.feature.widget.R
import com.gekkotron.nidoo.feature.widget.WidgetComponentProvider
import com.gekkotron.nidoo.feature.widget.ui.AppLinks
import com.gekkotron.nidoo.feature.widget.ui.voiceIntent
import kotlinx.coroutines.flow.first

private val mic = ColorProvider(day = Color(0xFF1F1B16), night = Color(0xFFE8ECFF))
private val dim = ColorProvider(day = Color(0xFFA09A90), night = Color(0xFF6F7694))

/**
 * "Nidoo voice": a single large mic that opens the voice sheet. Android cannot refuse the widget while voice is off, so it
 * then shows a greyed "Voice off" state whose tap opens Settings, where the toggle lives.
 */
class VoiceGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val enabled = (context.applicationContext as WidgetComponentProvider).widgetComponent().voiceEnabled
        // Read once before composing, so the first frame is the real state and not a flash of the other one.
        val initial = enabled.first()
        val openVoice = actionStartActivity(voiceIntent(context))
        val openSettings = actionStartActivity(
            Intent(Intent.ACTION_VIEW, AppLinks.SETTINGS.toUri()).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        provideContent {
            val on by enabled.collectAsState(initial = initial)
            VoiceWidgetContent(on, openVoice, openSettings)
        }
    }
}

@Composable
internal fun VoiceWidgetContent(enabled: Boolean, openVoice: Action, openSettings: Action) {
    val context = LocalContext.current
    Column(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).clickable(if (enabled) openVoice else openSettings),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            ImageProvider(R.drawable.ic_widget_mic),
            contentDescription = context.getString(if (enabled) R.string.widget_voice else R.string.widget_voice_off_description),
            colorFilter = ColorFilter.tint(if (enabled) mic else dim),
            modifier = GlanceModifier.size(32.dp),
        )
        if (!enabled) {
            Text(context.getString(R.string.widget_voice_off), maxLines = 1, style = TextStyle(color = dim, fontSize = 10.sp))
        }
    }
}

suspend fun updateAllVoiceWidgets(context: Context) = VoiceGlanceWidget().updateAll(context)
