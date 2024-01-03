package com.megster.cordova.ble.central

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

internal class BLEEventManager private constructor() {
    companion object {
        val sharedInstance = BLEEventManager()
    }

    private val _notificationBroadcaster = MutableSharedFlow<BluetoothEvent>()
    private val notificationBroadcaster: SharedFlow<BluetoothEvent>
        get() = _notificationBroadcaster.asSharedFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val bluetoothWatchEndpoints: MutableList<BluetoothWatchEndpoint> = mutableListOf()

    private var listenerJob: Job? = null
    private var scanServicesJob: Job? = null

    init {
        setupPolling()
    }

   private fun setupPolling() {
        scanServicesJob = coroutineScope.launch {
            //TODO: Add Polling Logic
        }
    }

    fun sendEvent(bleEvent: BluetoothEvent) {
        coroutineScope.launch {
            _notificationBroadcaster.emit(bleEvent)
        }
    }

    fun watch(endpoints: List<BluetoothWatchEndpoint>) {
        bluetoothWatchEndpoints.addAll(endpoints)
    }

    fun unwatch(endpoints: List<BluetoothWatchEndpoint>) {
        endpoints.forEach { currentEndpoint ->
            bluetoothWatchEndpoints.removeAll { removingEndpoint ->
                return@removeAll currentEndpoint.characteristicId == removingEndpoint.characteristicId
                    && currentEndpoint.serviceId == removingEndpoint.serviceId
                    && currentEndpoint.deviceId == removingEndpoint.deviceId
            }
        }
    }

    fun addEventListener(listener: (BluetoothEvent) -> Unit) {
        listenerJob = coroutineScope.launch {
            notificationBroadcaster.filter {
                return@filter bluetoothWatchEndpoints.any { endpoint ->
                    return@any endpoint.deviceId == it.deviceId
                        && if (it.serviceId != null && it.characteristicId != null) {
                            it.serviceId == endpoint.serviceId && it.characteristicId == endpoint.characteristicId
                        } else {
                            true
                        }
                }
            }.collect {
                listener(it)
            }
        }
    }

    fun removeEventListener() {
        listenerJob?.cancel()
        listenerJob = null
    }
}
