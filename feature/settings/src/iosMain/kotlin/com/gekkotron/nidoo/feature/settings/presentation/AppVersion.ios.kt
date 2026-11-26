package com.gekkotron.nidoo.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSBundle

@OptIn(ExperimentalNativeApi::class)
@Composable
actual fun rememberAppVersion(): String = remember {
    val name = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"
    if (Platform.isDebugBinary) "$name · debug" else name
}
