package com.megster.cordova.ble.central

enum class BLEEventListenerType(val value: String) {
    SET_EVENT_LISTENER("setEventListener"),
    REMOVE_EVENT_LISTENER("removeEventListener"),
    WATCH("watch"),
    UNWATCH("unwatch"),
}
