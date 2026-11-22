package com.gekkotron.nidoo.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun animationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return true
    return remember { !UIAccessibilityIsReduceMotionEnabled() }
}
