package com.gekkotron.nidoo.shared

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * iOS's root owner never enables saved-state handles; the per-entry owner of `rememberViewModelStoreNavEntryDecorator`
 * must, or Home's and Rooms' ViewModels (which take a `SavedStateHandle`) would not resolve.
 */
@OptIn(ExperimentalTestApi::class)
class EntryViewModelsTest {
    private data object First : NavKey
    private data object Second : NavKey

    private class EntryVm(val handle: SavedStateHandle) : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private val created = mutableListOf<EntryVm>()
    private val koinApp = koinApplication {
        modules(module { viewModel { EntryVm(get()).also { created += it } } })
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(stack: MutableList<NavKey>) {
        setContent {
            KoinIsolatedContext(koinApp) {
                NavDisplay(
                    backStack = stack,
                    onBack = { stack.removeLastOrNull() },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                    entryProvider = entryProvider {
                        entry<First> { koinViewModel<EntryVm>(); Text("first") }
                        entry<Second> { koinViewModel<EntryVm>(); Text("second") }
                    },
                )
            }
        }
    }

    @Test
    fun entryViewModelGetsASavedStateHandle() = runComposeUiTest {
        try {
            show(mutableStateListOf(First))
            waitForIdle()
            val vm = created.single()
            vm.handle["k"] = "v"
            assertEquals("v", vm.handle.get<String>("k"))
        } finally {
            koinApp.close()
        }
    }

    @Test
    fun eachEntryHasItsOwnViewModel() = runComposeUiTest {
        try {
            val stack = mutableStateListOf<NavKey>(First)
            show(stack)
            waitForIdle()
            stack.add(Second)
            waitUntil(timeoutMillis = 5_000) { created.size == 2 }
            assertNotSame(created[0], created[1])
        } finally {
            koinApp.close()
        }
    }

    @Test
    fun aPoppedEntryClearsItsViewModel() = runComposeUiTest {
        try {
            // NavDisplay composes only the top entry, so the second ViewModel exists once Second is pushed.
            val stack = mutableStateListOf<NavKey>(First)
            show(stack)
            waitForIdle()
            stack.add(Second)
            waitUntil(timeoutMillis = 5_000) { created.size == 2 }
            stack.removeLast()
            waitUntil(timeoutMillis = 5_000) { created[1].cleared }
            // First's ViewModel survived and was reused, not recreated.
            assertEquals(2, created.size)
            assertTrue(!created[0].cleared)
        } finally {
            koinApp.close()
        }
    }
}
