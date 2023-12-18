package com.megster.cordova.ble.central

data class BLENotification(
    val messageId: ULong,
    val type: BLENotificationType,
    val deviceId: String,
    val serviceId: String,
    val characteristicId: String,
    val data: List<Byte>
)
