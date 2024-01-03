package com.megster.cordova.ble.central

import org.apache.cordova.CallbackContext;
import java.util.UUID;

/**
 * Android BLE stack is async but doesn't queue commands, so it ignore additional commands when processing. WTF?
 * This is an object to encapsulate the command data for queuing
 */
internal class BLECommand {
    // BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    // BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    var callbackContext: CallbackContext
        private set
    var serviceUUID: UUID? = null
        private set
    var characteristicUUID: UUID? = null
        private set
    var data: ByteArray? = null
        private set
    var type: Int
        private set
    var pSM = 0
        private set

    constructor(
        callbackContext: CallbackContext,
        serviceUUID: UUID?,
        characteristicUUID: UUID?,
        type: Int
    ) {
        this.callbackContext = callbackContext
        this.serviceUUID = serviceUUID
        this.characteristicUUID = characteristicUUID
        this.type = type
    }

    constructor(
        callbackContext: CallbackContext,
        serviceUUID: UUID?,
        characteristicUUID: UUID?,
        data: ByteArray,
        type: Int
    ) {
        this.callbackContext = callbackContext
        this.serviceUUID = serviceUUID
        this.characteristicUUID = characteristicUUID
        this.data = data
        this.type = type
    }

    constructor(callbackContext: CallbackContext, psm: Int, type: Int) {
        this.callbackContext = callbackContext
        pSM = psm
        this.type = type
    }

    constructor(callbackContext: CallbackContext, psm: Int, data: ByteArray?, type: Int) {
        this.callbackContext = callbackContext
        pSM = psm
        this.data = data
        this.type = type
    }

    companion object {
        // Types
        var READ = 10000
        var REGISTER_NOTIFY = 10001
        var REMOVE_NOTIFY = 10002
        var READ_RSSI = 10003
    }
}

