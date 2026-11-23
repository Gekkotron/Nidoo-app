package com.gekkotron.nidoo.feature.settings

import com.gekkotron.nidoo.feature.settings.resources.Res
import com.gekkotron.nidoo.feature.settings.resources.rooms_save_failed
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class StringsTest {
    @Test
    fun apostrophesAreNotEscaped() = runBlocking {
        assertEquals("Couldn't save the order. Check the connection.", getString(Res.string.rooms_save_failed))
    }
}
