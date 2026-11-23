package com.gekkotron.nidoo.shared

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertNotNull

/** iOS's root owner never enables saved-state handles, so a screen's owner must work outside any Activity. */
@RunWith(AndroidJUnit4::class)
class ScreenOwnerTest {
    @Test
    fun aSavedStateHandleComesFromTheOwnerAlone() {
        val owner = ScreenOwner()
        assertNotNull(MutableCreationExtras(owner.defaultViewModelCreationExtras).apply { this[ViewModelProvider.VIEW_MODEL_KEY] = "test" }.createSavedStateHandle())
        owner.destroy()
    }
}
