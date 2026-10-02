package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.platform.CredentialStore
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.platform.mac.CoreFoundation.CFDataRef
import com.sun.jna.platform.mac.CoreFoundation.CFIndex
import com.sun.jna.platform.mac.CoreFoundation.CFMutableDictionaryRef
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef
import com.sun.jna.platform.mac.CoreFoundation.CFTypeRef
import com.sun.jna.ptr.PointerByReference

/** Uses the same service/account identity as the former security CLI, without secret-bearing argv. */
internal class MacOsKeychainCredentialStore(
    private val service: String = "com.simon.harmonichackernews.desktop",
    private val api: MacKeychainApi = NativeMacKeychainApi,
) : CredentialStore {
    override fun read(id: String): String? = runCatching {
        api.read(service, id)?.let { bytes ->
            try { bytes.decodeToString() } finally { bytes.fill(0) }
        }
    }.getOrNull()

    override fun write(id: String, value: String): Boolean {
        val bytes = value.encodeToByteArray()
        return try { runCatching { api.write(service, id, bytes) }.getOrDefault(false) }
        finally { bytes.fill(0) }
    }

    override fun remove(id: String): Boolean =
        runCatching { api.remove(service, id) }.getOrDefault(false)
}

internal interface MacKeychainApi {
    fun read(service: String, account: String): ByteArray?
    fun write(service: String, account: String, value: ByteArray): Boolean
    fun remove(service: String, account: String): Boolean
}

private object NativeMacKeychainApi : MacKeychainApi {
    private val security by lazy { Native.load("Security", SecurityItemApi::class.java) }
    private val securityLibrary by lazy { NativeLibrary.getInstance("Security") }
    private val coreLibrary by lazy { NativeLibrary.getInstance("CoreFoundation") }
    private const val NOT_FOUND = -25300
    private const val DUPLICATE_ITEM = -25299

    override fun read(service: String, account: String): ByteArray? = withQuery(service, account) { query, _ ->
        query.setValue(symbol("kSecReturnData"), CFTypeRef(coreLibrary.getGlobalVariableAddress("kCFBooleanTrue").getPointer(0)))
        query.setValue(symbol("kSecMatchLimit"), symbol("kSecMatchLimitOne"))
        val result = PointerByReference()
        if (security.SecItemCopyMatching(query, result) != 0) return@withQuery null
        val data = CFDataRef(result.value ?: return@withQuery null)
        try { if (data.length == 0) ByteArray(0) else data.bytePtr.getByteArray(0, data.length) }
        finally { CoreFoundation.INSTANCE.CFRelease(data) }
    }

    override fun write(service: String, account: String, value: ByteArray): Boolean = withQuery(service, account) { query, owned ->
        val memory = Memory(value.size.coerceAtLeast(1).toLong())
        val data = try {
            if (value.isNotEmpty()) memory.write(0, value, 0, value.size)
            CoreFoundation.INSTANCE.CFDataCreate(null, memory, CFIndex(value.size.toLong()))
        } finally { memory.clear(); memory.close() }
        owned += data
        val updates = dictionary().also { owned += it }
        updates.setValue(symbol("kSecValueData"), data)
        // The file-based macOS keychain can report a successful zero-byte update while
        // retaining the old password. Recreate only this identity when clearing its value.
        if (value.isEmpty()) {
            val deleted = security.SecItemDelete(query)
            if (deleted != 0 && deleted != NOT_FOUND) return@withQuery false
            query.setValue(symbol("kSecValueData"), data)
            return@withQuery security.SecItemAdd(query, null) == 0
        }
        when (security.SecItemUpdate(query, updates)) {
            0 -> true
            NOT_FOUND -> {
                query.setValue(symbol("kSecValueData"), data)
                when (security.SecItemAdd(query, null)) {
                    0 -> true
                    DUPLICATE_ITEM -> {
                        // Another writer inserted after the lookup. Update the same identity.
                        withQuery(service, account) { retryQuery, _ ->
                            security.SecItemUpdate(retryQuery, updates) == 0
                        }
                    }
                    else -> false
                }
            }
            else -> false
        }
    }

    override fun remove(service: String, account: String): Boolean = withQuery(service, account) { query, _ ->
        security.SecItemDelete(query).let { it == 0 || it == NOT_FOUND }
    }

    private fun symbol(name: String) = CFStringRef(securityLibrary.getGlobalVariableAddress(name).getPointer(0))

    private fun dictionary(): CFMutableDictionaryRef =
        CoreFoundation.INSTANCE.CFDictionaryCreateMutable(
            null, CFIndex(0),
            coreLibrary.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
            coreLibrary.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks"),
        )

    private inline fun <T> withQuery(service: String, account: String, block: (CFMutableDictionaryRef, MutableList<CFTypeRef>) -> T): T {
        val owned = mutableListOf<CFTypeRef>()
        try {
            val query = dictionary().also { owned += it }
            val serviceRef = CFStringRef.createCFString(service).also { owned += it }
            val accountRef = CFStringRef.createCFString(account).also { owned += it }
            query.setValue(symbol("kSecClass"), symbol("kSecClassGenericPassword"))
            query.setValue(symbol("kSecAttrService"), serviceRef)
            query.setValue(symbol("kSecAttrAccount"), accountRef)
            return block(query, owned)
        } finally { owned.asReversed().forEach { CoreFoundation.INSTANCE.CFRelease(it) } }
    }
}

internal interface SecurityItemApi : Library {
    fun SecItemCopyMatching(query: CFMutableDictionaryRef, result: PointerByReference): Int
    fun SecItemUpdate(query: CFMutableDictionaryRef, attributes: CFMutableDictionaryRef): Int
    fun SecItemAdd(attributes: CFMutableDictionaryRef, result: PointerByReference?): Int
    fun SecItemDelete(query: CFMutableDictionaryRef): Int
}
