package com.simon.harmonichackernews.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.platform.CloudEndpointAccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.UnknownHostException

/** Resolves off Main; .local must be recognized before permission-gated mDNS resolution. */
internal object LocalAiEndpointPolicy {
    fun requiresLocalAccess(
        host: String,
        resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
        isOnLink: (InetAddress) -> Boolean = { false },
    ): Boolean {
        val name = host.trimEnd('.').lowercase()
        if (name.endsWith(".local")) return true
        val addresses = try {
            resolve(name)
        } catch (_: UnknownHostException) {
            // Preserve the normal DNS error rather than asking for an unrelated permission.
            return false
        }
        return addresses.any { address ->
            !address.isLoopbackAddress && !address.isAnyLocalAddress && (
                address.isSiteLocalAddress || address.isLinkLocalAddress ||
                    // InetAddress.isSiteLocalAddress does not include IPv6 unique-local addresses.
                    (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc) ||
                    isOnLink(address)
                )
        }
    }
}

/** Application-owned so requests survive Activity recreation without retaining an Activity. */
internal class AndroidLocalNetworkAccess(private val context: Context) : CloudEndpointAccess {
    sealed interface UiState {
        data class Request(val id: Long, val host: String, val systemPrompt: Boolean = false) : UiState
        data object Denied : UiState
    }

    private class Pending(val id: Long) {
        val result = CompletableDeferred<Boolean>()
        var waiters = 0
    }

    private val mutableUiState = MutableStateFlow<UiState?>(null)
    val uiState = mutableUiState.asStateFlow()
    // Accessed only on Main. Concurrent summary/Ask requests share a single permission prompt.
    private var pending: Pending? = null
    private var nextId = 0L

    override suspend fun ensureAccess(baseUrl: String) {
        if (Build.VERSION.SDK_INT < 37 || isGranted()) return
        val host = baseUrl.toHttpUrlOrNull()?.host ?: return
        val local = withContext(Dispatchers.IO) {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val onLinkPrefixes = connectivity.allNetworks.flatMap { network ->
                val capabilities = connectivity.getNetworkCapabilities(network)
                if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true ||
                    (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true &&
                        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) != true)
                ) return@flatMap emptyList()
                connectivity.getLinkProperties(network)?.routes.orEmpty()
                    .filter { !it.hasGateway() && !it.isDefaultRoute }
                    .map { it.destination }
            }
            LocalAiEndpointPolicy.requiresLocalAccess(host, isOnLink = { address ->
                onLinkPrefixes.any { it.contains(address) }
            })
        }
        if (!local) return
        withContext(Dispatchers.Main.immediate) {
            if (isGranted()) return@withContext
            val request = pending ?: Pending(++nextId).also {
                pending = it
                mutableUiState.value = UiState.Request(it.id, host)
            }
            request.waiters++
            try {
                if (!request.result.await()) {
                    throw CloudSummaryException(context.getString(R.string.local_network_access_required))
                }
            } finally {
                request.waiters--
                if (request.waiters == 0 && pending === request) {
                    pending = null
                    if (mutableUiState.value is UiState.Request) mutableUiState.value = null
                }
            }
        }
    }

    @RequiresApi(37)
    private fun isGranted(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_LOCAL_NETWORK,
    ) == PackageManager.PERMISSION_GRANTED

    @MainThread
    fun beginSystemPrompt(id: Long) {
        val state = mutableUiState.value as? UiState.Request ?: return
        if (state.id == id) mutableUiState.value = state.copy(systemPrompt = true)
    }

    @MainThread
    fun complete(id: Long, granted: Boolean, showDenied: Boolean = false) {
        val request = pending?.takeIf { it.id == id } ?: return
        mutableUiState.value = if (!granted && showDenied) UiState.Denied else null
        request.result.complete(granted)
    }

    @MainThread
    fun dismissDenied() {
        if (mutableUiState.value == UiState.Denied) mutableUiState.value = null
    }
}
