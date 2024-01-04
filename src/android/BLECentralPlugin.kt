// (c) 2014-2016 Don Coleman
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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import androidx.annotation.RequiresApi
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaArgs
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.LOG
import org.apache.cordova.PermissionHelper
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.Hashtable
import java.util.UUID

class BLECentralPlugin : CordovaPlugin() {
    private val CONNECTION_PRIORITY_HIGH = "high"
    private val CONNECTION_PRIORITY_LOW = "low"
    private val CONNECTION_PRIORITY_BALANCED = "balanced"

    // callbacks
    var discoverCallback: CallbackContext? = null
    private var enableBluetoothCallback: CallbackContext? = null
    var bluetoothAdapter: BluetoothAdapter? = null

    // key is the MAC Address
    var peripherals: MutableMap<String?, Peripheral> = LinkedHashMap()

    // scan options
    var reportDuplicates = false
    private var permissionCallback: CallbackContext? = null
    private var deviceMacAddress: String? = null
    private var serviceUUIDs: Array<UUID>? = null
    private var scanSeconds = 0
    private var scanSettings: ScanSettings? = null
    private val stopScanHandler = Handler(Looper.getMainLooper())

    // Bluetooth state notification
    var stateCallback: CallbackContext? = null
    var stateReceiver: BroadcastReceiver? = null
    var bondStateCallback: CallbackContext? = null
    var bondStateReceiver: BroadcastReceiver? = null
    var bondedState = 0
    var device: BluetoothDevice? = null
    var bluetoothStates: Hashtable<Int?, String?> = object : Hashtable<Int?, String?>() {
        init {
            put(BluetoothAdapter.STATE_OFF, "off")
            put(BluetoothAdapter.STATE_TURNING_OFF, "turningOff")
            put(BluetoothAdapter.STATE_ON, "on")
            put(BluetoothAdapter.STATE_TURNING_ON, "turningOn")
        }
    }
    var bluetoothBondStates: Hashtable<Int?, String?> = object : Hashtable<Int?, String?>() {
        init {
            put(BluetoothDevice.BOND_NONE, "none")
            put(BluetoothDevice.BOND_BONDED, "bonded")
            put(BluetoothDevice.BOND_BONDING, "bonding")
        }
    }
    var locationStateCallback: CallbackContext? = null
    var locationStateReceiver: BroadcastReceiver? = null
    var pairingCallback: PairingCallback? = null

    interface PairingCallback {
        fun onPairingCompleted(device: BluetoothDevice?, bondedState: Int)
    }

    override fun pluginInitialize() {
        if (COMPILE_SDK_VERSION == -1) {
            val context = cordova.context
            COMPILE_SDK_VERSION = context.applicationContext.applicationInfo.targetSdkVersion
        }
    }

    override fun onDestroy() {
        removeStateListener()
        removeLocationStateListener()
        removeBondStateListener()
        for (peripheral in peripherals.values) {
            peripheral.disconnect()
        }
    }

    override fun onReset() {
        removeStateListener()
        removeLocationStateListener()
        removeBondStateListener()
        for (peripheral in peripherals.values) {
            peripheral.disconnect()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Throws(JSONException::class)
    override fun execute(
        action: String,
        args: CordovaArgs,
        callbackContext: CallbackContext
    ): Boolean {
        Timber.i("action = %s", action)
        if (bluetoothAdapter == null) {
            val activity: Activity = cordova.activity
            val hardwareSupportsBLE = activity.applicationContext
                .packageManager
                .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
                Build.VERSION.SDK_INT >= 18
            if (!hardwareSupportsBLE) {
                LOG.w(TAG, "This hardware does not support Bluetooth Low Energy.")
                callbackContext.error("This hardware does not support Bluetooth Low Energy.")
                return false
            }
            val bluetoothManager =
                activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager.adapter
        }
        var validAction = true
        if (action == SCAN) {
            val serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0))
            val scanSeconds = args.getInt(1)
            resetScanOptions()
            findLowEnergyDevices(callbackContext, serviceUUIDs, scanSeconds)
        } else if (action == START_SCAN) {
            val serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0))
            resetScanOptions()
            findLowEnergyDevices(callbackContext, serviceUUIDs, -1)
        } else if (action == STOP_SCAN) {
            stopScan()
            callbackContext.success()
        } else if (action == LIST) {
            listKnownDevices(callbackContext)
        } else if (action == CONNECT) {
            val macAddress = args.getString(0)
            connect(callbackContext, macAddress)
        } else if (action == AUTOCONNECT) {
            val macAddress = args.getString(0)
            autoConnect(callbackContext, macAddress)
        } else if (action == DISCONNECT) {
            val macAddress = args.getString(0)
            disconnect(callbackContext, macAddress)
        } else if (action == QUEUE_CLEANUP) {
            val macAddress = args.getString(0)
            queueCleanup(callbackContext, macAddress)
        } else if (action == SET_PIN) {
            val pin = args.getString(0)
            setPin(callbackContext, pin)
        } else if (action == REQUEST_MTU) {
            val macAddress = args.getString(0)
            val mtuValue = args.getInt(1)
            requestMtu(callbackContext, macAddress, mtuValue)
        } else if (action == REQUEST_CONNECTION_PRIORITY) {
            val macAddress = args.getString(0)
            val priority = args.getString(1)
            requestConnectionPriority(callbackContext, macAddress, priority)
        } else if (action == REFRESH_DEVICE_CACHE) {
            val macAddress = args.getString(0)
            val timeoutMillis = args.getLong(1)
            refreshDeviceCache(callbackContext, macAddress, timeoutMillis)
        } else if (action == READ) {
            val macAddress = args.getString(0)
            val serviceUUID = uuidFromString(args.getString(1))
            val characteristicUUID = uuidFromString(args.getString(2))
            read(callbackContext, macAddress, serviceUUID, characteristicUUID)
        } else if (action == READ_RSSI) {
            val macAddress = args.getString(0)
            readRSSI(callbackContext, macAddress)
        } else if (action == WRITE) {
            val macAddress = args.getString(0)
            val serviceUUID = uuidFromString(args.getString(1))
            val characteristicUUID = uuidFromString(args.getString(2))
            val data = args.getArrayBuffer(3)
            val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            write(callbackContext, macAddress, serviceUUID, characteristicUUID, data, type)
        } else if (action == WRITE_WITHOUT_RESPONSE) {
            val macAddress = args.getString(0)
            val serviceUUID = uuidFromString(args.getString(1))
            val characteristicUUID = uuidFromString(args.getString(2))
            val data = args.getArrayBuffer(3)
            val type = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            write(callbackContext, macAddress, serviceUUID, characteristicUUID, data, type)
        } else if (action == START_NOTIFICATION) {
            val macAddress = args.getString(0)
            val serviceUUID = uuidFromString(args.getString(1))
            val characteristicUUID = uuidFromString(args.getString(2))
            registerNotifyCallback(callbackContext, macAddress, serviceUUID, characteristicUUID)
        } else if (action == STOP_NOTIFICATION) {
            val macAddress = args.getString(0)
            val serviceUUID = uuidFromString(args.getString(1))
            val characteristicUUID = uuidFromString(args.getString(2))
            removeNotifyCallback(callbackContext, macAddress, serviceUUID, characteristicUUID)
        } else if (action == IS_ENABLED) {
            if (bluetoothAdapter!!.isEnabled) {
                callbackContext.success()
            } else {
                callbackContext.error("Bluetooth is disabled.")
            }
        } else if (action == IS_LOCATION_ENABLED) {
            if (locationServicesEnabled()) {
                callbackContext.success()
            } else {
                callbackContext.error("Location services disabled.")
            }
        } else if (action == IS_CONNECTED) {
            val macAddress = args.getString(0)
            if (peripherals.containsKey(macAddress) && peripherals[macAddress]!!.isConnected) {
                callbackContext.success()
            } else {
                callbackContext.error("Not connected")
            }
        } else if (action == SETTINGS) {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            cordova.activity.startActivity(intent)
            callbackContext.success()
        } else if (action == ENABLE) {
            enableBluetooth(callbackContext)
        } else if (action == START_STATE_NOTIFICATIONS) {
            if (stateCallback != null) {
                callbackContext.error("State callback already registered.")
            } else {
                stateCallback = callbackContext
                addStateListener()
                addBondStateListener()
                sendBluetoothStateChange(bluetoothAdapter!!.state)
            }
        } else if (action == STOP_STATE_NOTIFICATIONS) {
            if (stateCallback != null) {
                // Clear callback in JavaScript without actually calling it
                val result = PluginResult(PluginResult.Status.NO_RESULT)
                result.keepCallback = false
                stateCallback!!.sendPluginResult(result)
                stateCallback = null
            }
            removeStateListener()
            removeBondStateListener()
            callbackContext.success()
        } else if (action == START_LOCATION_STATE_NOTIFICATIONS) {
            if (locationStateCallback != null) {
                callbackContext.error("Location state callback already registered.")
            } else {
                locationStateCallback = callbackContext
                addLocationStateListener()
                sendLocationStateChange()
            }
        } else if (action == STOP_LOCATION_STATE_NOTIFICATIONS) {
            if (locationStateCallback != null) {
                // Clear callback in JavaScript without actually calling it
                val result = PluginResult(PluginResult.Status.NO_RESULT)
                result.keepCallback = false
                locationStateCallback!!.sendPluginResult(result)
                locationStateCallback = null
            }
            removeLocationStateListener()
            callbackContext.success()
        } else if (action == START_SCAN_WITH_OPTIONS) {
            val serviceUUIDs = parseServiceUUIDList(args.getJSONArray(0))
            val options = args.getJSONObject(1)
            resetScanOptions()
            reportDuplicates = options.optBoolean("reportDuplicates", false)
            val scanSettings = ScanSettings.Builder()
            when (options.optString("scanMode", "")) {
                "" -> {}
                "lowPower" -> scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                "balanced" -> scanSettings.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                "lowLatency" -> scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                "opportunistic" -> scanSettings.setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC)
                else -> {
                    callbackContext.error("scanMode must be one of: lowPower | balanced | lowLatency | opportunistic")
                    validAction = false
                }
            }
            when (options.optString("callbackType", "")) {
                "" -> {}
                "all" -> scanSettings.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                "first" -> scanSettings.setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                "lost" -> scanSettings.setCallbackType(ScanSettings.CALLBACK_TYPE_MATCH_LOST)
                else -> {
                    callbackContext.error("callbackType must be one of: all | first | lost")
                    validAction = false
                }
            }
            when (options.optString("matchMode", "")) {
                "" -> {}
                "aggressive" -> scanSettings.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                "sticky" -> scanSettings.setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                else -> {
                    callbackContext.error("matchMode must be one of: aggressive | sticky")
                    validAction = false
                }
            }
            when (options.optString("numOfMatches", "")) {
                "" -> {}
                "one" -> scanSettings.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                "few" -> scanSettings.setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
                "max" -> scanSettings.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                else -> {
                    callbackContext.error("numOfMatches must be one of: one | few | max")
                    validAction = false
                }
            }
            when (options.optString("phy", "")) {
                "" -> {}
                "1m" -> scanSettings.setPhy(BluetoothDevice.PHY_LE_1M)
                "coded" -> scanSettings.setPhy(BluetoothDevice.PHY_LE_CODED)
                "all" -> scanSettings.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                else -> {
                    callbackContext.error("phy must be one of: 1m | coded | all")
                    validAction = false
                }
            }
            if (validAction) {
                val LEGACY = "legacy"
                if (!options.isNull(LEGACY)) scanSettings.setLegacy(options.getBoolean(LEGACY))
                val reportDelay = options.optLong("reportDelay", -1)
                if (reportDelay >= 0L) scanSettings.setReportDelay(reportDelay)
                findLowEnergyDevices(callbackContext, serviceUUIDs, -1, scanSettings.build())
            }
        } else if (action == BONDED_DEVICES) {
            getBondedDevices(callbackContext)
        } else if (action == OPEN_L2CAP) {
            val macAddress = args.getString(0)
            val psm = args.getInt(1)
            val options = args.optJSONObject(2)
            val secureChannel = options != null && options.optBoolean("secureChannel", false)
            connectL2cap(callbackContext, macAddress, psm, secureChannel)
        } else if (action == CLOSE_L2CAP) {
            val macAddress = args.getString(0)
            val psm = args.getInt(1)
            disconnectL2cap(callbackContext, macAddress, psm)
        } else if (action == WRITE_L2CAP) {
            val macAddress = args.getString(0)
            val psm = args.getInt(1)
            val data = args.getArrayBuffer(2)
            writeL2cap(callbackContext, macAddress, psm, data)
        } else if (action == RECEIVE_L2CAP) {
            val macAddress = args.getString(0)
            val psm = args.getInt(1)
            registerL2CapReceiver(callbackContext, macAddress, psm)
        } else if (action == BLEEventListenerType.SET_EVENT_LISTENER.value) {
            BLEEventManager.sharedInstance.addEventListener { bluetoothEvent ->
                val result = PluginResult(PluginResult.Status.OK, bluetoothEvent.toJSON())
                result.keepCallback = true
                callbackContext.sendPluginResult(result)
            }
        } else if (action == BLEEventListenerType.REMOVE_EVENT_LISTENER.value) {
            BLEEventManager.sharedInstance.removeEventListener()
        } else if (action == BLEEventListenerType.WATCH.value) {
            val listenersArgument = args.getJSONObject(0)
            val listeners = mutableListOf<BluetoothWatchEndpoint>()
            //TODO: Assemble Listeners
            BLEEventManager.sharedInstance.watch(listeners)
        } else if (action == BLEEventListenerType.UNWATCH.value) {
            val listenersArgument = args.getJSONObject(0)
            val listeners = mutableListOf<BluetoothWatchEndpoint>()
            //TODO: Assemble Listeners
            BLEEventManager.sharedInstance.watch(listeners)
        } else {
            validAction = false
        }
        return validAction
    }

    private fun enableBluetooth(callbackContext: CallbackContext) {
        if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) {
            // https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#ACTION_REQUEST_ENABLE
            // Android 12+ requires BLUETOOTH_CONNECT in order to trigger an enable request
            if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
                permissionCallback = callbackContext
                PermissionHelper.requestPermission(
                    this,
                    REQUEST_ENABLE_BLUETOOTH,
                    BLUETOOTH_CONNECT
                )
                return
            }
        }
        enableBluetoothCallback = callbackContext
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        cordova.startActivityForResult(this, intent, REQUEST_ENABLE_BLUETOOTH)
    }

    @SuppressLint("MissingPermission")
    private fun getBondedDevices(callbackContext: CallbackContext) {
        if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
            if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
                permissionCallback = callbackContext
                PermissionHelper.requestPermission(
                    this,
                    REQUEST_GET_BONDED_DEVICES,
                    BLUETOOTH_CONNECT
                )
                return
            }
        }
        val bonded = JSONArray()
        val bondedDevices = bluetoothAdapter!!.bondedDevices
        for (device in bondedDevices) {
            device.bondState
            val type = device.type

            // just low energy devices (filters out classic and unknown devices)
            if (type == BluetoothDevice.DEVICE_TYPE_LE || type == BluetoothDevice.DEVICE_TYPE_DUAL) {
                val p = Peripheral(device)
                bonded.put(p.asJSONObject())
            }
        }
        callbackContext.success(bonded)
    }

    @Throws(JSONException::class)
    private fun parseServiceUUIDList(jsonArray: JSONArray): Array<UUID> {
        val serviceUUIDs: MutableList<UUID> = ArrayList()
        for (i in 0 until jsonArray.length()) {
            val uuidString = jsonArray.getString(i)
            serviceUUIDs.add(uuidFromString(uuidString))
        }
        return serviceUUIDs.toTypedArray()
    }

    @SuppressLint("MissingPermission")
    private fun onBluetoothStateChange(intent: Intent) {
        val action = intent.action
        Timber.i("onBluetoothStateChange$action")
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            sendBluetoothStateChange(state)
            if (state == BluetoothAdapter.STATE_OFF) {
                // #894 When Bluetooth is physically turned off the whole process might die, so the normal
                // onConnectionStateChange callbacks won't be invoked
                val bluetoothManager =
                    cordova.activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                for (peripheral in peripherals.values) {
                    if (!peripheral.isConnected) continue
                    val connectedState = bluetoothManager.getConnectionState(
                        peripheral.device,
                        BluetoothProfile.GATT
                    )
                    if (connectedState == BluetoothProfile.STATE_DISCONNECTED) {
                        peripheral.peripheralDisconnected("Bluetooth Disabled")
                    }
                }
            }
        } else if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            bondedState = device!!.bondState
            sendBluetoothBondStateChange(bondedState)
            if (pairingCallback != null) {
                pairingCallback!!.onPairingCompleted(device, bondedState)
            }
        }
    }

    private fun sendBluetoothStateChange(state: Int) {
        if (stateCallback != null) {
            val result = PluginResult(PluginResult.Status.OK, bluetoothStates[state])
            result.keepCallback = true
            stateCallback!!.sendPluginResult(result)
        }
    }

    private fun sendBluetoothBondStateChange(state: Int) {
        if (bondStateCallback != null) {
            val result = PluginResult(PluginResult.Status.OK, bluetoothBondStates[state])
            result.keepCallback = true
            bondStateCallback!!.sendPluginResult(result)
        }
    }

    private fun addStateListener() {
        if (stateReceiver == null) {
            stateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    onBluetoothStateChange(intent)
                }
            }
        }
        try {
            val intentFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            webView.context.registerReceiver(stateReceiver, intentFilter)
        } catch (e: Exception) {
            Timber.e("Error registering state receiver: %s", e.message)
        }
    }

    private fun addBondStateListener() {
        if (bondStateReceiver == null) {
            bondStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    onBluetoothStateChange(intent)
                }
            }
        }
        try {
            val intentFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            webView.context.registerReceiver(bondStateReceiver, intentFilter)
        } catch (e: Exception) {
            Timber.e("Error registering bond state receiver: %s", e.message)
        }
    }

    private fun removeBondStateListener() {
        if (bondStateReceiver != null) {
            try {
                webView.context.unregisterReceiver(bondStateReceiver)
            } catch (e: Exception) {
                Timber.e("Error unregistering bond state receiver: %s", e.message)
            }
        }
        bondStateCallback = null
        bondStateReceiver = null
    }

    private fun removeStateListener() {
        if (stateReceiver != null) {
            try {
                webView.context.unregisterReceiver(stateReceiver)
            } catch (e: Exception) {
                Timber.e("Error unregistering state receiver: %s", e.message)
            }
        }
        stateCallback = null
        stateReceiver = null
    }

    private fun onLocationStateChange(intent: Intent) {
        val action = intent.action
        if (LocationManager.PROVIDERS_CHANGED_ACTION == action) {
            sendLocationStateChange()
        }
    }

    private fun sendLocationStateChange() {
        if (locationStateCallback != null) {
            val result = PluginResult(PluginResult.Status.OK, locationServicesEnabled())
            result.keepCallback = true
            locationStateCallback!!.sendPluginResult(result)
        }
    }

    private fun addLocationStateListener() {
        if (locationStateReceiver == null) {
            locationStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    onLocationStateChange(intent)
                }
            }
        }
        try {
            val intentFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            intentFilter.addAction(Intent.ACTION_PROVIDER_CHANGED)
            webView.context.registerReceiver(locationStateReceiver, intentFilter)
        } catch (e: Exception) {
            Timber.e("Error registering location state receiver: %s", e.message)
        }
    }

    private fun removeLocationStateListener() {
        if (locationStateReceiver != null) {
            try {
                webView.context.unregisterReceiver(locationStateReceiver)
            } catch (e: Exception) {
                Timber.e("Error unregistering location state receiver: %s", e.message)
            }
        }
        locationStateCallback = null
        locationStateReceiver = null
    }

    private fun connect(callbackContext: CallbackContext, macAddress: String?) {
        if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
            if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
                permissionCallback = callbackContext
                deviceMacAddress = macAddress
                PermissionHelper.requestPermission(
                    this,
                    REQUEST_BLUETOOTH_CONNECT,
                    BLUETOOTH_CONNECT
                )
                return
            }
        }
        if (bluetoothAdapter!!.state != BluetoothAdapter.STATE_ON) {
            LOG.w(TAG, "Tried to connect while Bluetooth is disabled.")
            return
        }
        if (!peripherals.containsKey(macAddress) && BluetoothAdapter.checkBluetoothAddress(
                macAddress
            )
        ) {
            val device = bluetoothAdapter!!.getRemoteDevice(macAddress)
            val peripheral = Peripheral(device)
            peripherals[macAddress] = peripheral
        }
        val peripheral = peripherals[macAddress]
        if (peripheral != null) {
            // #894: BLE adapter state listener required so disconnect can be fired on BLE disabled
            addStateListener()
            addBondStateListener()
            peripheral.connect(callbackContext, cordova.activity, false)
        } else {
            callbackContext.error("Peripheral $macAddress not found.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun autoConnect(callbackContext: CallbackContext, macAddress: String?) {
        if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
            if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
                permissionCallback = callbackContext
                deviceMacAddress = macAddress
                PermissionHelper.requestPermission(
                    this,
                    REQUEST_BLUETOOTH_CONNECT_AUTO,
                    BLUETOOTH_CONNECT
                )
                return
            }
        }
        if (bluetoothAdapter!!.state != BluetoothAdapter.STATE_ON) {
            LOG.w(TAG, "Tried to connect while Bluetooth is disabled.")
            callbackContext.error("Bluetooth is disabled.")
            return
        }
        var peripheral = peripherals[macAddress]

        // allow auto-connect to connect to devices without scanning
        if (peripheral == null) {
            if (BluetoothAdapter.checkBluetoothAddress(macAddress)) {
                val device = bluetoothAdapter!!.getRemoteDevice(macAddress)
                Timber.i("Device Mac Address %s", device)
                Timber.i("Bond State %s", bondedState)
                peripheral = Peripheral(device)
                peripherals[device.address] = peripheral
            } else {
                callbackContext.error("$macAddress is not a valid MAC address.")
                return
            }
        }

        // #894: BLE adapter state listener required so disconnect can be fired on BLE disabled
        addStateListener()
        addBondStateListener()
        val pairedDevice: BluetoothDevice
        pairedDevice = bluetoothAdapter!!.getRemoteDevice(macAddress)
        if (COMPILE_SDK_VERSION >= 29 && Build.VERSION.SDK_INT >= 29 && (pairedDevice.name.contains(
                "UA-651"
            ) || pairedDevice.name.contains("UC-352")
                || pairedDevice.name.contains("IR20") || pairedDevice.name.contains("TAIDOC TD8255")
                || pairedDevice.name.contains("TD1107") || pairedDevice.name.contains("Nonin3230"))
        ) {
            Timber.i("Bond State for Version > 29" + peripheral.device.bondState)
            if (peripheral.device.bondState == BluetoothDevice.BOND_BONDED) {
                peripheral.connect(
                    callbackContext,
                    cordova.activity,
                    false
                ) // TODO setting this to false to stop auto connecting
            } else {
                val device = bluetoothAdapter!!.getRemoteDevice(macAddress)
                pairingCallback = object : PairingCallback {
                    override fun onPairingCompleted(btDevice: BluetoothDevice?, bondedState: Int) {
                        Timber.i("onPairingComplete Callback:$btDevice")
                        Timber.i("onPairingComplete Callback bond state:$bondedState")
                        if (bondedState == BluetoothDevice.BOND_BONDED) {
                            Timber.i("onPairingComplete Initiate GattConnect:$btDevice")
                            val peripheralDevice = Peripheral(btDevice!!)
                            peripheralDevice.connect(
                                callbackContext,
                                cordova.activity,
                                false
                            ) // TODO setting this to false to stop auto connecting
                        }
                    }
                }
                device.createBond()
            }
        } else {
            peripheral.connect(
                callbackContext,
                cordova.activity,
                false
            ) // TODO setting this to false to stop auto connecting
        }
    }

    private fun disconnect(callbackContext: CallbackContext, macAddress: String) {
        val peripheral = peripherals[macAddress]
        val device = bluetoothAdapter!!.getRemoteDevice(macAddress)
        if (peripheral != null) {
            peripheral.disconnect()
            try {
                val method = device!!.javaClass.getMethod("removeBond")
                method.invoke(device)
                Timber.i("Successfully removed bond")
            } catch (e: Exception) {
                Timber.e("ERROR: could not remove bond")
                e.printStackTrace()
            }
            callbackContext.success()
            // The below is for the admin screen only; cannot access Peripheral
        } else if (device != null) {
            try {
                val method = device.javaClass.getMethod("removeBond")
                method.invoke(device)
                Timber.i("Successfully removed bond from device")
            } catch (e: Exception) {
                Timber.e("ERROR: could not remove bond from device")
                e.printStackTrace()
            }
            callbackContext.success()
        } else {
            val message = "Peripheral $macAddress not found."
            LOG.w(TAG, message)
            callbackContext.error(message)
        }
    }

    private fun queueCleanup(callbackContext: CallbackContext, macAddress: String) {
        val peripheral = peripherals[macAddress]
        peripheral?.queueCleanup()
        callbackContext.success()
    }

    var broadCastReceiver: BroadcastReceiver? = null
    private fun setPin(callbackContext: CallbackContext, pin: String) {
        try {
            if (broadCastReceiver != null) {
                webView.context.unregisterReceiver(broadCastReceiver)
            }
            broadCastReceiver = object : BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action
                    if (BluetoothDevice.ACTION_PAIRING_REQUEST == action) {
                        val bluetoothDevice =
                            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        val type = intent.getIntExtra(
                            BluetoothDevice.EXTRA_PAIRING_VARIANT,
                            BluetoothDevice.ERROR
                        )
                        if (type == BluetoothDevice.PAIRING_VARIANT_PIN) {
                            bluetoothDevice!!.setPin(pin.toByteArray())
                            abortBroadcast()
                        }
                    }
                }
            }
            val intentFilter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
            intentFilter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            webView.context.registerReceiver(broadCastReceiver, intentFilter)
            callbackContext.success("OK")
        } catch (e: Exception) {
            callbackContext.error("Error: " + e.message)
            return
        }
    }

    private fun requestMtu(callbackContext: CallbackContext, macAddress: String, mtuValue: Int) {
        val peripheral = peripherals[macAddress]
        if (peripheral != null) {
            peripheral.requestMtu(callbackContext, mtuValue)
        } else {
            val message = "Peripheral $macAddress not found."
            LOG.w(TAG, message)
            callbackContext.error(message)
        }
    }

    private fun requestConnectionPriority(
        callbackContext: CallbackContext,
        macAddress: String,
        priority: String
    ) {
        val peripheral = peripherals[macAddress]
        if (peripheral == null) {
            callbackContext.error("Peripheral $macAddress not found.")
            return
        }
        if (!peripheral.isConnected) {
            callbackContext.error("Peripheral $macAddress is not connected.")
            return
        }
        var androidPriority = BluetoothGatt.CONNECTION_PRIORITY_BALANCED
        if (priority == CONNECTION_PRIORITY_LOW) {
            androidPriority = BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER
        } else if (priority == CONNECTION_PRIORITY_BALANCED) {
            androidPriority = BluetoothGatt.CONNECTION_PRIORITY_BALANCED
        } else if (priority == CONNECTION_PRIORITY_HIGH) {
            androidPriority = BluetoothGatt.CONNECTION_PRIORITY_HIGH
        }
        peripheral.requestConnectionPriority(androidPriority)
        callbackContext.success()
    }

    private fun refreshDeviceCache(
        callbackContext: CallbackContext,
        macAddress: String,
        timeoutMillis: Long
    ) {
        val peripheral = peripherals[macAddress]
        if (peripheral != null) {
            peripheral.refreshDeviceCache(callbackContext, timeoutMillis)
        } else {
            val message = "Peripheral $macAddress not found."
            LOG.w(TAG, message)
            callbackContext.error(message)
        }
    }

    private fun read(
        callbackContext: CallbackContext,
        macAddress: String,
        serviceUUID: UUID,
        characteristicUUID: UUID
    ) {
        val peripheral = peripherals[macAddress]
        if (peripheral == null) {
            callbackContext.error("Peripheral $macAddress not found.")
            return
        }
        if (!peripheral.isConnected) {
            callbackContext.error("Peripheral $macAddress is not connected.")
            return
        }

        //peripheral.readCharacteristic(callbackContext, serviceUUID, characteristicUUID);
        peripheral.queueRead(callbackContext, serviceUUID, characteristicUUID)
    }

    private fun readRSSI(callbackContext: CallbackContext, macAddress: String) {
        val peripheral = peripherals[macAddress]
        if (peripheral == null) {
            callbackContext.error("Peripheral $macAddress not found.")
            return
        }
        if (!peripheral.isConnected) {
            callbackContext.error("Peripheral $macAddress is not connected.")
            return
        }
        peripheral.queueReadRSSI(callbackContext)
    }

    private fun write(
        callbackContext: CallbackContext,
        macAddress: String,
        serviceUUID: UUID,
        characteristicUUID: UUID,
        data: ByteArray,
        writeType: Int
    ) {
        val peripheral = peripherals[macAddress]
        if (peripheral == null) {
            callbackContext.error("Peripheral $macAddress not found.")
            return
        }
        if (!peripheral.isConnected) {
            callbackContext.error("Peripheral $macAddress is not connected.")
            return
        }

        //peripheral.writeCharacteristic(callbackContext, serviceUUID, characteristicUUID, data, writeType);
        peripheral.queueWrite(callbackContext, serviceUUID, characteristicUUID, data, writeType)
    }

    private fun connectL2cap(
        callbackContext: CallbackContext,
        macAddress: String,
        psm: Int,
        secureChannel: Boolean
    ) {
        val peripheral = peripherals[macAddress]
        if (peripheral == null) {
            callbackContext.error("Peripheral $macAddress not found.")
            return
        }
        if (!peripheral.isConnected) {
            callbackContext.error("Peripheral $macAddress is not connected.")
            return
        }
        peripheral.connectL2cap(callbackContext, psm, secureChannel)
    }

    private fun disconnectL2cap(callbackContext: CallbackContext, macAddress: String, psm: Int) {
        val peripheral = peripherals[macAddress]
        peripheral?.disconnectL2Cap(callbackContext, psm)
        callbackContext.success()
    }

    private fun writeL2cap(
        callbackContext: CallbackContext,
        macAddress: String,
        psm: Int,
        data: ByteArray
    ) {
        val peripheral = peripherals[macAddress]
        if (peripheral == null) {
            callbackContext.error("Peripheral $macAddress not found.")
            return
        }
        if (!peripheral.isL2capConnected(psm)) {
            callbackContext.error("Peripheral $macAddress L2Cap is not connected.")
            return
        }
        cordova.threadPool.execute {
            peripheral.writeL2CapChannel(
                callbackContext,
                psm,
                data
            )
        }
    }

    private fun registerL2CapReceiver(
        callbackContext: CallbackContext,
        macAddress: String,
        psm: Int
    ) {
        val peripheral = peripherals[macAddress]
        if (peripheral == null) {
            callbackContext.error("Peripheral $macAddress not found.")
            return
        }
        peripheral.registerL2CapReceiver(callbackContext, psm)
    }

    private fun registerNotifyCallback(
        callbackContext: CallbackContext,
        macAddress: String,
        serviceUUID: UUID,
        characteristicUUID: UUID
    ) {
        val peripheral = peripherals[macAddress]
        if (peripheral != null) {
            if (!peripheral.isConnected) {
                callbackContext.error("Peripheral $macAddress is not connected.")
                return
            }

            //peripheral.setOnDataCallback(serviceUUID, characteristicUUID, callbackContext);
            peripheral.queueRegisterNotifyCallback(callbackContext, serviceUUID, characteristicUUID)
        } else {
            callbackContext.error("Peripheral $macAddress not found")
        }
    }

    private fun removeNotifyCallback(
        callbackContext: CallbackContext,
        macAddress: String,
        serviceUUID: UUID,
        characteristicUUID: UUID
    ) {
        val peripheral = peripherals[macAddress]
        if (peripheral != null) {
            if (!peripheral.isConnected) {
                callbackContext.error("Peripheral $macAddress is not connected.")
                return
            }
            peripheral.queueRemoveNotifyCallback(callbackContext, serviceUUID, characteristicUUID)
        } else {
            callbackContext.error("Peripheral $macAddress not found")
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            LOG.w(TAG, "Scan Result")
            super.onScanResult(callbackType, result)
            val device = result.device
            val address = device.address
            val alreadyReported = peripherals.containsKey(address) && !peripherals[address]!!
                .isUnscanned
            if (!alreadyReported) {
                val peripheral = Peripheral(
                    device, result.rssi, result.scanRecord!!
                        .bytes
                )
                peripherals[device.address] = peripheral
                if (discoverCallback != null) {
                    val pluginResult =
                        PluginResult(PluginResult.Status.OK, peripheral.asJSONObject())
                    pluginResult.keepCallback = true
                    discoverCallback!!.sendPluginResult(pluginResult)
                }
            } else {
                val peripheral = peripherals[address]
                if (peripheral != null) {
                    peripheral.update(result.rssi, result.scanRecord!!.bytes)
                    if (reportDuplicates && discoverCallback != null) {
                        val pluginResult =
                            PluginResult(PluginResult.Status.OK, peripheral.asJSONObject())
                        pluginResult.keepCallback = true
                        discoverCallback!!.sendPluginResult(pluginResult)
                    }
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Timber.i("Scan FAILED $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun findLowEnergyDevices(
        callbackContext: CallbackContext,
        serviceUUIDs: Array<UUID>?,
        scanSeconds: Int,
        scanSettings: ScanSettings? = ScanSettings.Builder().build()
    ) {
        if (!locationServicesEnabled() && Build.VERSION.SDK_INT < 31) {
            LOG.w(TAG, "Location Services are disabled")
        }
        val missingPermissions: MutableList<String> = ArrayList()
        if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
            if (!PermissionHelper.hasPermission(this, BLUETOOTH_SCAN)) {
                missingPermissions.add(BLUETOOTH_SCAN)
            }
            if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
                missingPermissions.add(BLUETOOTH_CONNECT)
            }
        } else if (COMPILE_SDK_VERSION >= 30 && Build.VERSION.SDK_INT >= 30) { // (API 30) Build.VERSION_CODES.R
            // Android 11 specifically requires FINE location access to be granted first before
            // the app is allowed to ask for ACCESS_BACKGROUND_LOCATION
            // Source: https://developer.android.com/about/versions/11/privacy/location
            if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                val accessBackgroundLocation =
                    preferences.getString("accessBackgroundLocation", "false")
                if (accessBackgroundLocation === "true" && !PermissionHelper.hasPermission(
                        this,
                        ACCESS_BACKGROUND_LOCATION
                    )
                ) {
                    LOG.w(TAG, "ACCESS_BACKGROUND_LOCATION is being requested")
                    missingPermissions.add(ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else if (COMPILE_SDK_VERSION >= 29 && Build.VERSION.SDK_INT >= 29) { // (API 29) Build.VERSION_CODES.Q
            if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            val accessBackgroundLocation =
                preferences.getString("accessBackgroundLocation", "false")
            if (accessBackgroundLocation === "true" && !PermissionHelper.hasPermission(
                    this,
                    ACCESS_BACKGROUND_LOCATION
                )
            ) {
                LOG.w(TAG, "ACCESS_BACKGROUND_LOCATION is being requested")
                missingPermissions.add(ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            if (!PermissionHelper.hasPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (missingPermissions.size > 0) {
            // save info so we can call this method again after permissions are granted
            permissionCallback = callbackContext
            this.serviceUUIDs = serviceUUIDs
            this.scanSeconds = scanSeconds
            this.scanSettings = scanSettings
            PermissionHelper.requestPermissions(
                this,
                REQUEST_BLUETOOTH_SCAN,
                missingPermissions.toTypedArray()
            )
            return
        }
        if (bluetoothAdapter!!.state != BluetoothAdapter.STATE_ON) {
            LOG.w(TAG, "Tried to start scan while Bluetooth is disabled.")
            callbackContext.error("Bluetooth is disabled.")
            return
        }

        // return error if already scanning
        if (bluetoothAdapter!!.isDiscovering) {
            LOG.w(TAG, "Tried to start scan while already running.")
            val jsonErrorObj = JSONObject()
            try {
                jsonErrorObj.put("scanErrorMsg", "Tried to start scan while already running.")
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            callbackContext.error(jsonErrorObj)
            return
        }

        // clear non-connected cached peripherals
        val iterator: MutableIterator<Map.Entry<String?, Peripheral>> =
            peripherals.entries.iterator()
        while (iterator.hasNext()) {
            val (_, peripheral) = iterator.next()
            val connecting = peripheral.isConnecting
            if (connecting) {
                Timber.i("Not removing connecting device: " + peripheral.device.address)
            }
            if (!peripheral.isConnected && !connecting) {
                iterator.remove()
            }
        }
        discoverCallback = callbackContext
        val bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        val filters: MutableList<ScanFilter> = ArrayList()
        if (serviceUUIDs != null && serviceUUIDs.size > 0) {
            for (uuid in serviceUUIDs) {
                val filter = ScanFilter.Builder().setServiceUuid(
                    ParcelUuid(uuid)
                ).build()
                filters.add(filter)
            }
        }
        stopScanHandler.removeCallbacks { stopScan() }
        bluetoothLeScanner.startScan(filters, scanSettings, leScanCallback)
        if (scanSeconds > 0) {
            stopScanHandler.postDelayed({ stopScan() }, (scanSeconds * 1000).toLong())
        }
        val result = PluginResult(PluginResult.Status.NO_RESULT)
        result.keepCallback = true
        callbackContext.sendPluginResult(result)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        stopScanHandler.removeCallbacks { stopScan() }
        if (bluetoothAdapter!!.state == BluetoothAdapter.STATE_ON) {
            Timber.i("Stopping Scan")
            try {
                val bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
                bluetoothLeScanner?.stopScan(leScanCallback)
                val json = JSONObject()
                try {
                    json.put("scanEnd", "scanEndSuccess")
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
                // Send the result indicating BLE scan is stopped now
                val pluginResult = PluginResult(PluginResult.Status.OK, json)
                pluginResult.keepCallback = true
                discoverCallback!!.sendPluginResult(pluginResult)
            } catch (e: Exception) {
                Timber.e("Exception stopping scan %s", e.message)
            }
        }
    }

    private fun locationServicesEnabled(): Boolean {
        var locationMode = 0
        try {
            locationMode = Settings.Secure.getInt(
                cordova.activity.contentResolver,
                Settings.Secure.LOCATION_MODE
            )
        } catch (e: SettingNotFoundException) {
            Timber.e("Location Mode Setting Not Found %s", e.message)
        }
        return locationMode > 0
    }

    private fun listKnownDevices(callbackContext: CallbackContext) {
        if (COMPILE_SDK_VERSION >= 31 && Build.VERSION.SDK_INT >= 31) { // (API 31) Build.VERSION_CODE.S
            if (!PermissionHelper.hasPermission(this, BLUETOOTH_CONNECT)) {
                permissionCallback = callbackContext
                PermissionHelper.requestPermission(
                    this,
                    REQUEST_LIST_KNOWN_DEVICES,
                    BLUETOOTH_CONNECT
                )
                return
            }
        }
        val json = JSONArray()

        // do we care about consistent order? will peripherals.values() be in order?
        for ((_, peripheral) in peripherals) {
            if (!peripheral.isUnscanned) {
                json.put(peripheral.asJSONObject())
            }
        }
        val result = PluginResult(PluginResult.Status.OK, json)
        callbackContext.sendPluginResult(result)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                Timber.i("User enabled Bluetooth")
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback!!.success()
                }
            } else {
                Timber.i("User did *NOT* enable Bluetooth")
                if (enableBluetoothCallback != null) {
                    enableBluetoothCallback!!.error("User did not enable Bluetooth")
                }
            }
            enableBluetoothCallback = null
        }
    }

    /* @Override */
    override fun onRequestPermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        val callback = popPermissionsCallback()
        if (callback == null) {
            if (grantResults.size > 0) {
                // There are some odd happenings if permission requests are made while booting up capacitor
                LOG.w(TAG, "onRequestPermissionResult received with no pending callback")
            }
            return
        }
        if (grantResults.size == 0) {
            callback.error("No permissions not granted.")
            return
        }

        //Android 12 (API 31) and higher
        // Users MUST accept BLUETOOTH_SCAN and BLUETOOTH_CONNECT
        // Android 10 (API 29) up to Android 11 (API 30)
        // Users MUST accept ACCESS_FINE_LOCATION
        // Users may accept or reject ACCESS_BACKGROUND_LOCATION
        // Android 9 (API 28) and lower
        // Users MUST accept ACCESS_COARSE_LOCATION
        for (i in permissions.indices) {
            if (permissions[i] == Manifest.permission.ACCESS_FINE_LOCATION && grantResults[i] == PackageManager.PERMISSION_DENIED) {
                Timber.i("User *rejected* Fine Location Access")
                callback.error("Location permission not granted.")
                return
            } else if (permissions[i] == Manifest.permission.ACCESS_COARSE_LOCATION && grantResults[i] == PackageManager.PERMISSION_DENIED) {
                Timber.i("User *rejected* Coarse Location Access")
                callback.error("Location permission not granted.")
                return
            } else if (permissions[i] == BLUETOOTH_SCAN && grantResults[i] == PackageManager.PERMISSION_DENIED) {
                Timber.i("User *rejected* Bluetooth_Scan Access")
                callback.error("Bluetooth scan permission not granted.")
                return
            } else if (permissions[i] == BLUETOOTH_CONNECT && grantResults[i] == PackageManager.PERMISSION_DENIED) {
                Timber.i("User *rejected* Bluetooth_Connect Access")
                callback.error("Bluetooth Connect permission not granted.")
                return
            }
        }
        when (requestCode) {
            REQUEST_ENABLE_BLUETOOTH -> {
                Timber.i("User granted Bluetooth Connect access for enable bluetooth")
                enableBluetooth(callback)
            }

            REQUEST_BLUETOOTH_SCAN -> {
                Timber.i("User granted Bluetooth Scan Access")
                findLowEnergyDevices(callback, serviceUUIDs, scanSeconds, scanSettings)
                serviceUUIDs = null
                scanSeconds = -1
                scanSettings = null
            }

            REQUEST_BLUETOOTH_CONNECT -> {
                Timber.i("User granted Bluetooth Connect Access")
                connect(callback, deviceMacAddress)
                deviceMacAddress = null
            }

            REQUEST_BLUETOOTH_CONNECT_AUTO -> {
                Timber.i("User granted Bluetooth Auto Connect Access")
                autoConnect(callback, deviceMacAddress)
                deviceMacAddress = null
            }

            REQUEST_GET_BONDED_DEVICES -> {
                Timber.i("User granted permissions for bonded devices")
                getBondedDevices(callback)
            }

            REQUEST_LIST_KNOWN_DEVICES -> {
                Timber.i("User granted permissions for list known devices")
                listKnownDevices(callback)
            }
        }
    }

    private fun popPermissionsCallback(): CallbackContext? {
        val callback = permissionCallback
        permissionCallback = null
        return callback
    }

    private fun uuidFromString(uuid: String): UUID {
        return UUIDHelper.uuidFromString(uuid)
    }

    /**
     * Reset the BLE scanning options
     */
    private fun resetScanOptions() {
        reportDuplicates = false
    }

    companion object {
        // permissions
        private const val ACCESS_BACKGROUND_LOCATION =
            "android.permission.ACCESS_BACKGROUND_LOCATION" // API 29
        private const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT" // API 31
        private const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN" // API 31

        // actions
        private const val SCAN = "scan"
        private const val START_SCAN = "startScan"
        private const val STOP_SCAN = "stopScan"
        private const val START_SCAN_WITH_OPTIONS = "startScanWithOptions"
        private const val BONDED_DEVICES = "bondedDevices"
        private const val LIST = "list"
        private const val CONNECT = "connect"
        private const val AUTOCONNECT = "autoConnect"
        private const val DISCONNECT = "disconnect"
        private const val QUEUE_CLEANUP = "queueCleanup"
        private const val SET_PIN = "setPin"
        private const val REQUEST_MTU = "requestMtu"
        private const val REQUEST_CONNECTION_PRIORITY = "requestConnectionPriority"
        private const val REFRESH_DEVICE_CACHE = "refreshDeviceCache"
        private const val READ = "read"
        private const val WRITE = "write"
        private const val WRITE_WITHOUT_RESPONSE = "writeWithoutResponse"
        private const val READ_RSSI = "readRSSI"
        private const val START_NOTIFICATION =
            "startNotification" // register for characteristic notification
        private const val STOP_NOTIFICATION =
            "stopNotification" // remove characteristic notification
        private const val IS_ENABLED = "isEnabled"
        private const val IS_LOCATION_ENABLED = "isLocationEnabled"
        private const val IS_CONNECTED = "isConnected"
        private const val SETTINGS = "showBluetoothSettings"
        private const val ENABLE = "enable"
        private const val START_STATE_NOTIFICATIONS = "startStateNotifications"
        private const val STOP_STATE_NOTIFICATIONS = "stopStateNotifications"
        private const val OPEN_L2CAP = "openL2Cap"
        private const val CLOSE_L2CAP = "closeL2Cap"
        private const val RECEIVE_L2CAP = "receiveDataL2Cap"
        private const val WRITE_L2CAP = "writeL2Cap"
        private const val START_LOCATION_STATE_NOTIFICATIONS = "startLocationStateNotifications"
        private const val STOP_LOCATION_STATE_NOTIFICATIONS = "stopLocationStateNotifications"
        private const val TAG = "BLEPlugin"
        private const val REQUEST_ENABLE_BLUETOOTH = 1
        private const val REQUEST_BLUETOOTH_SCAN = 2
        private const val REQUEST_BLUETOOTH_CONNECT = 3
        private const val REQUEST_BLUETOOTH_CONNECT_AUTO = 4
        private const val REQUEST_GET_BONDED_DEVICES = 5
        private const val REQUEST_LIST_KNOWN_DEVICES = 6
        private var COMPILE_SDK_VERSION = -1
    }
}
