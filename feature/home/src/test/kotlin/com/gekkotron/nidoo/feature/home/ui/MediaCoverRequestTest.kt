package com.gekkotron.nidoo.feature.home.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.request.CachePolicy
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** The app's one disk cache holds alert pictures: covers change with every song and must not evict them. */
@RunWith(AndroidJUnit4::class)
class MediaCoverRequestTest {
    @Test
    fun `a cover skips the disk cache and keeps the memory cache`() {
        val request = coverRequest(ApplicationProvider.getApplicationContext(), "http://music.local/cover.jpg")
        assertEquals("http://music.local/cover.jpg", request.data)
        assertEquals(CachePolicy.DISABLED, request.diskCachePolicy)
        assertEquals(CachePolicy.ENABLED, request.memoryCachePolicy)
    }
}
