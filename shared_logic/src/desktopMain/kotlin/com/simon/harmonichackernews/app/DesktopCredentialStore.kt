package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.platform.CredentialStore
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinError
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** Selects an OS-owned credential vault. Unsupported platforms fail closed. */
internal fun desktopCredentialStore(
    osName: String = System.getProperty("os.name"),
): CredentialStore = when {
    osName.contains("win", ignoreCase = true) -> WindowsCredentialStore()
    osName.contains("mac", ignoreCase = true) -> MacOsKeychainCredentialStore()
    osName.contains("linux", ignoreCase = true) -> LinuxSecretServiceCredentialStore()
    else -> UnavailableCredentialStore
}

/** Windows Credential Manager adapter backed by CredRead/CredWrite generic credentials. */
internal class WindowsCredentialStore(
    private val service: String = SERVICE,
    private val api: WindowsCredentialApi = WindowsCredentialApi.INSTANCE,
) : CredentialStore {
    override fun read(id: String): String? {
        val result = PointerByReference()
        if (!api.CredReadW(targetName(id), CREDENTIAL_TYPE_GENERIC, 0, result)) return null
        val pointer = result.value ?: return null
        return try {
            val credential = WindowsCredential(pointer)
            val blob = credential.CredentialBlob
            if (credential.CredentialBlobSize == 0 || blob == null) {
                ""
            } else {
                val bytes = blob.getByteArray(0, credential.CredentialBlobSize)
                try {
                    bytes.toString(StandardCharsets.UTF_8)
                } finally {
                    bytes.fill(0)
                }
            }
        } finally {
            api.CredFree(pointer)
        }
    }

    override fun write(id: String, value: String): Boolean {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val blob = Memory(bytes.size.coerceAtLeast(1).toLong())
        return try {
            if (bytes.isNotEmpty()) blob.write(0, bytes, 0, bytes.size)
            val credential = WindowsCredential().apply {
                Type = CREDENTIAL_TYPE_GENERIC
                TargetName = targetName(id)
                CredentialBlobSize = bytes.size
                CredentialBlob = blob
                Persist = CREDENTIAL_PERSIST_LOCAL_MACHINE
                UserName = WString("Harmonic")
                write()
            }
            api.CredWriteW(credential, 0)
        } finally {
            blob.clear()
            bytes.fill(0)
        }
    }

    override fun remove(id: String): Boolean =
        api.CredDeleteW(targetName(id), CREDENTIAL_TYPE_GENERIC, 0) ||
            Native.getLastError() == WinError.ERROR_NOT_FOUND

    private fun targetName(id: String) = WString("$service/$id")

    private companion object {
        const val SERVICE = "com.simon.harmonichackernews.desktop"
        const val CREDENTIAL_TYPE_GENERIC = 1
        const val CREDENTIAL_PERSIST_LOCAL_MACHINE = 2
    }
}

internal interface WindowsCredentialApi : StdCallLibrary {
    fun CredReadW(
        targetName: WString,
        type: Int,
        flags: Int,
        credential: PointerByReference,
    ): Boolean

    fun CredWriteW(credential: WindowsCredential, flags: Int): Boolean
    fun CredDeleteW(targetName: WString, type: Int, flags: Int): Boolean
    fun CredFree(credential: Pointer)

    companion object {
        val INSTANCE: WindowsCredentialApi = Native.load(
            "Advapi32",
            WindowsCredentialApi::class.java,
            W32APIOptions.DEFAULT_OPTIONS,
        )
    }
}

@Structure.FieldOrder(
    "Flags",
    "Type",
    "TargetName",
    "Comment",
    "LastWritten",
    "CredentialBlobSize",
    "CredentialBlob",
    "Persist",
    "AttributeCount",
    "Attributes",
    "TargetAlias",
    "UserName",
)
internal class WindowsCredential : Structure {
    @JvmField var Flags: Int = 0
    @JvmField var Type: Int = 0
    @JvmField var TargetName: WString? = null
    @JvmField var Comment: WString? = null
    @JvmField var LastWritten: WinBase.FILETIME = WinBase.FILETIME()
    @JvmField var CredentialBlobSize: Int = 0
    @JvmField var CredentialBlob: Pointer? = null
    @JvmField var Persist: Int = 0
    @JvmField var AttributeCount: Int = 0
    @JvmField var Attributes: Pointer? = null
    @JvmField var TargetAlias: WString? = null
    @JvmField var UserName: WString? = null

    constructor() : super()

    constructor(pointer: Pointer) : super(pointer) {
        read()
    }
}

/** Uses the login Keychain on macOS. */
private class MacOsKeychainCredentialStore : CredentialStore {
    override fun read(id: String): String? {
        val result = security("find-generic-password", "-s", SERVICE, "-a", id, "-w")
        return result.output.trimEnd('\r', '\n').takeIf { result.success }
    }

    override fun write(id: String, value: String): Boolean = security(
        "add-generic-password",
        "-U",
        "-s",
        SERVICE,
        "-a",
        id,
        "-w",
        value,
    ).success

    override fun remove(id: String): Boolean {
        val result = security("delete-generic-password", "-s", SERVICE, "-a", id)
        return result.success || result.output.contains("could not be found", ignoreCase = true)
    }

    private fun security(vararg arguments: String): CredentialCommandResult =
        runCredentialCommand(listOf("/usr/bin/security") + arguments)

    private companion object {
        const val SERVICE = "com.simon.harmonichackernews.desktop"
    }
}

/** Linux Secret Service adapter using libsecret's standard secret-tool client. */
private class LinuxSecretServiceCredentialStore : CredentialStore {
    override fun read(id: String): String? {
        val result = secretTool("lookup", "service", SERVICE, "account", id)
        return result.output.trimEnd('\r', '\n').takeIf { result.success }
    }

    override fun write(id: String, value: String): Boolean = secretTool(
        "store",
        "--label=Harmonic",
        "service",
        SERVICE,
        "account",
        id,
        standardInput = value,
    ).success

    override fun remove(id: String): Boolean = secretTool(
        "clear",
        "service",
        SERVICE,
        "account",
        id,
    ).let { it.success || it.output.contains("not found", ignoreCase = true) }

    private fun secretTool(
        vararg arguments: String,
        standardInput: String? = null,
    ): CredentialCommandResult = runCredentialCommand(
        command = listOf("secret-tool") + arguments,
        standardInput = standardInput,
    )

    private companion object {
        const val SERVICE = "com.simon.harmonichackernews.desktop"
    }
}

private data object UnavailableCredentialStore : CredentialStore {
    override fun read(id: String): String? = null
    override fun write(id: String, value: String): Boolean = false
    override fun remove(id: String): Boolean = true
}

private data class CredentialCommandResult(
    val success: Boolean,
    val output: String,
)

private fun runCredentialCommand(
    command: List<String>,
    standardInput: String? = null,
): CredentialCommandResult = runCatching {
    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    process.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { input ->
        if (standardInput != null) {
            input.write(standardInput)
            input.newLine()
        }
    }
    if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return@runCatching CredentialCommandResult(false, "Credential command timed out")
    }
    CredentialCommandResult(
        success = process.exitValue() == 0,
        output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
    )
}.getOrElse { CredentialCommandResult(false, it.message.orEmpty()) }
