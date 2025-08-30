package com.yourorg.scanner.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityMonitor @Inject constructor(
    private val context: Context
) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _wasOffline = MutableStateFlow(false)
    val wasOffline: StateFlow<Boolean> = _wasOffline.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    companion object {
        private const val TAG = "ConnectivityMonitor"
    }
    
    init {
        startMonitoring()
        updateConnectionStatus()
    }
    
    private fun startMonitoring() {
        try {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available: $network")
                    val wasOfflineBefore = !_isConnected.value
                    _isConnected.value = true
                    if (wasOfflineBefore) {
                        _wasOffline.value = true
                        Log.d(TAG, "Device came back online - sync needed")
                    }
                }
                
                override fun onLost(network: Network) {
                    Log.d(TAG, "Network lost: $network")
                    updateConnectionStatus()
                }
                
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    Log.d(TAG, "Network capabilities changed: $network")
                    updateConnectionStatus()
                }
            }
            
            // Use registerDefaultNetworkCallback instead of registerNetworkCallback to avoid too many callbacks
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback, using polling approach", e)
            // Fall back to periodic checking if callback registration fails
            startPollingMode()
        }
    }
    
    private fun startPollingMode() {
        // Simple polling approach as fallback
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val wasConnected = _isConnected.value
                updateConnectionStatus()
                if (!wasConnected && _isConnected.value) {
                    _wasOffline.value = true
                    Log.d(TAG, "Device came back online (polling) - sync needed")
                }
                delay(5000) // Check every 5 seconds
            }
        }
    }
    
    private fun updateConnectionStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        val isConnected = networkCapabilities?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } ?: false
        
        val wasOfflineBefore = !_isConnected.value
        _isConnected.value = isConnected
        
        if (!isConnected && !wasOfflineBefore) {
            Log.d(TAG, "Device went offline")
        } else if (isConnected && wasOfflineBefore) {
            _wasOffline.value = true
            Log.d(TAG, "Device came back online - sync needed")
        }
        
        Log.d(TAG, "Connection status updated: $isConnected")
    }
    
    fun isCurrentlyConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        return networkCapabilities?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } ?: false
    }
    
    fun markSyncCompleted() {
        _wasOffline.value = false
        Log.d(TAG, "Sync completed - offline flag cleared")
    }
    
    fun stopMonitoring() {
        networkCallback?.let { callback ->
            connectivityManager.unregisterNetworkCallback(callback)
        }
        networkCallback = null
    }
}