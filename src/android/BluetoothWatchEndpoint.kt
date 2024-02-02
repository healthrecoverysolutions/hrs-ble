package com.megster.cordova.ble.central

data class BluetoothWatchEndpoint(
    val deviceId: String,
    val serviceId: String,
    val characteristicId: String,
) {
    override fun equals(other: Any?): Boolean {
        val otherEndpoint = other as? BluetoothWatchEndpoint ?: return false
        return otherEndpoint.serviceId == serviceId
            && otherEndpoint.deviceId == deviceId
            && otherEndpoint.characteristicId == characteristicId
    }

    override fun hashCode(): Int {
        return deviceId.hashCode() + serviceId.hashCode() + characteristicId.hashCode()
    }
}
