package com.gekkotron.nidoo.feature.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.feature.security.resources.Res
import com.gekkotron.nidoo.feature.security.resources.security_count_opening
import com.gekkotron.nidoo.feature.security.resources.security_no_panel
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
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
        assertEquals("Aucune centrale d'alarme. Publiez un alarm_control_panel MQTT pour la voir ici.", getString(Res.string.security_no_panel))
    }

    @Test
    fun countsUsePlurals() = runBlocking {
        assertEquals("1 ouvert", getPluralString(Res.plurals.security_count_opening, 1, 1))
        assertEquals("2 ouverts", getPluralString(Res.plurals.security_count_opening, 2, 2))
    }
}
