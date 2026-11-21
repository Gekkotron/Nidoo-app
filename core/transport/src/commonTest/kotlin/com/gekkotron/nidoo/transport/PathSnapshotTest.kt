package com.gekkotron.nidoo.transport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathSnapshotTest {
    private val wifi = PathSnapshot(satisfied = true, interfaces = setOf("wifi"))

    @Test
    fun theFirstSnapshotIsAChange() = assertTrue(pathChanged(null, wifi))

    @Test
    fun anIdenticalSnapshotIsNot() = assertFalse(pathChanged(wifi, wifi.copy()))

    @Test
    fun satisfiedFlipsAreChanges() {
        assertTrue(pathChanged(wifi, wifi.copy(satisfied = false)))
        assertTrue(pathChanged(wifi.copy(satisfied = false), wifi))
    }

    @Test
    fun wifiToCellularIsAChange() = assertTrue(pathChanged(wifi, PathSnapshot(true, setOf("cellular"))))
}
