package com.gekkotron.nidoo.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/** A file in `Application Support/Nidoo/` (backed up with the app). The directory is created on first use. */
fun nidooDataPath(file: String): Path = nidooDirectory().resolve(file)

/** Like [nidooDataPath], but the directory is excluded from iCloud and device backups (the entity cache). */
fun nidooCachePath(file: String): Path = excludedFromBackup(CACHE_DIR).resolve(file)

/**
 * A file that must never reach another phone: `Application Support/Nidoo/device/`, excluded from backups. The profile
 * lives here, because its secrets are sealed by a Keychain key that is ThisDeviceOnly: restored elsewhere, it could
 * not be opened.
 */
fun nidooDeviceOnlyPath(file: String): Path = excludedFromBackup(DEVICE_DIR).resolve(file)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun excludedFromBackup(name: String): Path {
    val dir = nidooDirectory().resolve(name)
    FileSystem.SYSTEM.createDirectories(dir)
    NSURL.fileURLWithPath(dir.toString(), isDirectory = true)
        .setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    return dir
}

@OptIn(ExperimentalForeignApi::class)
private fun nidooDirectory(): Path {
    val support = NSFileManager.defaultManager.URLForDirectory(
        NSApplicationSupportDirectory, NSUserDomainMask, appropriateForURL = null, create = true, error = null,
    )
    val dir = checkNotNull(support?.path) { "no Application Support directory" }.toPath().resolve(APP_DIR)
    FileSystem.SYSTEM.createDirectories(dir)
    return dir
}

private const val APP_DIR = "Nidoo"
private const val CACHE_DIR = "cache"
private const val DEVICE_DIR = "device"
