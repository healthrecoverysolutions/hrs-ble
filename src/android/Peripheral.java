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

package com.megster.cordova.ble.central;

import android.app.Activity;

import android.bluetooth.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.google.firebase.analytics.FirebaseAnalytics;

import timber.log.Timber;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Peripheral wraps the BluetoothDevice and provides methods to convert to JSON.
 */
public class Peripheral extends BluetoothGattCallback {

    // 0x2902 org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
    //public final static UUID CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
    public final static UUID CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = UUIDHelper.uuidFromString("2902");
    private static final String TAG = "Peripheral";

    private static final int FAKE_PERIPHERAL_RSSI = 0x7FFFFFFF;

    private BluetoothDevice device;
    private byte[] advertisingData;
    public int advertisingRSSI;
    private boolean autoconnect = false;
    private boolean connected = false;
    private boolean connecting = false;
    private ConcurrentLinkedQueue<BLECommand> commandQueue = new ConcurrentLinkedQueue<BLECommand>();
    private final Map<Integer, L2CAPContext> l2capContexts = new HashMap<Integer, L2CAPContext>();
    private final AtomicBoolean bleProcessing = new AtomicBoolean();

    BluetoothGatt gatt;

    private CallbackContext connectCallback;
    private CallbackContext refreshCallback;
    private CallbackContext readCallback;
    private CallbackContext writeCallback;
    private CallbackContext requestMtuCallback;
    private Activity currentActivity;
    private int disconnectCount = 0;

    private Map<String, SequentialCallbackContext> notificationCallbacks = new HashMap<String, SequentialCallbackContext>();

    private FirebaseAnalytics mFirebaseAnalytics;

    public Peripheral(BluetoothDevice device, FirebaseAnalytics firebaseAnalytics) {

        Timber.i("Creating un-scanned peripheral entry for address: %s", device.getAddress());

        this.mFirebaseAnalytics = firebaseAnalytics;
        this.device = device;
        this.advertisingRSSI = FAKE_PERIPHERAL_RSSI;
        this.advertisingData = null;

    }

    public Peripheral(BluetoothDevice device, int advertisingRSSI, byte[] scanRecord, FirebaseAnalytics firebaseAnalytics) {

        this.mFirebaseAnalytics = firebaseAnalytics;
        this.device = device;
        this.advertisingRSSI = advertisingRSSI;
        this.advertisingData = scanRecord;

    }

    private void gattConnect() {

        closeGatt();
        connected = false;
        connecting = true;
        queueCleanup();
        callbackCleanup();

        BluetoothDevice device = getDevice();
        if (Build.VERSION.SDK_INT < 23) {
            gatt = device.connectGatt(currentActivity, autoconnect, this);
        } else {
            gatt = device.connectGatt(currentActivity, autoconnect, this, BluetoothDevice.TRANSPORT_LE);
        }

    }

    public void connect(CallbackContext callbackContext, Activity activity, boolean auto) {
        currentActivity = activity;
        autoconnect = auto;
        connectCallback = callbackContext;
        if (refreshCallback != null) {
            refreshCallback.error(this.asJSONObject("refreshDeviceCache aborted due to new connect call"));
            refreshCallback = null;
        }

        gattConnect();

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    // the app requested the central disconnect from the peripheral
    // disconnect the gatt, do not call connectCallback.error
    public void disconnect() {
        connected = false;
        connecting = false;
        autoconnect = false;

        closeGatt();
        queueCleanup();
        callbackCleanup();
    }

    // the peripheral disconnected
    // always call connectCallback.error to notify the app
    public void peripheralDisconnected(String message) {
        Timber.i( "Peripheral disconnected " + message);
        connected = false;
        connecting = false;

        // don't remove the gatt for autoconnect
        if (!autoconnect) {
            closeGatt();
        }

        sendDisconnectMessage(message);

        queueCleanup();
        callbackCleanup();
    }

    private void closeGatt() {
        Timber.i( "Close Gatt");
        BluetoothGatt localGatt;
        synchronized (this) {
            localGatt = this.gatt;
            this.gatt = null;
        }
        if (localGatt != null) {
            localGatt.disconnect();
            localGatt.close();
        }
    }

    // notify the phone that the peripheral disconnected
    private void sendDisconnectMessage(String messageContent) {
        if (connectCallback != null) {
            JSONObject message = this.asJSONObject(messageContent);
            if (autoconnect) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, message);
                result.setKeepCallback(true);
                connectCallback.sendPluginResult(result);
            } else {
                connectCallback.error(message);
                connectCallback = null;
            }
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
        Timber.i("mtu=%d, status=%d", mtu, status);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            requestMtuCallback.success(mtu);
        } else {
            requestMtuCallback.error("MTU request failed");
        }
        requestMtuCallback = null;
    }

    public void requestMtu(CallbackContext callback, int mtuValue) {
        Timber.i("requestMtu mtu=%d", mtuValue);
        if (gatt == null) {
            callback.error("No GATT");
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            callback.error("Android version does not support requestMtu");
            return;
        }

        if (gatt.requestMtu(mtuValue)) {
            requestMtuCallback = callback;
        } else {
            callback.error("Could not initiate MTU request");
        }
    }

    public void requestConnectionPriority(int priority) {
        if (gatt != null) {
            Timber.i("requestConnectionPriority priority=%s", priority);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                gatt.requestConnectionPriority(priority);
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
    public void refreshDeviceCache(CallbackContext callback, final long timeoutMillis) {
        Timber.i("refreshDeviceCache");

        boolean success = false;
        if (gatt != null) {
            try {
                final Method refresh = gatt.getClass().getMethod("refresh");
                if (refresh != null) {
                    success = (Boolean)refresh.invoke(gatt);
                    if (success) {
                        this.refreshCallback = callback;
                        Handler handler = new Handler();
                        Timber.i("Waiting " + timeoutMillis + " milliseconds before discovering services");
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (gatt != null) {
                                    try {
                                        gatt.discoverServices();
                                    } catch(Exception e) {
                                        Timber.e("refreshDeviceCache Failed after delay %s", e.getMessage());
                                    }
                                }
                            }
                        }, timeoutMillis);
                    }
                } else {
                    LOG.w(TAG, "Refresh method not found on gatt");
                }
            } catch(Exception e) {
                Timber.e("refreshDeviceCache Failed %s", e.getMessage());
            }
        }

        if (!success) {
            callback.error("Service refresh failed");
        }
    }

    public boolean isUnscanned() {
        return advertisingData == null;
    }

    public JSONObject asJSONObject()  {

        JSONObject json = new JSONObject();

        try {
            json.put("name", device.getName());
            json.put("id", device.getAddress()); // mac address
            if (advertisingData != null) {
                json.put("advertising", byteArrayToJSON(advertisingData));
            }
            // TODO real RSSI if we have it, else
            if (advertisingRSSI != FAKE_PERIPHERAL_RSSI) {
                json.put("rssi", advertisingRSSI);
            }
        } catch (JSONException e) { // this shouldn't happen
            e.printStackTrace();
        }

        return json;
    }

    public JSONObject asJSONObject(String errorMessage)  {

        JSONObject json = new JSONObject();

        try {
            json.put("name", device.getName());
            json.put("id", device.getAddress()); // mac address
            json.put("errorMessage", errorMessage);
        } catch (JSONException e) { // this shouldn't happen
            e.printStackTrace();
        }

        return json;
    }

    public JSONObject asJSONObject(BluetoothGatt gatt) {

        JSONObject json = asJSONObject();

        try {
            JSONArray servicesArray = new JSONArray();
            JSONArray characteristicsArray = new JSONArray();
            json.put("services", servicesArray);
            json.put("characteristics", characteristicsArray);

            if (connected && gatt != null) {
                for (BluetoothGattService service : gatt.getServices()) {
                    servicesArray.put(UUIDHelper.uuidToString(service.getUuid()));

                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        JSONObject characteristicsJSON = new JSONObject();
                        characteristicsArray.put(characteristicsJSON);

                        characteristicsJSON.put("service", UUIDHelper.uuidToString(service.getUuid()));
                        characteristicsJSON.put("characteristic", UUIDHelper.uuidToString(characteristic.getUuid()));
                        //characteristicsJSON.put("instanceId", characteristic.getInstanceId());

                        characteristicsJSON.put("properties", Helper.decodeProperties(characteristic));
                        // characteristicsJSON.put("propertiesValue", characteristic.getProperties());

                        if (characteristic.getPermissions() > 0) {
                            characteristicsJSON.put("permissions", Helper.decodePermissions(characteristic));
                            // characteristicsJSON.put("permissionsValue", characteristic.getPermissions());
                        }

                        JSONArray descriptorsArray = new JSONArray();

                        for (BluetoothGattDescriptor descriptor: characteristic.getDescriptors()) {
                            JSONObject descriptorJSON = new JSONObject();
                            descriptorJSON.put("uuid", UUIDHelper.uuidToString(descriptor.getUuid()));
                            descriptorJSON.put("value", descriptor.getValue()); // always blank

                            if (descriptor.getPermissions() > 0) {
                                descriptorJSON.put("permissions", Helper.decodePermissions(descriptor));
                                // descriptorJSON.put("permissionsValue", descriptor.getPermissions());
                            }
                            descriptorsArray.put(descriptorJSON);
                        }
                        if (descriptorsArray.length() > 0) {
                            characteristicsJSON.put("descriptors", descriptorsArray);
                        }
                    }
                }
            }
        } catch (JSONException e) { // TODO better error handling
            e.printStackTrace();
        }

        return json;
    }

    static JSONObject byteArrayToJSON(byte[] bytes) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("CDVType", "ArrayBuffer");
        object.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
        return object;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isConnecting() {
        return connecting;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);

        // refreshCallback is a kludge for refreshing services, if it exists, it temporarily
        // overrides the connect callback. Unfortunately this edge case make the code confusing.

        if (status == BluetoothGatt.GATT_SUCCESS) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, this.asJSONObject(gatt));
            result.setKeepCallback(true);
            if (refreshCallback != null) {
                refreshCallback.sendPluginResult(result);
                refreshCallback = null;
            } else if (connectCallback != null) {
                connectCallback.sendPluginResult(result);
            }
        } else {
            Timber.e("Service discovery failed. status = %d", status);
            if (refreshCallback != null) {
                refreshCallback.error(this.asJSONObject("Service discovery failed"));
                refreshCallback = null;
            }

            peripheralDisconnected("Service discovery failed");
        }
    }

    /**
     * This enum lists all the BLE devices which face issues while we perform a retry after disconnection due to gatt error code (133)
     */
    public enum DEVICES_TO_ESCAPE_RETRY {
        NO_DEVICE("No device"); // Currently we have allowed to retry for all the supported devices. In future, if there are any exceptions, we can add those devices here

        private String text;

        DEVICES_TO_ESCAPE_RETRY(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }

        public static boolean notMatches(BluetoothDevice device) {
            if(device!=null) {
                String text = device.getName();
                if (text != null ) {
                    for (DEVICES_TO_ESCAPE_RETRY b : DEVICES_TO_ESCAPE_RETRY.values()) {
                        if (!text.equals(b.text) || !text.contains(b.text)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private String logConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        String log = "";
        log += "** Bluetooth ConnectionStateChangeFor Device " + getGattDeviceName(gatt);
        log += " Updated bluetooth connection state is " + newState + " ";
        switch (newState) {
            case BluetoothGatt.STATE_CONNECTED:
                log += "STATE_CONNECTED";
                break;
            case BluetoothGatt.STATE_CONNECTING:
                log += "STATE_CONNECTING";
                break;
            case BluetoothGatt.STATE_DISCONNECTED:
                log += "STATE_DISCONNECTED";
                break;
            case BluetoothGatt.STATE_DISCONNECTING:
                log += "STATE_DISCONNECTING";
                break;
        }
        log += " and Status is " + status + " "; // there are many other states which can be apart from success, thus we are also printing the int value
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                log += "GATT_SUCCESS ";
                break;
        }
        return log;
    }

    private String getGattDeviceName(BluetoothGatt gatt) {
        String deviceName = "";
        if (gatt != null && gatt.getDevice() != null) {
            deviceName = gatt.getDevice().getName();
        }
        return deviceName;
    }

    private BluetoothDevice getGattDevice(BluetoothGatt gatt) {
        BluetoothDevice device = this.device; // a fallback
        if (gatt != null && gatt.getDevice() != null) {
            device = gatt.getDevice();
        }
        return device;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        // status : Status of the connect or disconnect operation
        // newState : Returns the new connection state. Can be one of BluetoothProfile.STATE_DISCONNECTED or BluetoothProfile#STATE_CONNECTED
        this.gatt = gatt;
        Timber.i(logConnectionStateChange(gatt, status, newState));
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            Timber.i("onConnectionStateChange CONNECTED " + getGattDeviceName(gatt));
            connected = true;
            connecting = false;
            gatt.discoverServices();
            // Firebase analytics connect event
            Bundle bundle = getFirebaseInfoBundle("CONNECTED");
            mFirebaseAnalytics.logEvent(BTAnalyticsLogTypes.BT_CONNECTION.toString(), bundle);
        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {  // Disconnected
            Timber.i("onConnectionStateChange STATE_DISCONNECTED " + getGattDeviceName(gatt));

            // Firebase disconnect event
            Bundle bundle = getFirebaseInfoBundle("DISCONNECTED");
            bundle.putInt("ERROR_CODE", status); // disconnection status code
            mFirebaseAnalytics.logEvent(BTAnalyticsLogTypes.BT_CONNECTION.toString(), bundle);

            if(DEVICES_TO_ESCAPE_RETRY.notMatches(gatt.getDevice())) { // Retry is allowed for the device
                // Will retry to connect for two times after we receive Gatt status code 133
                if (status != 133 || disconnectCount >= 2) { /*If more then 2 count gatt close process*/
                    disconnectCount = 0;
                    connected = false;
                    peripheralDisconnected("Peripheral Disconnected");
                } else { /*reconnection goes here*/
                    disconnectCount++;
                    if (connected) {
                        Timber.i("Reconnection attempt for device " + getGattDeviceName(gatt) + " after gatt status code : " + status);
                        disconnect();
                    } else {
                        Timber.i("Gatt status code " + status + " for device " + getGattDeviceName(gatt) );
                        final Handler handler = new Handler(Looper.getMainLooper());
                        handler.postDelayed(() -> {
                            if (connectCallback != null && currentActivity != null) {
                                Timber.i("Got callback, Will Retry connection after 100ms");
                                connect(connectCallback, currentActivity, false); // it is recommended to retry after 100ms in case of gatt 133 connection issue
                            }
                        }, 100);

                    }
                }
            } else {
                Timber.i("onConnectionStateChange DISCONNECTED for " + getGattDeviceName(gatt));
                connected = false;
                peripheralDisconnected("Peripheral Disconnected");
            }
        } else {
            Timber.i("onConnectionStateChange else part! " + newState + " " + getGattDeviceName(gatt));
        }
    }

    private String isDevicePaired() {
        Timber.i("Bond state -> " + this.getGattDevice(gatt).getBondState());
        if (NATIVE_PAIRING_NOT_SUPPORTED_DEVICES.matches(this.getGattDevice(gatt))) {
            Timber.i("This device does not support native pairing " + this.getGattDevice(gatt).getName());
            return "NA";
        }else {
            Timber.i("isPaired -> " + (new Integer(this.getGattDevice(gatt).getBondState()).equals(new Integer(BluetoothDevice.BOND_BONDED))));
            return "" + (new Integer(this.getGattDevice(gatt).getBondState()).equals(new Integer(BluetoothDevice.BOND_BONDED)));
        }

    }

    /**
     * This enum will contain devices for which cannot be natively paired
     */
    enum NATIVE_PAIRING_NOT_SUPPORTED_DEVICES {
        WELCH_SC100("SC100"),
        WELCH_BP100("BP100"),
        FORAIR20("IR20");

        private String text;

        NATIVE_PAIRING_NOT_SUPPORTED_DEVICES(String text) {
            this.text = text;
        }

        public String getText() {
            return this.text;
        }

        public static boolean matches(BluetoothDevice device) {
            if(device!=null) {
                String text = device.getName();
                if (text != null ) {
                    for (NATIVE_PAIRING_NOT_SUPPORTED_DEVICES b : NATIVE_PAIRING_NOT_SUPPORTED_DEVICES.values()) {
                        if (text.equals(b.text) || text.contains(b.text)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private Bundle getFirebaseInfoBundle(String isConnected) {
        Bundle bundle = new Bundle();
        SupportedPeripherals templateDevice = SupportedPeripherals.findMatchingDevice(this.device);
        if(templateDevice!=null) {
            Timber.i(templateDevice.getDisplay());
            Timber.i(templateDevice.getPeripheralType());
            bundle.putString("DEVICE_NAME", templateDevice.getDisplay());
            bundle.putString("PERIPHERAL_TYPE", templateDevice.getPeripheralType());
        } else {
            bundle.putString("DEVICE_NAME", this.device.getName());
        }
        bundle.putString("STATE", isConnected);
        bundle.putString("PAIRING_STATE", isDevicePaired());
        if (advertisingRSSI != FAKE_PERIPHERAL_RSSI) {
            bundle.putString("BT_RSSI", Integer.toString(this.advertisingRSSI)); // TODO
        }
        return bundle;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        Timber.i("onCharacteristicChanged %s", characteristic);

        SequentialCallbackContext callback = notificationCallbacks.get(generateHashKey(characteristic));

        if (callback != null) {
            callback.sendSequentialResult(characteristic.getValue());
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        Timber.i("onCharacteristicRead %s", characteristic);

        synchronized(this) {
            if (readCallback != null) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    readCallback.success(characteristic.getValue());
                } else {
                    readCallback.error("Error reading " + characteristic.getUuid() + " status=" + status);
                }

                readCallback = null;
            }
        }

        commandCompleted();
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        Timber.i("onCharacteristicWrite %s", characteristic);

        synchronized(this) {
            if (writeCallback != null) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeCallback.success();
                } else {
                    writeCallback.error(status);
                }

                writeCallback = null;
            }
        }

        commandCompleted();
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        Timber.i("onDescriptorWrite %s", descriptor);
        if (descriptor.getUuid().equals(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID)) {
            BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
            String key = generateHashKey(characteristic);
            SequentialCallbackContext callback = notificationCallbacks.get(key);

            if (callback != null) {
                boolean success = callback.completeSubscription(status);
                if (!success) {
                    notificationCallbacks.remove(key);
                }
            }
        }
        commandCompleted();
    }


    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
        synchronized(this) {
            if (readCallback != null) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    updateRssi(rssi);
                    readCallback.success(rssi);
                } else {
                    readCallback.error("Error reading RSSI status=" + status);
                }

                readCallback = null;
            }
        }
        commandCompleted();
    }

    // Update rssi and scanRecord.
    public void update(int rssi, byte[] scanRecord) {
        this.advertisingRSSI = rssi;
        this.advertisingData = scanRecord;
    }

    public void updateRssi(int rssi) {
        advertisingRSSI = rssi;
    }

    // This seems way too complicated
    private void registerNotifyCallback(CallbackContext callbackContext, UUID serviceUUID, UUID characteristicUUID) {

        if (gatt == null) {
            callbackContext.error("BluetoothGatt is null");
            commandCompleted();
            return;
        }

        boolean success = false;

        BluetoothGattService service = gatt.getService(serviceUUID);

        if (service == null) {
            callbackContext.error("Service " + serviceUUID + " not found.");
            commandCompleted();
            return;
        }

        BluetoothGattCharacteristic characteristic = findNotifyCharacteristic(service, characteristicUUID);

        if (characteristic == null) {
            callbackContext.error("Characteristic " + characteristicUUID + " not found.");
            commandCompleted();
            return;
        }

        String key = generateHashKey(serviceUUID, characteristic);

        notificationCallbacks.put(key, new SequentialCallbackContext(callbackContext));

        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            callbackContext.error("Failed to register notification for " + characteristicUUID);
            notificationCallbacks.remove(key);
            commandCompleted();
            return;
        }

        // Why doesn't setCharacteristicNotification write the descriptor?
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID);
        if (descriptor == null) {
            callbackContext.error("Set notification failed for " + characteristicUUID);
            notificationCallbacks.remove(key);
            commandCompleted();
            return;
        }

        // prefer notify over indicate
        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        } else {
            LOG.w(TAG, "Characteristic %s does not have NOTIFY or INDICATE property set", characteristicUUID);
        }

        if (!gatt.writeDescriptor(descriptor)) {
            callbackContext.error("Failed to set client characteristic notification for " + characteristicUUID);
            notificationCallbacks.remove(key);
            commandCompleted();
        }
    }

    private void removeNotifyCallback(CallbackContext callbackContext, UUID serviceUUID, UUID characteristicUUID) {

        if (gatt == null) {
            callbackContext.error("BluetoothGatt is null");
            commandCompleted();
            return;
        }

        BluetoothGattService service = gatt.getService(serviceUUID);

        if (service == null) {
            callbackContext.error("Service " + serviceUUID + " not found.");
            commandCompleted();
            return;
        }

        BluetoothGattCharacteristic characteristic = findNotifyCharacteristic(service, characteristicUUID);

        if (characteristic == null) {
            callbackContext.error("Characteristic " + characteristicUUID + " not found.");
            commandCompleted();
            return;
        }

        String key = generateHashKey(serviceUUID, characteristic);

        notificationCallbacks.remove(key);

        if (gatt.setCharacteristicNotification(characteristic, false)) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
            callbackContext.success();
        } else {
            // TODO we can probably ignore and return success anyway since we removed the notification callback
            callbackContext.error("Failed to stop notification for " + characteristicUUID);
        }

        commandCompleted();

    }

    // Some devices reuse UUIDs across characteristics, so we can't use service.getCharacteristic(characteristicUUID)
    // instead check the UUID and properties for each characteristic in the service until we find the best match
    // This function prefers Notify over Indicate
    private BluetoothGattCharacteristic findNotifyCharacteristic(BluetoothGattService service, UUID characteristicUUID) {
        BluetoothGattCharacteristic characteristic = null;

        // Check for Notify first
        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
        for (BluetoothGattCharacteristic c : characteristics) {
            if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 && characteristicUUID.equals(c.getUuid())) {
                characteristic = c;
                break;
            }
        }

        if (characteristic != null) return characteristic;

        // If there wasn't Notify Characteristic, check for Indicate
        for (BluetoothGattCharacteristic c : characteristics) {
            if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 && characteristicUUID.equals(c.getUuid())) {
                characteristic = c;
                break;
            }
        }

        // As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
        if (characteristic == null) {
            characteristic = service.getCharacteristic(characteristicUUID);
        }

        return characteristic;
    }

    private void readCharacteristic(CallbackContext callbackContext, UUID serviceUUID, UUID characteristicUUID) {

        if (gatt == null) {
            callbackContext.error("BluetoothGatt is null");
            commandCompleted();
            return;
        }

        BluetoothGattService service = gatt.getService(serviceUUID);

        if (service == null) {
            callbackContext.error("Service " + serviceUUID + " not found.");
            commandCompleted();
            return;
        }

        BluetoothGattCharacteristic characteristic = findReadableCharacteristic(service, characteristicUUID);

        if (characteristic == null) {
            callbackContext.error("Characteristic " + characteristicUUID + " not found.");
            commandCompleted();
            return;
        }

        boolean success = false;

        synchronized(this) {
            readCallback = callbackContext;
            if (gatt.readCharacteristic(characteristic)) {
                success = true;
            } else {
                readCallback = null;
                callbackContext.error("Read failed");
            }
        }

        if (!success) {
            commandCompleted();
        }

    }

    private void readRSSI(CallbackContext callbackContext) {

        if (gatt == null) {
            callbackContext.error("BluetoothGatt is null");
            commandCompleted();
            return;
        }

        boolean success = false;

        synchronized(this) {
            readCallback = callbackContext;

            if (gatt.readRemoteRssi()) {
                success = true;
            } else {
                readCallback = null;
                callbackContext.error("Read RSSI failed");
            }
        }

        if (!success) {
            commandCompleted();
        }

    }

    // Some peripherals re-use UUIDs for multiple characteristics so we need to check the properties
    // and UUID of all characteristics instead of using service.getCharacteristic(characteristicUUID)
    private BluetoothGattCharacteristic findReadableCharacteristic(BluetoothGattService service, UUID characteristicUUID) {
        BluetoothGattCharacteristic characteristic = null;

        int read = BluetoothGattCharacteristic.PROPERTY_READ;

        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
        for (BluetoothGattCharacteristic c : characteristics) {
            if ((c.getProperties() & read) != 0 && characteristicUUID.equals(c.getUuid())) {
                characteristic = c;
                break;
            }
        }

        // As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
        if (characteristic == null) {
            characteristic = service.getCharacteristic(characteristicUUID);
        }

        return characteristic;
    }

    private void writeCharacteristic(CallbackContext callbackContext, UUID serviceUUID, UUID characteristicUUID, byte[] data, int writeType) {

        if (gatt == null) {
            callbackContext.error("BluetoothGatt is null");
            commandCompleted();
            return;
        }

        BluetoothGattService service = gatt.getService(serviceUUID);

        if (service == null) {
            callbackContext.error("Service " + serviceUUID + " not found.");
            commandCompleted();
            return;
        }

        BluetoothGattCharacteristic characteristic = findWritableCharacteristic(service, characteristicUUID, writeType);

        if (characteristic == null) {
            callbackContext.error("Characteristic " + characteristicUUID + " not found.");
            commandCompleted();
            return;
        }

        boolean success = false;

        characteristic.setValue(data);
        characteristic.setWriteType(writeType);
        synchronized(this) {
            writeCallback = callbackContext;

            if (gatt.writeCharacteristic(characteristic)) {
                success = true;
            } else {
                writeCallback = null;
                callbackContext.error("Write failed");
            }
        }

        if (!success) {
            commandCompleted();
        }

    }

    // Some peripherals re-use UUIDs for multiple characteristics so we need to check the properties
    // and UUID of all characteristics instead of using service.getCharacteristic(characteristicUUID)
    private BluetoothGattCharacteristic findWritableCharacteristic(BluetoothGattService service, UUID characteristicUUID, int writeType) {
        BluetoothGattCharacteristic characteristic = null;

        // get write property
        int writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE;
        if (writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
        }

        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
        for (BluetoothGattCharacteristic c : characteristics) {
            if ((c.getProperties() & writeProperty) != 0 && characteristicUUID.equals(c.getUuid())) {
                characteristic = c;
                break;
            }
        }

        // As a last resort, try and find ANY characteristic with this UUID, even if it doesn't have the correct properties
        if (characteristic == null) {
            characteristic = service.getCharacteristic(characteristicUUID);
        }

        return characteristic;
    }

    public void queueRead(CallbackContext callbackContext, UUID serviceUUID, UUID characteristicUUID) {
        BLECommand command = new BLECommand(callbackContext, serviceUUID, characteristicUUID, BLECommand.READ);
        queueCommand(command);
    }

    public void queueWrite(CallbackContext callbackContext, UUID serviceUUID, UUID characteristicUUID, byte[] data, int writeType) {
        BLECommand command = new BLECommand(callbackContext, serviceUUID, characteristicUUID, data, writeType);
        queueCommand(command);
    }

    public void queueRegisterNotifyCallback(CallbackContext callbackContext, UUID serviceUUID, UUID characteristicUUID) {
        BLECommand command = new BLECommand(callbackContext, serviceUUID, characteristicUUID, BLECommand.REGISTER_NOTIFY);
        queueCommand(command);
    }

    public void queueRemoveNotifyCallback(CallbackContext callbackContext, UUID serviceUUID, UUID characteristicUUID) {
        BLECommand command = new BLECommand(callbackContext, serviceUUID, characteristicUUID, BLECommand.REMOVE_NOTIFY);
        queueCommand(command);
    }


    public void queueReadRSSI(CallbackContext callbackContext) {
        BLECommand command = new BLECommand(callbackContext, null, null, BLECommand.READ_RSSI);
        queueCommand(command);
    }

    public void queueCleanup() {
        bleProcessing.set(true); // Stop anything else trying to process
        for (BLECommand command = commandQueue.poll(); command != null; command = commandQueue.poll()) {
            command.getCallbackContext().error("Peripheral Disconnected");
        }
        bleProcessing.set(false); // Now re-allow processing

        Collection<L2CAPContext> contexts;
        synchronized (l2capContexts) {
            contexts = new ArrayList<>(l2capContexts.values());
        }
        for(L2CAPContext context : contexts) {
            context.disconnectL2Cap();
        }
    }

    public void writeL2CapChannel(CallbackContext callbackContext, int psm, byte[] data) {
        Timber.i("L2CAP Write %s", psm);
        getOrAddL2CAPContext(psm).writeL2CapChannel(callbackContext, data);
    }

    private void callbackCleanup() {
        synchronized(this) {
            if (readCallback != null) {
                readCallback.error(this.asJSONObject("Peripheral Disconnected"));
                readCallback = null;
                commandCompleted();
            }
            if (writeCallback != null) {
                writeCallback.error(this.asJSONObject("Peripheral Disconnected"));
                writeCallback = null;
                commandCompleted();
            }
        }
    }

    // add a new command to the queue
    private void queueCommand(BLECommand command) {
        Timber.i("Queuing Command %s", command);
        commandQueue.add(command);

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        command.getCallbackContext().sendPluginResult(result);

        processCommands();
    }

    // command finished, queue the next command
    private void commandCompleted() {
        Timber.i("Processing Complete");
        bleProcessing.set(false);
        processCommands();
    }

    // process the queue
    private void processCommands() {
        final boolean canProcess = bleProcessing.compareAndSet(false, true);
        if (!canProcess) { return; }
        Timber.i("Processing Commands");

        BLECommand command = commandQueue.poll();
        if (command != null) {
            if (command.getType() == BLECommand.READ) {
                Timber.i("Read %s", command.getCharacteristicUUID());
                readCharacteristic(command.getCallbackContext(), command.getServiceUUID(), command.getCharacteristicUUID());
            } else if (command.getType() == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                Timber.i("Write %s", command.getCharacteristicUUID());
                writeCharacteristic(command.getCallbackContext(), command.getServiceUUID(), command.getCharacteristicUUID(), command.getData(), command.getType());
            } else if (command.getType() == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                Timber.i("Write No Response %s", command.getCharacteristicUUID());
                writeCharacteristic(command.getCallbackContext(), command.getServiceUUID(), command.getCharacteristicUUID(), command.getData(), command.getType());
            } else if (command.getType() == BLECommand.REGISTER_NOTIFY) {
                Timber.i("Register Notify %s", command.getCharacteristicUUID());
                registerNotifyCallback(command.getCallbackContext(), command.getServiceUUID(), command.getCharacteristicUUID());
            } else if (command.getType() == BLECommand.REMOVE_NOTIFY) {
                Timber.i("Remove Notify %s", command.getCharacteristicUUID());
                removeNotifyCallback(command.getCallbackContext(), command.getServiceUUID(), command.getCharacteristicUUID());
            } else if (command.getType() == BLECommand.READ_RSSI) {
                Timber.i("Read RSSI");
                readRSSI(command.getCallbackContext());
            } else {
                // this shouldn't happen
                bleProcessing.set(false);
                throw new RuntimeException("Unexpected BLE Command type " + command.getType());
            }
        } else {
            bleProcessing.set(false);
            Timber.i("Command Queue is empty.");
        }

    }

    private String generateHashKey(BluetoothGattCharacteristic characteristic) {
        return generateHashKey(characteristic.getService().getUuid(), characteristic);
    }

    private String generateHashKey(UUID serviceUUID, BluetoothGattCharacteristic characteristic) {
        return serviceUUID + "|" + characteristic.getUuid() + "|" + characteristic.getInstanceId();
    }

    public void connectL2cap(CallbackContext callbackContext, int psm, boolean secureChannel) {
        getOrAddL2CAPContext(psm).connectL2cap(callbackContext, secureChannel);
    }

    public void disconnectL2Cap(CallbackContext callbackContext, int psm) {
        L2CAPContext context;
        synchronized (l2capContexts) {
            context = l2capContexts.get(psm);
        };
        if (context != null) {
            context.disconnectL2Cap();
        }
        callbackContext.success();
    }

    public boolean isL2capConnected(int psm) {
        return getOrAddL2CAPContext(psm).isConnected();
    }

    public void registerL2CapReceiver(CallbackContext callbackContext, int psm) {
        getOrAddL2CAPContext(psm).registerL2CapReceiver(callbackContext);
    }

    private L2CAPContext getOrAddL2CAPContext(int psm) {
        synchronized (l2capContexts) {
            L2CAPContext context = l2capContexts.get(psm);
            if (context == null) {
                context = new L2CAPContext(this.device, psm);
                l2capContexts.put(psm, context);
            }
            return context;
        }
    }
}
