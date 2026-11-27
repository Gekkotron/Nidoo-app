package com.gekkotron.nidoo.feature.automations

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.feature.automations.resources.Res
import com.gekkotron.nidoo.feature.automations.resources.wake_up_hub_outdated
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "fr")
class StringsTest {
    @Test
    fun apostrophesAreNotEscaped() = runBlocking {
        assertEquals("Mettez à jour votre hub Nidoo pour modifier les réveils depuis l'app", getString(Res.string.wake_up_hub_outdated))
    }
}
