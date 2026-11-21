package com.gekkotron.nidoo.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

internal sealed interface KeychainRead {
    class Found(val value: ByteArray) : KeychainRead

    data object NotFound : KeychainRead

    /** Any other OSStatus, e.g. errSecInteractionNotAllowed (-25308) while the phone is locked after a reboot. */
    data class Failed(val status: Int) : KeychainRead
}

/** The two Keychain calls the secret store needs, behind an interface so tests can stand in for the system. */
internal interface Keychain {
    fun read(service: String, account: String): KeychainRead

    /** @return the OSStatus: 0 when added, errSecDuplicateItem (-25299) when the item already exists. */
    fun add(service: String, account: String, value: ByteArray): Int
}

/** Generic-password items, readable after the first unlock and never migrated to another device. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object SystemKeychain : Keychain {
    override fun read(service: String, account: String): KeychainRead = withQuery(service, account) { query ->
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)?.let { KeychainRead.Found(it.toByteArray()) }
                    ?: KeychainRead.Failed(status)
                errSecItemNotFound -> KeychainRead.NotFound
                else -> KeychainRead.Failed(status)
            }
        }
    }

    override fun add(service: String, account: String, value: ByteArray): Int = withQuery(service, account) { query ->
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        val data = CFBridgingRetain(value.toNSData())
        try {
            CFDictionaryAddValue(query, kSecValueData, data)
            SecItemAdd(query, null)
        } finally {
            CFRelease(data)
        }
    }

    private fun <T> withQuery(service: String, account: String, block: (CFMutableDictionaryRef?) -> T): T {
        val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val cfService: CFTypeRef? = CFBridgingRetain(service)
        val cfAccount: CFTypeRef? = CFBridgingRetain(account)
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, cfService)
            CFDictionaryAddValue(query, kSecAttrAccount, cfAccount)
            return block(query)
        } finally {
            CFRelease(cfService)
            CFRelease(cfAccount)
            CFRelease(query)
        }
    }

    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        if (size == 0) return ByteArray(0)
        return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }
    }
}
