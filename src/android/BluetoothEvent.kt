package com.megster.cordova.ble.central

import org.json.JSONObject

data class BluetoothEvent(
    val messageId: ULong,
    val type: BluetoothEventType,
    val deviceId: String,
    val serviceId: String?,
    val characteristicId: String?,
    val data: List<Byte>?
) {
    fun toJSON() : JSONObject{
        val json = JSONObject()
        json.put("messageId", messageId)
        json.put("type", type.name)
        json.put("deviceId", deviceId)
        json.put("serviceId", serviceId)
        json.put("characteristicId", characteristicId)
        json.put("data", data)
        return json
    }
}
