package com.gekkotron.nidoo.feature.home.di

import com.gekkotron.nidoo.feature.home.presentation.EntityLabels
import com.gekkotron.nidoo.feature.home.presentation.LabelText
import com.gekkotron.nidoo.format.FormatLocale
import com.gekkotron.nidoo.format.systemLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** Every known entity label (normalised) with its translation in the current language. */
suspend fun loadEntityLabels(): Map<String, String> =
    EntityLabels.resources.mapValues { (_, resource) -> getString(resource) }

/**
 * Entity-label translations for the ViewModels. CMP strings are suspend, but [invoke] is not, so the map is loaded
 * ahead and looked up synchronously. A label is unknown (null, shown as published) until the first load lands, and
 * [updates] then tells the ViewModels to rebuild. When the language changes, [invoke] starts one reload for the new
 * one and keeps answering from the old map until it lands.
 */
class PreloadedLabelText(
    private val scope: CoroutineScope,
    private val locale: () -> FormatLocale = ::systemLocale,
    private val load: suspend () -> Map<String, String> = ::loadEntityLabels,
) : LabelText {
    private class Loaded(val locale: FormatLocale, val labels: Map<String, String>)

    private val loaded = MutableStateFlow<Loaded?>(null)

    /** The locale being loaded right now; null when no load is running. */
    private val loading = MutableStateFlow<FormatLocale?>(null)

    init {
        reloadIfStale()
    }

    override fun invoke(label: String): String? {
        reloadIfStale()
        return loaded.value?.labels?.get(EntityLabels.normalize(label))
    }

    /** Cancels the load in flight; called when the Koin application closes. */
    fun close() = scope.cancel()

    /** Emits once at first, then after every completed load. */
    override val updates: Flow<Unit> = loaded.map { }

    private fun reloadIfStale() {
        val wanted = locale()
        if (loaded.value?.locale == wanted) return
        if (!loading.compareAndSet(null, wanted)) return
        scope.launch {
            val labels = try {
                load()
            } catch (e: CancellationException) {
                loading.value = null
                throw e
            } catch (e: Exception) {
                // Keep what we had; the screen shows the labels as published rather than retrying on every lookup.
                loaded.value?.labels.orEmpty()
            }
            // Free the slot first: a rebuild triggered by the update may already want the next locale.
            loading.value = null
            loaded.value = Loaded(wanted, labels)
        }
    }
}
