@file:OptIn(ExperimentalAtomicApi::class)

package com.gekkotron.nidoo.transport.testing

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A thread-safe list for test fakes that transports write from their own coroutines while the test reads:
 * the multiplatform stand-in for java.util.concurrent.CopyOnWriteArrayList. Each write swaps in a new copy.
 */
internal class CopyOnWriteList<T> : AbstractMutableList<T>() {
    private val items = AtomicReference<List<T>>(emptyList())

    override val size: Int get() = items.load().size

    override fun get(index: Int): T = items.load()[index]

    override fun add(index: Int, element: T) = update { it.add(index, element) }

    override fun removeAt(index: Int): T {
        var removed: T? = null
        update { removed = it.removeAt(index) }
        @Suppress("UNCHECKED_CAST")
        return removed as T
    }

    override fun set(index: Int, element: T): T {
        var previous: T? = null
        update { previous = it.set(index, element) }
        @Suppress("UNCHECKED_CAST")
        return previous as T
    }

    /** Iterates over a snapshot, like CopyOnWriteArrayList. */
    override fun iterator(): MutableIterator<T> = items.load().toMutableList().iterator()

    private inline fun update(change: (MutableList<T>) -> Unit) {
        while (true) {
            val current = items.load()
            val next = current.toMutableList().also(change)
            if (items.compareAndSet(current, next)) return
        }
    }
}
