package com.gekkotron.nidoo.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

/**
 * The Reduce Motion setting, shared by every caller. Reduce Motion can change while the app runs, so the one
 * observer is registered on the main queue the first time this object is read, and never removed.
 */
internal object ReduceMotion {
    private val state = MutableStateFlow(UIAccessibilityIsReduceMotionEnabled())

    /** True when animations should be reduced. */
    val reduce: StateFlow<Boolean> = state

    init {
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIAccessibilityReduceMotionStatusDidChangeNotification,
            null,
            NSOperationQueue.mainQueue,
        ) {
            state.value = UIAccessibilityIsReduceMotionEnabled()
        }
    }
}

@Composable
actual fun animationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return true
    return !ReduceMotion.reduce.collectAsState().value
}
