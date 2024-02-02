package com.megster.cordova.ble.central

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import org.json.JSONArray

object Helper {
    fun decodeProperties(characteristic: BluetoothGattCharacteristic): JSONArray {

        // NOTE: props strings need to be consistent across iOS and Android
        val props = JSONArray()
        val properties = characteristic.properties
        if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0x0) {
            props.put("Broadcast")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0x0) {
            props.put("Read")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0x0) {
            props.put("WriteWithoutResponse")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0x0) {
            props.put("Write")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0x0) {
            props.put("Notify")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0x0) {
            props.put("Indicate")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0x0) {
            // Android calls this "write with signature", using iOS name for now
            props.put("AuthenticateSignedWrites")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0x0) {
            props.put("ExtendedProperties")
        }

//      iOS only?
//
//            if ((p & CBCharacteristicPropertyNotifyEncryptionRequired) != 0x0) {  // 0x100
//                [props addObject:@"NotifyEncryptionRequired"];
//            }
//
//            if ((p & CBCharacteristicPropertyIndicateEncryptionRequired) != 0x0) { // 0x200
//                [props addObject:@"IndicateEncryptionRequired"];
//            }
        return props
    }

    fun decodePermissions(characteristic: BluetoothGattCharacteristic): JSONArray {

        // NOTE: props strings need to be consistent across iOS and Android
        val props = JSONArray()
        val permissions = characteristic.permissions
        if (permissions and BluetoothGattCharacteristic.PERMISSION_READ != 0x0) {
            props.put("Read")
        }
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE != 0x0) {
            props.put("Write")
        }
        if (permissions and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED != 0x0) {
            props.put("ReadEncrypted")
        }
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED != 0x0) {
            props.put("WriteEncrypted")
        }
        if (permissions and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM != 0x0) {
            props.put("ReadEncryptedMITM")
        }
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM != 0x0) {
            props.put("WriteEncryptedMITM")
        }
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED != 0x0) {
            props.put("WriteSigned")
        }
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM != 0x0) {
            props.put("WriteSignedMITM")
        }
        return props
    }

    fun decodePermissions(descriptor: BluetoothGattDescriptor): JSONArray {

        // NOTE: props strings need to be consistent across iOS and Android
        val props = JSONArray()
        val permissions = descriptor.permissions
        if (permissions and BluetoothGattDescriptor.PERMISSION_READ != 0x0) {
            props.put("Read")
        }
        if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE != 0x0) {
            props.put("Write")
        }
        if (permissions and BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED != 0x0) {
            props.put("ReadEncrypted")
        }
        if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED != 0x0) {
            props.put("WriteEncrypted")
        }
        if (permissions and BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM != 0x0) {
            props.put("ReadEncryptedMITM")
        }
        if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM != 0x0) {
            props.put("WriteEncryptedMITM")
        }
        if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED != 0x0) {
            props.put("WriteSigned")
        }
        if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED_MITM != 0x0) {
            props.put("WriteSignedMITM")
        }
        return props
    }
}
