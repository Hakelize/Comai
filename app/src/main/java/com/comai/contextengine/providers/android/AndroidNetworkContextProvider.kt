package com.comai.contextengine.providers.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.comai.contextengine.providers.interfaces.NetworkContextProvider
import com.comai.contextengine.providers.models.NetworkContextData

/**
 * Android implementation for Network Context. Works completely locally without cloud APIs.
 */
class AndroidNetworkContextProvider(private val context: Context) : NetworkContextProvider {

    override val isAvailable: Boolean = true

    override fun getContextData(): NetworkContextData {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkContextData(networkType = "OFFLINE", isConnected = false)

            val network = cm.activeNetwork ?: return NetworkContextData(networkType = "OFFLINE", isConnected = false)
            val capabilities = cm.getNetworkCapabilities(network) ?: return NetworkContextData(networkType = "OFFLINE", isConnected = false)

            val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

            val networkType = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> if (isConnected) "CONNECTED" else "OFFLINE"
            }

            val detailedType = when {
                isVpn -> "VPN_$networkType"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI_HIGH_SPEED"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR_DATA"
                else -> networkType
            }

            NetworkContextData(
                networkType = networkType,
                isConnected = isConnected,
                isMetered = isMetered,
                isVpnConnected = isVpn,
                detailedType = detailedType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring network context", e)
            NetworkContextData(networkType = "UNKNOWN", isConnected = true, isMetered = false)
        }
    }

    companion object {
        private const val TAG = "AndroidNetworkProvider"
    }
}
