package com.megster.cordova.ble.central

import org.json.JSONObject

var messageCounter: UInt = 0u

val nextMessageId: UInt
    get() {
        messageCounter++
        if (messageCounter >= UInt.MAX_VALUE) {
            messageCounter = 1u
        }
        return messageCounter
    }

data class BluetoothEvent(
    val type: BluetoothEventType,
    val deviceId: String? = null,
    val serviceId: String? = null,
    val characteristicId: String? = null,
    val data: JSONObject? = null,
    val messageId: UInt = nextMessageId,
) {
    fun toJSON() : JSONObject{
        val json = JSONObject()
        json.put("messageId", messageId)
        json.put("type", type.name)
        json.put("deviceId", deviceId)

        if (serviceId != null) {
            json.put("serviceId", serviceId)
        }

        if (characteristicId != null) {
            json.put("characteristicId", characteristicId)
        }

        if (data != null) {
            json.put("data", data)
        }

        return json
    }
}
