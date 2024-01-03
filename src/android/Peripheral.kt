// (c) 2104 Don Coleman
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.megster.cordova.ble.central

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.os.Handler
import android.util.Base64
import org.apache.cordova.CallbackContext
import org.apache.cordova.LOG
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Peripheral wraps the BluetoothDevice and provides methods to convert to JSON.
 */
class Peripheral : BluetoothGattCallback {
    var device: BluetoothDevice
        private set
    private var advertisingData: ByteArray?
    private var advertisingRSSI: Int
    private var autoconnect = false
    var isConnected = false
        private set
    var isConnecting = false
        private set
    private val commandQueue = ConcurrentLinkedQueue<BLECommand>()
    private val l2capContexts: MutableMap<Int, L2CAPContext> = HashMap()
    private val bleProcessing = AtomicBoolean()
    var gatt: BluetoothGatt? = null
    private var connectCallback: CallbackContext? = null
    private var refreshCallback: CallbackContext? = null
    private var readCallback: CallbackContext? = null
    private var writeCallback: CallbackContext? = null
    private var requestMtuCallback: CallbackContext? = null
    private var currentActivity: Activity? = null
    private var disconnectCount = 0
    private val notificationCallbacks: MutableMap<String, SequentialCallbackContext> = HashMap()

    constructor(device: BluetoothDevice) {
        Timber.i("Creating un-scanned peripheral entry for address: %s", device.address)
        this.device = device
        advertisingRSSI = FAKE_PERIPHERAL_RSSI
        advertisingData = null
    }

    constructor(device: BluetoothDevice, advertisingRSSI: Int, scanRecord: ByteArray?) {
        this.device = device
        this.advertisingRSSI = advertisingRSSI
        advertisingData = scanRecord
    }

    @SuppressLint("MissingPermission")
    private fun gattConnect() {
        closeGatt()
        isConnected = false
        isConnecting = true
        queueCleanup()
        callbackCleanup()
        val device = device
        gatt = if (Build.VERSION.SDK_INT < 23) {
            device.connectGatt(currentActivity, autoconnect, this)
        } else {
            device.connectGatt(currentActivity, autoconnect, this, BluetoothDevice.TRANSPORT_LE)
        }
    }

    fun connect(callbackContext: CallbackContext, activity: Activity?, auto: Boolean) {
        currentActivity = activity
        autoconnect = auto
        connectCallback = callbackContext
        if (refreshCallback != null) {
            refreshCallback!!.error(this.asJSONObject("refreshDeviceCache aborted due to new connect call"))
            refreshCallback = null
        }
        gattConnect()
        val result = PluginResult(PluginResult.Status.NO_RESULT)
        result.keepCallback = true
        callbackContext.sendPluginResult(result)
    }

    // the app requested the central disconnect from the peripheral
    // disconnect the gatt, do not call connectCallback.error
    fun disconnect() {
        isConnected = false
        isConnecting = false
        autoconnect = false
        closeGatt()
        queueCleanup()
        callbackCleanup()
    }

    // the peripheral disconnected
    // always call connectCallback.error to notify the app
    fun peripheralDisconnected(message: String) {
        isConnected = false
        isConnecting = false

        // don't remove the gatt for autoconnect
        if (!autoconnect) {
            closeGatt()
        }
        sendDisconnectMessage(message)
        queueCleanup()
        callbackCleanup()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        var localGatt: BluetoothGatt?
        synchronized(this) {
            localGatt = gatt
            gatt = null
        }
        if (localGatt != null) {
            localGatt!!.disconnect()
            localGatt!!.close()
        }
    }

    // notify the phone that the peripheral disconnected
    private fun sendDisconnectMessage(messageContent: String) {
        if (connectCallback != null) {
            val message = this.asJSONObject(messageContent)
            if (autoconnect) {
                val result = PluginResult(PluginResult.Status.ERROR, message)
                result.keepCallback = true
                connectCallback!!.sendPluginResult(result)
            } else {
                connectCallback!!.error(message)
                connectCallback = null
            }
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
        Timber.i("mtu=%d, status=%d", mtu, status)
        if (status == BluetoothGatt.GATT_SUCCESS) {
            requestMtuCallback!!.success(mtu)
        } else {
            requestMtuCallback!!.error("MTU request failed")
        }
        requestMtuCallback = null
    }

    @SuppressLint("MissingPermission")
    fun requestMtu(callback: CallbackContext, mtuValue: Int) {
        Timber.i("requestMtu mtu=%d", mtuValue)
        if (gatt == null) {
            callback.error("No GATT")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            callback.error("Android version does not support requestMtu")
            return
        }
        if (gatt!!.requestMtu(mtuValue)) {
            requestMtuCallback = callback
        } else {
            callback.error("Could not initiate MTU request")
        }
    }

    @SuppressLint("MissingPermission")
    fun requestConnectionPriority(priority: Int) {
        if (gatt != null) {
            Timber.i("requestConnectionPriority priority=%s", priority)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                gatt!!.requestConnectionPriority(priority)
            }
        }
    }

    /**
     * Uses reflection to refresh the device cache. This *might* be helpful if a peripheral changes
     * services or characteristics and does not correctly implement Service Changed 0x2a05
     * on Generic Attribute Service 0x1801.
     *
     * Since this uses an undocumented API it's not guaranteed to work.
     *
     */
    @SuppressLint("MissingPermission")
    fun refreshDeviceCache(callback: CallbackContext, timeoutMillis: Long) {
        Timber.i("refreshDeviceCache")
        var success = false
        if (gatt != null) {
            try {
                val refresh = gatt!!.javaClass.getMethod("refresh")
                if (refresh != null) {
                    success = refresh.invoke(gatt) as Boolean
                    if (success) {
                        refreshCallback = callback
                        val handler = Handler()
                        Timber.i("Waiting $timeoutMillis milliseconds before discovering services")
                        handler.postDelayed({
                            if (gatt != null) {
                                try {
                                    gatt!!.discoverServices()
                                } catch (e: Exception) {
                                    Timber.e("refreshDeviceCache Failed after delay %s", e.message)
                                }
                            }
                        }, timeoutMillis)
                    }
                } else {
                    LOG.w(TAG, "Refresh method not found on gatt")
                }
            } catch (e: Exception) {
                Timber.e("refreshDeviceCache Failed %s", e.message)
            }
        }
        if (!success) {
            callback.error("Service refresh failed")
        }
    }

    val isUnscanned: Boolean
        get() = advertisingData == null

    @SuppressLint("MissingPermission")
    fun asJSONObject(): JSONObject {
        val json = JSONObject()
        try {
            json.put("name", device.name)
            json.put("id", device.address) // mac address
            if (advertisingData != null) {
                json.put("advertising", byteArrayToJSON(advertisingData))
            }
            // TODO real RSSI if we have it, else
            if (advertisingRSSI != FAKE_PERIPHERAL_RSSI) {
                json.put("rssi", advertisingRSSI)
            }
        } catch (e: JSONException) { // this shouldn't happen
            e.printStackTrace()
        }
        return json
    }

    @SuppressLint("MissingPermission")
    fun asJSONObject(errorMessage: String?): JSONObject {
        val json = JSONObject()
        try {
            json.put("name", device.name)
            json.put("id", device.address) // mac address
            json.put("errorMessage", errorMessage)
        } catch (e: JSONException) { // this shouldn't happen
            e.printStackTrace()
        }
        return json
    }

    fun asJSONObject(gatt: BluetoothGatt?): JSONObject {
        val json = asJSONObject()
        try {
            val servicesArray = JSONArray()
            val characteristicsArray = JSONArray()
            json.put("services", servicesArray)
            json.put("characteristics", characteristicsArray)
            if (isConnected && gatt != null) {
                for (service in gatt.services) {
                    servicesArray.put(UUIDHelper.uuidToString(service.uuid))
                    for (characteristic in service.characteristics) {
                        val characteristicsJSON = JSONObject()
                        characteristicsArray.put(characteristicsJSON)
                        characteristicsJSON.put("service", UUIDHelper.uuidToString(service.uuid))
                        characteristicsJSON.put(
                            "characteristic",
                            UUIDHelper.uuidToString(characteristic.uuid)
                        )
                        //characteristicsJSON.put("instanceId", characteristic.getInstanceId());
                        characteristicsJSON.put(
                            "properties",
                            Helper.decodeProperties(characteristic)
                        )
                        // characteristicsJSON.put("propertiesValue", characteristic.getProperties());
                        if (characteristic.permissions > 0) {
                            characteristicsJSON.put(
                                "permissions",
                                Helper.decodePermissions(characteristic)
                            )
                            // characteristicsJSON.put("permissionsValue", characteristic.getPermissions());
                        }
                        val descriptorsArray = JSONArray()
                        for (descriptor in characteristic.descriptors) {
                            val descriptorJSON = JSONObject()
                            descriptorJSON.put("uuid", UUIDHelper.uuidToString(descriptor.uuid))
                            descriptorJSON.put("value", descriptor.value) // always blank
                            if (descriptor.permissions > 0) {
                                descriptorJSON.put(
                                    "permissions",
                                    Helper.decodePermissions(descriptor)
                                )
                                // descriptorJSON.put("permissionsValue", descriptor.getPermissions());
                            }
                            descriptorsArray.put(descriptorJSON)
                        }
                        if (descriptorsArray.length() > 0) {
                            characteristicsJSON.put("descriptors", descriptorsArray)
                        }
                    }
                }
            }
        } catch (e: JSONException) { // TODO better error handling
            e.printStackTrace()
        }
        return json
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        super.onServicesDiscovered(gatt, status)

        // refreshCallback is a kludge for refreshing services, if it exists, it temporarily
        // overrides the connect callback. Unfortunately this edge case make the code confusing.
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val result = PluginResult(PluginResult.Status.OK, this.asJSONObject(gatt))
            result.keepCallback = true
            if (refreshCallback != null) {
                refreshCallback!!.sendPluginResult(result)
                refreshCallback = null
            } else if (connectCallback != null) {
                connectCallback!!.sendPluginResult(result)
            }
        } else {
            Timber.e("Service discovery failed. status = %d", status)
            if (refreshCallback != null) {
                refreshCallback!!.error(this.asJSONObject("Service discovery failed"))
                refreshCallback = null
            }
            peripheralDisconnected("Service discovery failed")
        }
    }

    /**
     * This enum lists all the BLE devices which dont need Auto connect but require few reconnect attempts when they face Gatt133 connection error
     * Like, On Samsung Tab A7 , with Welch devices, there was random gatt 133 error while making a connection.
     * Thus tried to reconnect in case of 133 failure which solves the problem.
     */
    enum class AUTO_CONNECT_OFF_DEVICES(val text: String) {
        WELCH_SC100("SC100"), TNG_SCALE("TNG SCALE"), WELCH_BP100("BP100");

        companion object {
            @SuppressLint("MissingPermission")
            fun shouldRetryForGatt133Status(device: BluetoothDevice?): Boolean {
                if (device != null) {
                    val text = device.name
                    if (text != null) {
                        for (b in values()) {
                            if (text == b.text) {
                                return true
                            }
                        }
                    }
                }
                return false
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        this.gatt = gatt
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            Timber.i("onConnectionStateChange CONNECTED")
            isConnected = true
            isConnecting = false
            gatt.discoverServices()
        } else {  // Disconnected
            Timber.i("On connection state change ---> $status disconnect count $disconnectCount")
            if (AUTO_CONNECT_OFF_DEVICES.shouldRetryForGatt133Status(device)) {
                Timber.i("Will retry to connect")
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (status != 133 || disconnectCount >= 2) { /*If more then 2 count gatt close process*/
                        disconnectCount = 0
                        Timber.i("onConnectionStateChange DISCONNECTED")
                        isConnected = false
                        peripheralDisconnected("Peripheral Disconnected")
                    } else { /*reconnection goes here*/
                        disconnectCount++
                        if (isConnected) {
                            Timber.i("While retrying after gatt 133, calling to disconnect")
                            disconnect()
                        } else {
                            Timber.i("While retrying after gatt 133, calling to connect")
                            if (connectCallback != null && currentActivity != null) {
                                Timber.i("Gatt 133 error -> Got callback and trying again to connect -->")
                                connect(connectCallback!!, currentActivity, false)
                            }
                        }
                    }
                }
            } else {
                Timber.i("onConnectionStateChange DISCONNECTED")
                isConnected = false
                peripheralDisconnected("Peripheral Disconnected")
            }
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        super.onCharacteristicChanged(gatt, characteristic)
        Timber.i("onCharacteristicChanged %s", characteristic)
        val callback = notificationCallbacks[generateHashKey(characteristic)]
        callback?.sendSequentialResult(characteristic.value)
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, status)
        Timber.i("onCharacteristicRead %s", characteristic)
        synchronized(this) {
            if (readCallback != null) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    readCallback!!.success(characteristic.value)
                } else {
                    readCallback!!.error("Error reading " + characteristic.uuid + " status=" + status)
                }
                readCallback = null
            }
        }
        commandCompleted()
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        Timber.i("onCharacteristicWrite %s", characteristic)
        synchronized(this) {
            if (writeCallback != null) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeCallback!!.success()
                } else {
                    writeCallback!!.error(status)
                }
                writeCallback = null
            }
        }
        commandCompleted()
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        super.onDescriptorWrite(gatt, descriptor, status)
        Timber.i("onDescriptorWrite %s", descriptor)
        if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIGURATION_UUID) {
            val characteristic = descriptor.characteristic
            val key = generateHashKey(characteristic)
            val callback = notificationCallbacks[key]
            if (callback != null) {
                val success = callback.completeSubscription(status)
                if (!success) {
                    notificationCallbacks.remove(key)
                }
            }
        }
        commandCompleted()
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        super.onReadRemoteRssi(gatt, rssi, status)
        synchronized(this) {
            if (readCallback != null) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    updateRssi(rssi)
                    readCallback!!.success(rssi)
                } else {
                    readCallback!!.error("Error reading RSSI status=$status")
                }
                readCallback = null
            }
        }
        commandCompleted()
    }

    // Update rssi and scanRecord.
    fun update(rssi: Int, scanRecord: ByteArray?) {
        advertisingRSSI = rssi
        advertisingData = scanRecord
    }

    fun updateRssi(rssi: Int) {
        advertisingRSSI = rssi
    }

    // This seems way too complicated
    @SuppressLint("MissingPermission")
    private fun registerNotifyCallback(
        callbackContext: CallbackContext,
        serviceUUID: UUID,
        characteristicUUID: UUID
    ) {
        if (gatt == null) {
            callbackContext.error("BluetoothGatt is null")
            commandCompleted()
            return
        }
        val success = false
        val service = gatt!!.getService(serviceUUID)
        if (service == null) {
            callbackContext.error("Service $serviceUUID not found.")
            commandCompleted()
            return
        }
        val characteristic = findNotifyCharacteristic(service, characteristicUUID)
        if (characteristic == null) {
            callbackContext.error("Characteristic $characteristicUUID not found.")
            commandCompleted()
            return
        }
        val key = generateHashKey(serviceUUID, characteristic)
        notificationCallbacks[key] = SequentialCallbackContext(callbackContext)
        if (!gatt!!.setCharacteristicNotification(characteristic, true)) {
            callbackContext.error("Failed to register notification for $characteristicUUID")
            notificationCallbacks.remove(key)
            commandCompleted()
            return
        }

        // Why doesn't setCharacteristicNotification write the descriptor?
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID)
        if (descriptor == null) {
            callbackContext.error("Set notification failed for $characteristicUUID")
            notificationCallbacks.remove(key)
            commandCompleted()
            return
        }

        // prefer notify over indicate
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            LOG.w(
                TAG,
                "Characteristic %s does not have NOTIFY or INDICATE property set",
                characteristicUUID
            )
        }
        if (!gatt!!.writeDescriptor(descriptor)) {
            callbackContext.error("Failed to set client characteristic notification for $characteristicUUID")
            notificationCallbacks.remove(key)
            commandCompleted()
        }
    }

    @SuppressLint("MissingPermission")
    private fun removeNotifyCallback(
        callbackContext: CallbackContext,
        serviceUUID: UUID,
        characteristicUUID: UUID
    ) {
        if (gatt == null) {
            callbackContext.error("BluetoothGatt is null")
            commandCompleted()
            return
        }
        val service = gatt!!.getService(serviceUUID)
        if (service == null) {
            callbackContext.error("Service $serviceUUID not found.")
            commandCompleted()
            return
        }
        val characteristic = findNotifyCharacteristic(service, characteristicUUID)
        if (characteristic == null) {
            callbackContext.error("Characteristic $characteristicUUID not found.")
            commandCompleted()
            return
        }
        val key = generateHashKey(serviceUUID, characteristic)
        notificationCallbacks.remove(key)
        if (gatt!!.setCharacteristicNotification(characteristic, false)) {
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                gatt!!.writeDescriptor(descriptor)
            }
            callbackContext.success()
        } else {
            // TODO we can probably ignore and return success anyway since we removed the notification callback
            callbackContext.error("Failed to stop notification for $characteristicUUID")
        }
        commandCompleted()
    }

    // Some devices reuse UUIDs across characteristics, so we can't use service.getCharacteristic(characteristicUUID)
    // instead check the UUID and properties for each characteristic in the service until we find the best match
    // This function prefers Notify over Indicate
    private fun findNotifyCharacteristic(
        service: BluetoothGattService,
        characteristicUUID: UUID
    ): BluetoothGattCharacteristic? {
        var characteristic: BluetoothGattCharacteristic? = null

        // Check for Notify first
        val characteristics = service.characteristics
        for (c in characteristics) {
            if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 && characteristicUUID == c.uuid) {
                characteristic = c
                break
            }
        }
        if (characteristic != null) return characteristic

        // If there wasn't Notify Characteristic, check for Indicate
        for (c in characteristics) {
            if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 && characteristicUUID == c.uuid) {
                characteristic = c
                break
            }
        }

        // As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
        if (characteristic == null) {
            characteristic = service.getCharacteristic(characteristicUUID)
        }
        return characteristic
    }

    @SuppressLint("MissingPermission")
    private fun readCharacteristic(
        callbackContext: CallbackContext,
        serviceUUID: UUID,
        characteristicUUID: UUID
    ) {
        if (gatt == null) {
            callbackContext.error("BluetoothGatt is null")
            commandCompleted()
            return
        }
        val service = gatt!!.getService(serviceUUID)
        if (service == null) {
            callbackContext.error("Service $serviceUUID not found.")
            commandCompleted()
            return
        }
        val characteristic = findReadableCharacteristic(service, characteristicUUID)
        if (characteristic == null) {
            callbackContext.error("Characteristic $characteristicUUID not found.")
            commandCompleted()
            return
        }
        var success = false
        synchronized(this) {
            readCallback = callbackContext
            if (gatt!!.readCharacteristic(characteristic)) {
                success = true
            } else {
                readCallback = null
                callbackContext.error("Read failed")
            }
        }
        if (!success) {
            commandCompleted()
        }
    }

    @SuppressLint("MissingPermission")
    private fun readRSSI(callbackContext: CallbackContext) {
        if (gatt == null) {
            callbackContext.error("BluetoothGatt is null")
            commandCompleted()
            return
        }
        var success = false
        synchronized(this) {
            readCallback = callbackContext
            if (gatt!!.readRemoteRssi()) {
                success = true
            } else {
                readCallback = null
                callbackContext.error("Read RSSI failed")
            }
        }
        if (!success) {
            commandCompleted()
        }
    }

    // Some peripherals re-use UUIDs for multiple characteristics so we need to check the properties
    // and UUID of all characteristics instead of using service.getCharacteristic(characteristicUUID)
    private fun findReadableCharacteristic(
        service: BluetoothGattService,
        characteristicUUID: UUID
    ): BluetoothGattCharacteristic? {
        var characteristic: BluetoothGattCharacteristic? = null
        val read = BluetoothGattCharacteristic.PROPERTY_READ
        val characteristics = service.characteristics
        for (c in characteristics) {
            if (c.properties and read != 0 && characteristicUUID == c.uuid) {
                characteristic = c
                break
            }
        }

        // As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
        if (characteristic == null) {
            characteristic = service.getCharacteristic(characteristicUUID)
        }
        return characteristic
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        callbackContext: CallbackContext,
        serviceUUID: UUID,
        characteristicUUID: UUID,
        data: ByteArray,
        writeType: Int
    ) {
        if (gatt == null) {
            callbackContext.error("BluetoothGatt is null")
            commandCompleted()
            return
        }
        val service = gatt!!.getService(serviceUUID)
        if (service == null) {
            callbackContext.error("Service $serviceUUID not found.")
            commandCompleted()
            return
        }
        val characteristic = findWritableCharacteristic(service, characteristicUUID, writeType)
        if (characteristic == null) {
            callbackContext.error("Characteristic $characteristicUUID not found.")
            commandCompleted()
            return
        }
        var success = false
        characteristic.value = data
        characteristic.writeType = writeType
        synchronized(this) {
            writeCallback = callbackContext
            if (gatt!!.writeCharacteristic(characteristic)) {
                success = true
            } else {
                writeCallback = null
                callbackContext.error("Write failed")
            }
        }
        if (!success) {
            commandCompleted()
        }
    }

    // Some peripherals re-use UUIDs for multiple characteristics so we need to check the properties
    // and UUID of all characteristics instead of using service.getCharacteristic(characteristicUUID)
    private fun findWritableCharacteristic(
        service: BluetoothGattService,
        characteristicUUID: UUID,
        writeType: Int
    ): BluetoothGattCharacteristic? {
        var characteristic: BluetoothGattCharacteristic? = null

        // get write property
        var writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        }
        val characteristics = service.characteristics
        for (c in characteristics) {
            if (c.properties and writeProperty != 0 && characteristicUUID == c.uuid) {
                characteristic = c
                break
            }
        }

        // As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
        if (characteristic == null) {
            characteristic = service.getCharacteristic(characteristicUUID)
        }
        return characteristic
    }

    fun queueRead(
        callbackContext: CallbackContext?,
        serviceUUID: UUID?,
        characteristicUUID: UUID?
    ) {
        val command = BLECommand(
            callbackContext!!, serviceUUID, characteristicUUID, BLECommand.READ
        )
        queueCommand(command)
    }

    fun queueWrite(
        callbackContext: CallbackContext?,
        serviceUUID: UUID?,
        characteristicUUID: UUID?,
        data: ByteArray?,
        writeType: Int
    ) {
        val command = BLECommand(
            callbackContext!!, serviceUUID, characteristicUUID, data!!, writeType
        )
        queueCommand(command)
    }

    fun queueRegisterNotifyCallback(
        callbackContext: CallbackContext?,
        serviceUUID: UUID?,
        characteristicUUID: UUID?
    ) {
        val command = BLECommand(
            callbackContext!!, serviceUUID, characteristicUUID, BLECommand.REGISTER_NOTIFY
        )
        queueCommand(command)
    }

    fun queueRemoveNotifyCallback(
        callbackContext: CallbackContext?,
        serviceUUID: UUID?,
        characteristicUUID: UUID?
    ) {
        val command = BLECommand(
            callbackContext!!, serviceUUID, characteristicUUID, BLECommand.REMOVE_NOTIFY
        )
        queueCommand(command)
    }

    fun queueReadRSSI(callbackContext: CallbackContext?) {
        val command = BLECommand(callbackContext!!, null, null, BLECommand.READ_RSSI)
        queueCommand(command)
    }

    fun queueCleanup() {
        bleProcessing.set(true) // Stop anything else trying to process
        var command = commandQueue.poll()
        while (command != null) {
            command.callbackContext.error("Peripheral Disconnected")
            command = commandQueue.poll()
        }
        bleProcessing.set(false) // Now re-allow processing
        var contexts: Collection<L2CAPContext>
        synchronized(l2capContexts) {
            contexts =
                ArrayList(l2capContexts.values)
        }
        for (context in contexts) {
            context.disconnectL2Cap()
        }
    }

    fun writeL2CapChannel(callbackContext: CallbackContext?, psm: Int, data: ByteArray?) {
        Timber.i("L2CAP Write %s", psm)
        getOrAddL2CAPContext(psm).writeL2CapChannel(callbackContext!!, data)
    }

    private fun callbackCleanup() {
        synchronized(this) {
            if (readCallback != null) {
                readCallback!!.error(this.asJSONObject("Peripheral Disconnected"))
                readCallback = null
                commandCompleted()
            }
            if (writeCallback != null) {
                writeCallback!!.error(this.asJSONObject("Peripheral Disconnected"))
                writeCallback = null
                commandCompleted()
            }
        }
    }

    // add a new command to the queue
    private fun queueCommand(command: BLECommand) {
        Timber.i("Queuing Command %s", command)
        commandQueue.add(command)
        val result = PluginResult(PluginResult.Status.NO_RESULT)
        result.keepCallback = true
        command.callbackContext.sendPluginResult(result)
        processCommands()
    }

    // command finished, queue the next command
    private fun commandCompleted() {
        Timber.i("Processing Complete")
        bleProcessing.set(false)
        processCommands()
    }

    // process the queue
    private fun processCommands() {
        val canProcess = bleProcessing.compareAndSet(false, true)
        if (!canProcess) {
            return
        }
        Timber.i("Processing Commands")
        val command = commandQueue.poll()
        if (command != null) {
            if (command.type == BLECommand.READ) {
                Timber.i("Read %s", command.characteristicUUID)
                readCharacteristic(
                    command.callbackContext,
                    command.serviceUUID!!,
                    command.characteristicUUID!!
                )
            } else if (command.type == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                Timber.i("Write %s", command.characteristicUUID)
                writeCharacteristic(
                    command.callbackContext,
                    command.serviceUUID!!,
                    command.characteristicUUID!!,
                    command.data!!,
                    command.type
                )
            } else if (command.type == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                Timber.i("Write No Response %s", command.characteristicUUID)
                writeCharacteristic(
                    command.callbackContext,
                    command.serviceUUID!!,
                    command.characteristicUUID!!,
                    command.data!!,
                    command.type
                )
            } else if (command.type == BLECommand.REGISTER_NOTIFY) {
                Timber.i("Register Notify %s", command.characteristicUUID)
                registerNotifyCallback(
                    command.callbackContext,
                    command.serviceUUID!!,
                    command.characteristicUUID!!
                )
            } else if (command.type == BLECommand.REMOVE_NOTIFY) {
                Timber.i("Remove Notify %s", command.characteristicUUID)
                removeNotifyCallback(
                    command.callbackContext,
                    command.serviceUUID!!,
                    command.characteristicUUID!!
                )
            } else if (command.type == BLECommand.READ_RSSI) {
                Timber.i("Read RSSI")
                readRSSI(command.callbackContext)
            } else {
                // this shouldn't happen
                bleProcessing.set(false)
                throw RuntimeException("Unexpected BLE Command type " + command.type)
            }
        } else {
            bleProcessing.set(false)
            Timber.i("Command Queue is empty.")
        }
    }

    private fun generateHashKey(characteristic: BluetoothGattCharacteristic): String {
        return generateHashKey(characteristic.service.uuid, characteristic)
    }

    private fun generateHashKey(
        serviceUUID: UUID,
        characteristic: BluetoothGattCharacteristic
    ): String {
        return serviceUUID.toString() + "|" + characteristic.uuid + "|" + characteristic.instanceId
    }

    fun connectL2cap(callbackContext: CallbackContext?, psm: Int, secureChannel: Boolean) {
        getOrAddL2CAPContext(psm).connectL2cap(callbackContext!!, secureChannel)
    }

    fun disconnectL2Cap(callbackContext: CallbackContext, psm: Int) {
        var context: L2CAPContext?
        synchronized(l2capContexts) { context = l2capContexts[psm] }
        if (context != null) {
            context!!.disconnectL2Cap()
        }
        callbackContext.success()
    }

    fun isL2capConnected(psm: Int): Boolean {
        return getOrAddL2CAPContext(psm).isConnected
    }

    fun registerL2CapReceiver(callbackContext: CallbackContext?, psm: Int) {
        getOrAddL2CAPContext(psm).registerL2CapReceiver(callbackContext)
    }

    private fun getOrAddL2CAPContext(psm: Int): L2CAPContext {
        synchronized(l2capContexts) {
            var context = l2capContexts[psm]
            if (context == null) {
                context = L2CAPContext(device, psm)
                l2capContexts[psm] = context
            }
            return context
        }
    }

    companion object {
        // 0x2902 org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
        //public final static UUID CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
        val CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = UUIDHelper.uuidFromString("2902")
        private const val TAG = "Peripheral"
        private const val FAKE_PERIPHERAL_RSSI = 0x7FFFFFFF
        @Throws(JSONException::class)
        fun byteArrayToJSON(bytes: ByteArray?): JSONObject {
            val `object` = JSONObject()
            `object`.put("CDVType", "ArrayBuffer")
            `object`.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            return `object`
        }
    }
}
