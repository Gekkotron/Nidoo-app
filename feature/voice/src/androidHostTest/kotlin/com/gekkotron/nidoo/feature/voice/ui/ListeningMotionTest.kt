package com.gekkotron.nidoo.feature.voice.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The listening mic pulses, unless the system's animations are off (Settings › Accessibility › Remove animations). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ListeningMotionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun animatorScale(scale: Float) =
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)

    /** The view drawn into a bitmap: the clock stays frozen, so no waiting on an animation to settle. */
    private fun frame(): IntArray {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    }

    /** The listening view drawn now, and again 400 ms later (half a pulse). */
    private fun twoFrames(): Pair<IntArray, IntArray> {
        compose.mainClock.autoAdvance = false
        compose.setContent { NidooTheme(ThemeChoice.NIGHT_GLASS) { Column { ListeningView("") } } }
        compose.mainClock.advanceTimeBy(100)
        val first = frame()
        compose.mainClock.advanceTimeBy(400)
        return first to frame()
    }

    @Test
    fun `the mic pulses while listening`() {
        animatorScale(1f)
        val (a, b) = twoFrames()
        assertFalse(a.contentEquals(b), "the mic did not pulse: this test could not see motion")
    }

    @Test
    fun `with animations off the mic is drawn still`() {
        animatorScale(0f)
        val (a, b) = twoFrames()
        assertTrue(a.contentEquals(b), "the mic pulsed with animations off")
    }
}
