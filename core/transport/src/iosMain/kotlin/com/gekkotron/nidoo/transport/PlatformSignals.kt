package com.gekkotron.nidoo.transport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_other
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_interface_type_wired
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_t
import platform.Network.nw_path_uses_interface_type
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.darwin.dispatch_get_main_queue

/** Emits when the network path changes in a way worth a reconnect check (see [pathChanged]); the first path counts. */
@OptIn(ExperimentalForeignApi::class)
fun iosNetworkChanges(): Flow<Unit> = callbackFlow {
    var previous: PathSnapshot? = null
    val monitor = nw_path_monitor_create()
    nw_path_monitor_set_update_handler(monitor) { path ->
        val current = path.snapshot()
        if (pathChanged(previous, current)) trySend(Unit)
        previous = current
    }
    nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
    nw_path_monitor_start(monitor)
    awaitClose { nw_path_monitor_cancel(monitor) }
}

/** true when the app becomes active, false when it goes to the background. */
fun iosForeground(): Flow<Boolean> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    val active = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { trySend(true) }
    val background = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { trySend(false) }
    awaitClose {
        center.removeObserver(active)
        center.removeObserver(background)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun nw_path_t.snapshot(): PathSnapshot = PathSnapshot(
    satisfied = nw_path_get_status(this) == nw_path_status_satisfied,
    interfaces = buildSet {
        if (nw_path_uses_interface_type(this@snapshot, nw_interface_type_wifi)) add("wifi")
        if (nw_path_uses_interface_type(this@snapshot, nw_interface_type_cellular)) add("cellular")
        if (nw_path_uses_interface_type(this@snapshot, nw_interface_type_wired)) add("wired")
        if (nw_path_uses_interface_type(this@snapshot, nw_interface_type_other)) add("other")
    },
)
