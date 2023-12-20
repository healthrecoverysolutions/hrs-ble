"use strict";
////////////////////////////////////////////////////////////////
// Generic Cordova Utilities
////////////////////////////////////////////////////////////////
Object.defineProperty(exports, "__esModule", { value: true });
exports.BLE = exports.BLEPluginCordovaInterface = exports.L2CAPCordovaInterface = exports.BluetoothEventType = exports.CORDOVA_BRIDGE_MOCKED = exports.CORDOVA_BRIDGE_DEFAULT = void 0;
function noop() {
    return;
}
function cordovaExec(plugin, method, successCallback, errorCallback, args) {
    if (successCallback === void 0) { successCallback = noop; }
    if (errorCallback === void 0) { errorCallback = noop; }
    if (args === void 0) { args = []; }
    if (window.cordova) {
        window.cordova.exec(successCallback, errorCallback, plugin, method, args);
    }
    else {
        console.warn("".concat(plugin, ".").concat(method, "(...) :: cordova not available"));
        errorCallback && errorCallback("cordova_not_available");
    }
}
function cordovaExecPromise(plugin, method, args) {
    return new Promise(function (resolve, reject) {
        cordovaExec(plugin, method, resolve, reject, args);
    });
}
////////////////////////////////////////////////////////////////
// Plugin Interface
////////////////////////////////////////////////////////////////
var PLUGIN_NAME = 'BLE';
exports.CORDOVA_BRIDGE_DEFAULT = {
    invoke: function (method) {
        var args = [];
        for (var _i = 1; _i < arguments.length; _i++) {
            args[_i - 1] = arguments[_i];
        }
        return cordovaExecPromise(PLUGIN_NAME, method, args);
    },
    invokeCb: function (method, successCallback, errorCallback) {
        if (successCallback === void 0) { successCallback = noop; }
        if (errorCallback === void 0) { errorCallback = noop; }
        var args = [];
        for (var _i = 3; _i < arguments.length; _i++) {
            args[_i - 3] = arguments[_i];
        }
        cordovaExec(PLUGIN_NAME, method, successCallback, errorCallback, args);
    }
};
exports.CORDOVA_BRIDGE_MOCKED = {
    invoke: function (method) {
        var _args = [];
        for (var _i = 1; _i < arguments.length; _i++) {
            _args[_i - 1] = arguments[_i];
        }
        return Promise.resolve(method);
    },
    invokeCb: function (method, successCallback, _errorCallback) {
        if (successCallback === void 0) { successCallback = noop; }
        if (_errorCallback === void 0) { _errorCallback = noop; }
        var _args = [];
        for (var _i = 3; _i < arguments.length; _i++) {
            _args[_i - 3] = arguments[_i];
        }
        successCallback(method);
    }
};
var BluetoothEventType;
(function (BluetoothEventType) {
    BluetoothEventType["CONNECTED"] = "CONNECTED";
    BluetoothEventType["DISCONNECTED"] = "DISCONNECTED";
    BluetoothEventType["READ_RESULT"] = "READ_RESULT";
    BluetoothEventType["NOTIFICATION_STARTED"] = "NOTIFICATION_STARTED";
    BluetoothEventType["NOTIFICATION_STOPPED"] = "NOTIFICATION_STOPPED";
    BluetoothEventType["NOTIFICATION_RESULT"] = "NOTIFICATION_RESULT";
})(BluetoothEventType || (exports.BluetoothEventType = BluetoothEventType = {}));
function stringToArrayBuffer(str) {
    var ret = new Uint8Array(str.length);
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
var autoconnected = {};
var L2CAPCordovaInterface = /** @class */ (function () {
    function L2CAPCordovaInterface(bridge) {
        if (bridge === void 0) { bridge = exports.CORDOVA_BRIDGE_DEFAULT; }
        this.bridge = bridge;
    }
    L2CAPCordovaInterface.prototype.close = function (deviceId, psm) {
        return this.bridge.invoke('closeL2Cap', deviceId, psm);
    };
    L2CAPCordovaInterface.prototype.open = function (deviceId, psmOrOptions) {
        var psm = psmOrOptions;
        var settings = {};
        if (typeof psmOrOptions === 'object' && 'psm' in psmOrOptions) {
            psm = psmOrOptions.psm;
            settings = psmOrOptions;
        }
        return this.bridge.invoke('openL2Cap', deviceId, psm, settings);
    };
    L2CAPCordovaInterface.prototype.receiveData = function (deviceId, psm) {
        return this.bridge.invoke('receiveDataL2Cap', deviceId, psm);
    };
    L2CAPCordovaInterface.prototype.write = function (deviceId, psm, data) {
        return this.bridge.invoke('writeL2Cap', deviceId, psm, data);
    };
    return L2CAPCordovaInterface;
}());
exports.L2CAPCordovaInterface = L2CAPCordovaInterface;
var BLEPluginCordovaInterface = /** @class */ (function () {
    function BLEPluginCordovaInterface(bridge) {
        if (bridge === void 0) { bridge = exports.CORDOVA_BRIDGE_DEFAULT; }
        this.bridge = bridge;
        this.l2cap = new L2CAPCordovaInterface(bridge);
    }
    BLEPluginCordovaInterface.prototype.addEventListener = function (listener) {
        return this.bridge.invoke("addEventListener", listener);
    };
    BLEPluginCordovaInterface.prototype.removeEventListener = function (listener) {
        return this.bridge.invoke("removeEventListener", listener);
    };
    BLEPluginCordovaInterface.prototype.removeAllEventListeners = function () {
        return this.bridge.invoke("removeAllEventListeners");
    };
    BLEPluginCordovaInterface.prototype.watch = function (endpoints) {
        return this.bridge.invoke("watch", endpoints);
    };
    BLEPluginCordovaInterface.prototype.unwatch = function (endpoints) {
        return this.bridge.invoke("unwatch", endpoints);
    };
    BLEPluginCordovaInterface.prototype.scan = function (services, seconds, success, failure) {
        var successWrapper = function (peripheral) {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        this.bridge.invokeCb('scan', successWrapper, failure, services, seconds);
    };
    BLEPluginCordovaInterface.prototype.startScan = function (services, success, failure) {
        var successWrapper = function (peripheral) {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        this.bridge.invokeCb('startScan', successWrapper, failure, services);
    };
    BLEPluginCordovaInterface.prototype.stopScan = function () {
        return this.bridge.invoke('stopScan');
    };
    BLEPluginCordovaInterface.prototype.startScanWithOptions = function (services, options, success, failure) {
        var successWrapper = function (peripheral) {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        options = options || {};
        this.bridge.invokeCb('startScanWithOptions', successWrapper, failure, services, options);
    };
    /**
     * Find connected peripherals offering the listed service UUIDs.
     * This function wraps CBCentralManager.retrieveConnectedPeripheralsWithServices.
     * [Android] peripheralsWithIdentifiers is not supported on Android.
     */
    BLEPluginCordovaInterface.prototype.connectedPeripheralsWithServices = function (services) {
        return this.bridge.invoke('connectedPeripheralsWithServices', services);
    };
    /**
     * Find known (but not necessarily connected) peripherals offering the listed device UUIDs.
     * This function wraps CBCentralManager.retrievePeripheralsWithIdentifiers
     * [Android] peripheralsWithIdentifiers is not supported on Android.
     */
    BLEPluginCordovaInterface.prototype.peripheralsWithIdentifiers = function (deviceIds) {
        return this.bridge.invoke('peripheralsWithIdentifiers', deviceIds);
    };
    /**
     * Find the bonded devices.
     * [iOS] bondedDevices is not supported on iOS.
     */
    BLEPluginCordovaInterface.prototype.bondedDevices = function () {
        return this.bridge.invoke('bondedDevices');
    };
    /* Lists all peripherals discovered by the plugin due to scanning or connecting since app launch.
    [iOS] list is not supported on iOS. */
    BLEPluginCordovaInterface.prototype.list = function () {
        return this.bridge.invoke('list');
    };
    BLEPluginCordovaInterface.prototype.connect = function (deviceId, connectCallback, disconnectCallback) {
        var successWrapper = function (peripheral) {
            convertToNativeJS(peripheral);
            connectCallback(peripheral);
        };
        this.bridge.invokeCb('connect', successWrapper, disconnectCallback, deviceId);
    };
    /**
     * Automatically connect to a device when it is in range of the phone
     * [iOS] background notifications on ios must be enabled if you want to run in the background
     * [Android] this relies on the autoConnect argument of BluetoothDevice.connectGatt().
     * Not all Android devices implement this feature correctly.
     */
    BLEPluginCordovaInterface.prototype.autoConnect = function (deviceId, connectCallback, disconnectCallback) {
        var _this = this;
        var disconnectCallbackWrapper;
        autoconnected[deviceId] = true;
        // wrap connectCallback so nested array buffers in advertising info are handled correctly
        var connectCallbackWrapper = function (peripheral) {
            convertToNativeJS(peripheral);
            connectCallback(peripheral);
        };
        // iOS needs to reconnect on disconnect, unless ble.disconnect was called.
        if (cordova.platformId === 'ios') {
            disconnectCallbackWrapper = function (peripheral) {
                // let the app know the peripheral disconnected
                disconnectCallback(peripheral);
                // reconnect if we have a peripheral.id and the user didn't call disconnect
                if (peripheral.id && autoconnected[peripheral.id]) {
                    _this.bridge.invokeCb('autoConnect', connectCallbackWrapper, disconnectCallbackWrapper, deviceId);
                }
            };
        }
        else {
            // no wrapper for Android
            disconnectCallbackWrapper = disconnectCallback;
        }
        this.bridge.invokeCb('autoConnect', connectCallbackWrapper, disconnectCallbackWrapper, deviceId);
    };
    BLEPluginCordovaInterface.prototype.disconnect = function (deviceId) {
        try {
            delete autoconnected[deviceId];
        }
        catch (e) {
            // ignore error
        }
        return this.bridge.invoke('disconnect', deviceId);
    };
    BLEPluginCordovaInterface.prototype.queueCleanup = function (deviceId) {
        return this.bridge.invoke('queueCleanup', deviceId);
    };
    /**
     * sets the pin when device requires it.
     * [iOS] setPin is not supported on iOS.
     */
    BLEPluginCordovaInterface.prototype.setPin = function (pin) {
        return this.bridge.invoke('setPin', pin);
    };
    /**
     * May be used to request (on Android) a larger MTU size to be able to send more data at once
     * [iOS] requestMtu is not supported on iOS.
     */
    BLEPluginCordovaInterface.prototype.requestMtu = function (deviceId, mtu) {
        return this.bridge.invoke('requestMtu', deviceId, mtu);
    };
    /**
     * When Connecting to a peripheral android can request for the connection priority for faster communication.
     * [iOS] requestConnectionPriority is not supported on iOS.
     */
    BLEPluginCordovaInterface.prototype.requestConnectionPriority = function (deviceId, priority) {
        return this.bridge.invoke('requestConnectionPriority', deviceId, priority);
    };
    /**
     * Clears cached services and characteristics info for some poorly behaved devices.
     * Uses an undocumented API, so it is not guaranteed to work.
     * [iOS] refreshDeviceCache is not supported on iOS.
     */
    BLEPluginCordovaInterface.prototype.refreshDeviceCache = function (deviceId, timeoutMillis) {
        return this.bridge.invoke('refreshDeviceCache', deviceId, timeoutMillis);
    };
    BLEPluginCordovaInterface.prototype.read = function (deviceId, serviceUuid, characteristicUuid) {
        return this.bridge.invoke('read', deviceId, serviceUuid, characteristicUuid);
    };
    BLEPluginCordovaInterface.prototype.readRSSI = function (deviceId) {
        return this.bridge.invoke('readRSSI', deviceId);
    };
    BLEPluginCordovaInterface.prototype.write = function (deviceId, serviceUuid, characteristicUuid, data) {
        return this.bridge.invoke('write', deviceId, serviceUuid, characteristicUuid, data);
    };
    /**
     * Writes data to a characteristic without a response from the peripheral.
     * You are not notified if the write fails in the BLE stack.
     * The success callback is be called when the characteristic is written.
     */
    BLEPluginCordovaInterface.prototype.writeWithoutResponse = function (deviceId, serviceUuid, characteristicUuid, data) {
        return this.bridge.invoke('writeWithoutResponse', deviceId, serviceUuid, characteristicUuid, data);
    };
    /**
     * Start notifications on the given characteristic
     * - options
     *      emitOnRegistered  - Default is false. Emit "registered" to success callback
     *                          when peripheral confirms notifications are active
     */
    BLEPluginCordovaInterface.prototype.startNotification = function (deviceId, serviceUuid, characteristicUuid, success, failure, options) {
        var emitOnRegistered = options && options.emitOnRegistered == true;
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
    };
    BLEPluginCordovaInterface.prototype.stopNotification = function (deviceId, serviceUuid, characteristicUuid) {
        return this.bridge.invoke('stopNotification', deviceId, serviceUuid, characteristicUuid);
    };
    /**
     * Calls the success callback when the peripheral is connected and the failure callback when not connected.
     */
    BLEPluginCordovaInterface.prototype.isConnected = function (deviceId) {
        return this.bridge.invoke('isConnected', deviceId);
    };
    BLEPluginCordovaInterface.prototype.testConnected = function (deviceId) {
        return this.isConnected(deviceId)
            .then(function () { return true; })
            .catch(function () { return false; });
    };
    /**
     * Reports if bluetooth is enabled.
     */
    BLEPluginCordovaInterface.prototype.isEnabled = function () {
        return this.bridge.invoke('isEnabled');
    };
    BLEPluginCordovaInterface.prototype.testEnabled = function () {
        return this.isEnabled()
            .then(function () { return true; })
            .catch(function () { return false; });
    };
    /**
     * Reports if location services are enabled.
     * [iOS] isLocationEnabled is not supported on iOS.
     */
    BLEPluginCordovaInterface.prototype.isLocationEnabled = function () {
        return this.bridge.invoke('isLocationEnabled');
    };
    BLEPluginCordovaInterface.prototype.testLocationEnabled = function () {
        return this.isLocationEnabled()
            .then(function () { return true; })
            .catch(function () { return false; });
    };
    /**
     * Enable Bluetooth on the device.
     * [iOS] enable is not supported on iOS.
     */
    BLEPluginCordovaInterface.prototype.enable = function () {
        return this.bridge.invoke('enable');
    };
    /**
     * Opens the Bluetooth settings for the operating systems.
     * [iOS] showBluetoothSettings is not supported on iOS.
     */
    BLEPluginCordovaInterface.prototype.showBluetoothSettings = function () {
        return this.bridge.invoke('showBluetoothSettings');
    };
    /**
     * Registers a change listener for location-related services.
     * [iOS] startLocationStateNotifications is not supported on iOS.
     */
    BLEPluginCordovaInterface.prototype.startLocationStateNotifications = function (change, failure) {
        this.bridge.invokeCb('startLocationStateNotifications', change, failure);
    };
    BLEPluginCordovaInterface.prototype.stopLocationStateNotifications = function () {
        return this.bridge.invoke('stopLocationStateNotifications');
    };
    /**
     * Registers a change listener for Bluetooth adapter state changes
     */
    BLEPluginCordovaInterface.prototype.startStateNotifications = function (success, failure) {
        this.bridge.invokeCb('startStateNotifications', success, failure);
    };
    BLEPluginCordovaInterface.prototype.stopStateNotifications = function () {
        return this.bridge.invoke('stopStateNotifications');
    };
    /**
     * Reports the BLE restoration status if the app was restarted by iOS as a result of a BLE event.
     * See https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html#//apple_ref/doc/uid/TP40013257-CH7-SW10
     * [Android] restoredBluetoothState is not supported on Android.
     */
    BLEPluginCordovaInterface.prototype.restoredBluetoothState = function () {
        return this.bridge.invoke('restoredBluetoothState');
    };
    return BLEPluginCordovaInterface;
}());
exports.BLEPluginCordovaInterface = BLEPluginCordovaInterface;
exports.BLE = new BLEPluginCordovaInterface();
