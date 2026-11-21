package com.gekkotron.nidoo.transport

/** What the phone's network path looks like: usable or not, and over which interfaces (wifi, cellular, wired…). */
data class PathSnapshot(val satisfied: Boolean, val interfaces: Set<String>)

/** Whether [current] is worth a reconnect check: the first snapshot, or any change in usability or interfaces. */
fun pathChanged(previous: PathSnapshot?, current: PathSnapshot): Boolean = previous != current
