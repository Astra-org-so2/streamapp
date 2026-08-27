package com.streamapp.core.features.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkStatus(
    val isWifiConnected: Boolean = false,
    val isCellularConnected: Boolean = false
)

@Singleton
class NetworkBondingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(NetworkStatus())
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val activeWifiNetworks = mutableSetOf<Network>()
    private val activeCellularNetworks = mutableSetOf<Network>()
    private var isMonitoring = false
    private val lock = Any()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handleCapabilitiesChange(network)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            synchronized(lock) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

                if (hasInternet && isWifi) {
                    activeWifiNetworks.add(network)
                } else {
                    activeWifiNetworks.remove(network)
                }

                if (hasInternet && isCellular) {
                    activeCellularNetworks.add(network)
                } else {
                    activeCellularNetworks.remove(network)
                }

                publishStatus()
            }
        }

        override fun onLost(network: Network) {
            synchronized(lock) {
                // Do NOT call getNetworkCapabilities(network) on lost network (it returns null)
                activeWifiNetworks.remove(network)
                activeCellularNetworks.remove(network)
                publishStatus()
            }
        }
    }

    private fun handleCapabilitiesChange(network: Network) {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return
        networkCallback.onCapabilitiesChanged(network, capabilities)
    }

    private fun publishStatus() {
        val hasWifi = activeWifiNetworks.isNotEmpty()
        val hasCellular = activeCellularNetworks.isNotEmpty()
        _networkStatus.update {
            it.copy(
                isWifiConnected = hasWifi,
                isCellularConnected = hasCellular
            )
        }
        AppLogger.i(LogCategory.NETWORK, "Network status updated: WiFi=$hasWifi, Cellular=$hasCellular")
    }

    fun startMonitoring() {
        synchronized(lock) {
            if (isMonitoring) return
            try {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, networkCallback)
                isMonitoring = true
                AppLogger.i(LogCategory.NETWORK, "NetworkBondingManager monitoring started")
            } catch (e: Exception) {
                AppLogger.e(LogCategory.NETWORK, "Failed to register network callback", e)
            }
        }
    }

    fun stopMonitoring() {
        synchronized(lock) {
            if (!isMonitoring) return
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                AppLogger.w(LogCategory.NETWORK, "Failed to unregister network callback: ${e.message}")
            } finally {
                isMonitoring = false
                activeWifiNetworks.clear()
                activeCellularNetworks.clear()
                _networkStatus.value = NetworkStatus()
                AppLogger.i(LogCategory.NETWORK, "NetworkBondingManager monitoring stopped")
            }
        }
    }
}
