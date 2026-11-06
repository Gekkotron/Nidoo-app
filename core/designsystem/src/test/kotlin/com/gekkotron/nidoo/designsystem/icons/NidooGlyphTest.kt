package com.gekkotron.nidoo.designsystem.icons

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NidooGlyphTest {
    @Test
    fun `glyphs parse to non empty paths`() {
        for (glyph in listOf(NidooGlyph.SUN, NidooGlyph.HOUSE, NidooGlyph.PYLON, NidooGlyph.BATTERY, NidooGlyph.SEARCH, NidooGlyph.CLOSE, NidooGlyph.GEAR, NidooGlyph.DRAG_HANDLE, NidooGlyph.STAR, NidooGlyph.BELL, NidooGlyph.PIN, NidooGlyph.SUN_MOON, NidooGlyph.BROKER, NidooGlyph.INFO, NidooGlyph.MUSIC, NidooGlyph.PAUSE, NidooGlyph.NEXT, NidooGlyph.PREVIOUS)) {
            assertTrue(glyph.paths.isNotEmpty(), "$glyph has no parts")
            assertTrue(glyph.paths.all { (path, _) -> !path.isEmpty }, "$glyph has an empty path")
        }
    }
}
