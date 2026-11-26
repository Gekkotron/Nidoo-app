package com.gekkotron.nidoo.feature.settings.presentation

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** "0.12", or "0.12 · debug" on a debuggable build. Read from the package, so this module needs no BuildConfig. */
fun appVersion(context: Context): String {
    val name = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    return if (debug) "$name · debug" else name
}

@Composable
actual fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember { appVersion(context) }
}
