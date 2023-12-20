////////////////////////////////////////////////////////////////
// Generic Cordova Utilities
////////////////////////////////////////////////////////////////
function noop() {
    return;
}
function cordovaExec(plugin, method, successCallback = noop, errorCallback = noop, args = []) {
    if (window.cordova) {
        window.cordova.exec(successCallback, errorCallback, plugin, method, args);
    }
    else {
        console.warn(`${plugin}.${method}(...) :: cordova not available`);
        errorCallback && errorCallback(`cordova_not_available`);
    }
}
function cordovaExecPromise(plugin, method, args) {
    return new Promise((resolve, reject) => {
        cordovaExec(plugin, method, resolve, reject, args);
    });
}
////////////////////////////////////////////////////////////////
// Plugin Interface
////////////////////////////////////////////////////////////////
const PLUGIN_NAME = 'BLE';
export const CORDOVA_BRIDGE_DEFAULT = {
    invoke(method, ...args) {
        return cordovaExecPromise(PLUGIN_NAME, method, args);
    },
    invokeCb(method, successCallback = noop, errorCallback = noop, ...args) {
        cordovaExec(PLUGIN_NAME, method, successCallback, errorCallback, args);
    }
};
export const CORDOVA_BRIDGE_MOCKED = {
    invoke(method, ..._args) {
        return Promise.resolve(method);
    },
    invokeCb(method, successCallback = noop, _errorCallback = noop, ..._args) {
        successCallback(method);
    }
};
export var BluetoothEventType;
(function (BluetoothEventType) {
    BluetoothEventType["CONNECTED"] = "CONNECTED";
    BluetoothEventType["DISCONNECTED"] = "DISCONNECTED";
    BluetoothEventType["READ_RESULT"] = "READ_RESULT";
    BluetoothEventType["NOTIFICATION_STARTED"] = "NOTIFICATION_STARTED";
    BluetoothEventType["NOTIFICATION_STOPPED"] = "NOTIFICATION_STOPPED";
    BluetoothEventType["NOTIFICATION_RESULT"] = "NOTIFICATION_RESULT";
})(BluetoothEventType || (BluetoothEventType = {}));
function stringToArrayBuffer(str) {
    const ret = new Uint8Array(str.length);
    for (var i = 0; i < str.length; i++) {
        ret[i] = str.charCodeAt(i);
    }
    return ret.buffer;
}
;
function base64ToArrayBuffer(b64) {
    return stringToArrayBuffer(atob(b64));
}
;
function massageMessageNativeToJs(message) {
    if (message.CDVType == 'ArrayBuffer') {
        message = base64ToArrayBuffer(message.data);
    }
    return message;
}
// Cordova 3.6 doesn't unwrap ArrayBuffers in nested data structures
// https://github.com/apache/cordova-js/blob/94291706945c42fd47fa632ed30f5eb811080e95/src/ios/exec.js#L107-L122
function convertToNativeJS(object) {
    Object.keys(object).forEach(function (key) {
        var value = object[key];
        object[key] = massageMessageNativeToJs(value);
        if (typeof value === 'object') {
            convertToNativeJS(value);
        }
    });
}
// set of auto-connected device ids
let autoconnected = {};
export class L2CAPCordovaInterface {
    constructor(bridge = CORDOVA_BRIDGE_DEFAULT) {
        this.bridge = bridge;
    }
    close(deviceId, psm) {
        return this.bridge.invoke('closeL2Cap', deviceId, psm);
    }
    open(deviceId, psmOrOptions) {
        let psm = psmOrOptions;
        let settings = {};
        if (typeof psmOrOptions === 'object' && 'psm' in psmOrOptions) {
            psm = psmOrOptions.psm;
            settings = psmOrOptions;
        }
        return this.bridge.invoke('openL2Cap', deviceId, psm, settings);
    }
    receiveData(deviceId, psm) {
        return this.bridge.invoke('receiveDataL2Cap', deviceId, psm);
    }
    write(deviceId, psm, data) {
        return this.bridge.invoke('writeL2Cap', deviceId, psm, data);
    }
}
export class BLEPluginCordovaInterface {
    constructor(bridge = CORDOVA_BRIDGE_DEFAULT) {
        this.bridge = bridge;
        this.l2cap = new L2CAPCordovaInterface(bridge);
    }
    addEventListener(listener) {
        return this.bridge.invoke(`addEventListener`, listener);
    }
    removeEventListener(listener) {
        return this.bridge.invoke(`removeEventListener`, listener);
    }
    removeAllEventListeners() {
        return this.bridge.invoke(`removeAllEventListeners`);
    }
    watch(endpoints) {
        return this.bridge.invoke(`watch`, endpoints);
    }
    unwatch(endpoints) {
        return this.bridge.invoke(`unwatch`, endpoints);
    }
    scan(services, seconds, success, failure) {
        const successWrapper = (peripheral) => {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        this.bridge.invokeCb('scan', successWrapper, failure, services, seconds);
    }
    startScan(services, success, failure) {
        const successWrapper = (peripheral) => {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        this.bridge.invokeCb('startScan', successWrapper, failure, services);
    }
    stopScan() {
        return this.bridge.invoke('stopScan');
    }
    startScanWithOptions(services, options, success, failure) {
        const successWrapper = (peripheral) => {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        options = options || {};
        this.bridge.invokeCb('startScanWithOptions', successWrapper, failure, services, options);
    }
    /**
     * Find connected peripherals offering the listed service UUIDs.
     * This function wraps CBCentralManager.retrieveConnectedPeripheralsWithServices.
     * [Android] peripheralsWithIdentifiers is not supported on Android.
     */
    connectedPeripheralsWithServices(services) {
        return this.bridge.invoke('connectedPeripheralsWithServices', services);
    }
    /**
     * Find known (but not necessarily connected) peripherals offering the listed device UUIDs.
     * This function wraps CBCentralManager.retrievePeripheralsWithIdentifiers
     * [Android] peripheralsWithIdentifiers is not supported on Android.
     */
    peripheralsWithIdentifiers(deviceIds) {
        return this.bridge.invoke('peripheralsWithIdentifiers', deviceIds);
    }
    /**
     * Find the bonded devices.
     * [iOS] bondedDevices is not supported on iOS.
     */
    bondedDevices() {
        return this.bridge.invoke('bondedDevices');
    }
    /* Lists all peripherals discovered by the plugin due to scanning or connecting since app launch.
    [iOS] list is not supported on iOS. */
    list() {
        return this.bridge.invoke('list');
    }
    connect(deviceId, connectCallback, disconnectCallback) {
        const successWrapper = (peripheral) => {
            convertToNativeJS(peripheral);
            connectCallback(peripheral);
        };
        this.bridge.invokeCb('connect', successWrapper, disconnectCallback, deviceId);
    }
    /**
     * Automatically connect to a device when it is in range of the phone
     * [iOS] background notifications on ios must be enabled if you want to run in the background
     * [Android] this relies on the autoConnect argument of BluetoothDevice.connectGatt().
     * Not all Android devices implement this feature correctly.
     */
    autoConnect(deviceId, connectCallback, disconnectCallback) {
        let disconnectCallbackWrapper;
        autoconnected[deviceId] = true;
        // wrap connectCallback so nested array buffers in advertising info are handled correctly
        const connectCallbackWrapper = (peripheral) => {
            convertToNativeJS(peripheral);
            connectCallback(peripheral);
        };
        // iOS needs to reconnect on disconnect, unless ble.disconnect was called.
        if (cordova.platformId === 'ios') {
            disconnectCallbackWrapper = (peripheral) => {
                // let the app know the peripheral disconnected
                disconnectCallback(peripheral);
                // reconnect if we have a peripheral.id and the user didn't call disconnect
                if (peripheral.id && autoconnected[peripheral.id]) {
                    this.bridge.invokeCb('autoConnect', connectCallbackWrapper, disconnectCallbackWrapper, deviceId);
                }
            };
        }
        else {
            // no wrapper for Android
            disconnectCallbackWrapper = disconnectCallback;
        }
        this.bridge.invokeCb('autoConnect', connectCallbackWrapper, disconnectCallbackWrapper, deviceId);
    }
    disconnect(deviceId) {
        try {
            delete autoconnected[deviceId];
        }
        catch (e) {
            // ignore error
        }
        return this.bridge.invoke('disconnect', deviceId);
    }
    queueCleanup(deviceId) {
        return this.bridge.invoke('queueCleanup', deviceId);
    }
    /**
     * sets the pin when device requires it.
     * [iOS] setPin is not supported on iOS.
     */
    setPin(pin) {
        return this.bridge.invoke('setPin', pin);
    }
    /**
     * May be used to request (on Android) a larger MTU size to be able to send more data at once
     * [iOS] requestMtu is not supported on iOS.
     */
    requestMtu(deviceId, mtu) {
        return this.bridge.invoke('requestMtu', deviceId, mtu);
    }
    /**
     * When Connecting to a peripheral android can request for the connection priority for faster communication.
     * [iOS] requestConnectionPriority is not supported on iOS.
     */
    requestConnectionPriority(deviceId, priority) {
        return this.bridge.invoke('requestConnectionPriority', deviceId, priority);
    }
    /**
     * Clears cached services and characteristics info for some poorly behaved devices.
     * Uses an undocumented API, so it is not guaranteed to work.
     * [iOS] refreshDeviceCache is not supported on iOS.
     */
    refreshDeviceCache(deviceId, timeoutMillis) {
        return this.bridge.invoke('refreshDeviceCache', deviceId, timeoutMillis);
    }
    read(deviceId, serviceUuid, characteristicUuid) {
        return this.bridge.invoke('read', deviceId, serviceUuid, characteristicUuid);
    }
    readRSSI(deviceId) {
        return this.bridge.invoke('readRSSI', deviceId);
    }
    write(deviceId, serviceUuid, characteristicUuid, data) {
        return this.bridge.invoke('write', deviceId, serviceUuid, characteristicUuid, data);
    }
    /**
     * Writes data to a characteristic without a response from the peripheral.
     * You are not notified if the write fails in the BLE stack.
     * The success callback is be called when the characteristic is written.
     */
    writeWithoutResponse(deviceId, serviceUuid, characteristicUuid, data) {
        return this.bridge.invoke('writeWithoutResponse', deviceId, serviceUuid, characteristicUuid, data);
    }
    /**
     * Start notifications on the given characteristic
     * - options
     *      emitOnRegistered  - Default is false. Emit "registered" to success callback
     *                          when peripheral confirms notifications are active
     */
    startNotification(deviceId, serviceUuid, characteristicUuid, success, failure, options) {
        const emitOnRegistered = options && options.emitOnRegistered == true;
        function onEvent(data) {
            if (data === 'registered') {
                // For backwards compatibility, don't emit the registered event unless explicitly instructed
                if (emitOnRegistered)
                    success(data);
            }
            else {
                success(data);
            }
        }
        this.bridge.invokeCb('startNotification', onEvent, failure, deviceId, serviceUuid, characteristicUuid);
    }
    stopNotification(deviceId, serviceUuid, characteristicUuid) {
        return this.bridge.invoke('stopNotification', deviceId, serviceUuid, characteristicUuid);
    }
    /**
     * Calls the success callback when the peripheral is connected and the failure callback when not connected.
     */
    isConnected(deviceId) {
        return this.bridge.invoke('isConnected', deviceId);
    }
    testConnected(deviceId) {
        return this.isConnected(deviceId)
            .then(() => true)
            .catch(() => false);
    }
    /**
     * Reports if bluetooth is enabled.
     */
    isEnabled() {
        return this.bridge.invoke('isEnabled');
    }
    testEnabled() {
        return this.isEnabled()
            .then(() => true)
            .catch(() => false);
    }
    /**
     * Reports if location services are enabled.
     * [iOS] isLocationEnabled is not supported on iOS.
     */
    isLocationEnabled() {
        return this.bridge.invoke('isLocationEnabled');
    }
    testLocationEnabled() {
        return this.isLocationEnabled()
            .then(() => true)
            .catch(() => false);
    }
    /**
     * Enable Bluetooth on the device.
     * [iOS] enable is not supported on iOS.
     */
    enable() {
        return this.bridge.invoke('enable');
    }
    /**
     * Opens the Bluetooth settings for the operating systems.
     * [iOS] showBluetoothSettings is not supported on iOS.
     */
    showBluetoothSettings() {
        return this.bridge.invoke('showBluetoothSettings');
    }
    /**
     * Registers a change listener for location-related services.
     * [iOS] startLocationStateNotifications is not supported on iOS.
     */
    startLocationStateNotifications(change, failure) {
        this.bridge.invokeCb('startLocationStateNotifications', change, failure);
    }
    stopLocationStateNotifications() {
        return this.bridge.invoke('stopLocationStateNotifications');
    }
    /**
     * Registers a change listener for Bluetooth adapter state changes
     */
    startStateNotifications(success, failure) {
        this.bridge.invokeCb('startStateNotifications', success, failure);
    }
    stopStateNotifications() {
        return this.bridge.invoke('stopStateNotifications');
    }
    /**
     * Reports the BLE restoration status if the app was restarted by iOS as a result of a BLE event.
     * See https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html#//apple_ref/doc/uid/TP40013257-CH7-SW10
     * [Android] restoredBluetoothState is not supported on Android.
     */
    restoredBluetoothState() {
        return this.bridge.invoke('restoredBluetoothState');
    }
}
export const BLE = new BLEPluginCordovaInterface();
