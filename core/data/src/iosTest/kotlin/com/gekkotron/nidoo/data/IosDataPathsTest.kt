package com.gekkotron.nidoo.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosDataPathsTest {
    private fun excludedFromBackup(dir: String): Boolean? = memScoped {
        val value = alloc<ObjCObjectVar<Any?>>()
        NSURL.fileURLWithPath(dir, isDirectory = true).getResourceValue(value.ptr, forKey = NSURLIsExcludedFromBackupKey, error = null)
        (value.value as? NSNumber)?.boolValue
    }

    /** The profile's secrets are sealed by a ThisDeviceOnly Keychain key: a backup restored on another phone can't open them. */
    @Test
    fun profileFileIsExcludedFromBackup() {
        val parent = checkNotNull(nidooDeviceOnlyPath(ProfileRepository.FILE).parent?.parent)
        assertEquals(true, excludedFromBackup(parent.toString()))
    }

    @Test
    fun cacheIsExcludedFromBackup() {
        assertEquals(true, excludedFromBackup(checkNotNull(nidooCachePath(FileEntityCache.FILE).parent).toString()))
    }
}
