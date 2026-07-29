package com.ers.emergencyresponseapp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable

/**
 * App-wide view of validated internet connectivity.
 *
 * A device can be connected to Wi-Fi or mobile data without actually having
 * internet access. For that reason, Online requires both INTERNET and
 * VALIDATED capabilities instead of checking only whether a network exists.
 */
enum class ConnectivityStatus {
    Checking,
    Online,
    Offline
}

class ConnectivityObserver(context: Context) : Closeable {
    private val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(ConnectivityStatus.Checking)
    val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateFromActiveNetwork()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            if (network == connectivityManager.activeNetwork) {
                _status.value = networkCapabilities.toConnectivityStatus()
            } else {
                updateFromActiveNetwork()
            }
        }

        override fun onLost(network: Network) {
            updateFromActiveNetwork()
        }

        override fun onUnavailable() {
            _status.value = ConnectivityStatus.Offline
        }
    }

    init {
        start()
    }

    private fun start() {
        updateFromActiveNetwork()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
            registered = true
            updateFromActiveNetwork()
        } catch (_: SecurityException) {
            // ACCESS_NETWORK_STATE is declared in the manifest. If an OEM still
            // denies access, fail closed so the UI never claims it is online.
            _status.value = ConnectivityStatus.Offline
        } catch (_: RuntimeException) {
            _status.value = ConnectivityStatus.Offline
        }
    }

    fun refresh() {
        updateFromActiveNetwork()
    }

    private fun updateFromActiveNetwork() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { network ->
            connectivityManager.getNetworkCapabilities(network)
        }
        _status.value = capabilities.toConnectivityStatus()
    }

    private fun NetworkCapabilities?.toConnectivityStatus(): ConnectivityStatus {
        if (this == null) return ConnectivityStatus.Offline

        val hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return if (hasInternet && isValidated) {
            ConnectivityStatus.Online
        } else {
            ConnectivityStatus.Offline
        }
    }

    override fun close() {
        if (!registered) return
        registered = false
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
