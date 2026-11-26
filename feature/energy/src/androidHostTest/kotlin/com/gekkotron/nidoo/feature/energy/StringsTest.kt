package com.gekkotron.nidoo.feature.energy

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gekkotron.nidoo.feature.energy.resources.Res
import com.gekkotron.nidoo.feature.energy.resources.energy_save_failed
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class StringsTest {
    @Test
    fun apostrophesAreNotEscaped() = runBlocking {
        assertEquals("Couldn't save your changes. Check the connection and try again.", getString(Res.string.energy_save_failed))
    }
}
