package com.nexa.social.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors the real connection state (NET_CAPABILITY_VALIDATED), not just "a network exists".
 * Uses ConnectivityManager from the SDK — no extra libraries.
 */
class NetworkMonitor(context: Context) {

    private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)
    private val _isOnline = MutableStateFlow(checkNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        runCatching {
            cm?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOnline.value = checkNow()
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = checkNow()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        _isOnline.value = checkNow()
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }
    }

    fun checkNow(): Boolean {
        val manager = cm ?: return false
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
