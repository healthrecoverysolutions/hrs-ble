////////////////////////////////////////////////////////////////
// Generic Cordova Utilities
////////////////////////////////////////////////////////////////

type CdvSuccessCallback<TValue> = (value: TValue) => void;
type CdvErrorCallback = (error: any) => void;

function noop() {
    return;
}

function cordovaExec<T>(
    plugin: string,
	method: string,
	successCallback: CdvSuccessCallback<T> = noop,
	errorCallback: CdvErrorCallback = noop,
	args: any[] = [],
): void {
    if (window.cordova) {
        window.cordova.exec(successCallback, errorCallback, plugin, method, args);

    } else {
        console.warn(`${plugin}.${method}(...) :: cordova not available`);
        errorCallback && errorCallback(`cordova_not_available`);
    }
}

function cordovaExecPromise<T>(plugin: string, method: string, args?: any[]): Promise<T> {
    return new Promise<T>((resolve, reject) => {
        cordovaExec<T>(plugin, method, resolve, reject, args);
    });
}

export interface CordovaBridge {
    invoke<T>(method: string, ...args: any[]): Promise<T>;
    invokeCb<T>(
        method: string,
        successCallback?: CdvSuccessCallback<T>,
        errorCallback?: CdvErrorCallback,
        ...args: any[]
    ): void;
}

////////////////////////////////////////////////////////////////
// Plugin Interface
////////////////////////////////////////////////////////////////

const PLUGIN_NAME = 'BLE';

export const CORDOVA_BRIDGE_DEFAULT: CordovaBridge = {
    invoke<T>(method: string, ...args: any[]): Promise<T> {
        return cordovaExecPromise<T>(PLUGIN_NAME, method, args);
    },
    invokeCb<T>(
        method: string,
        successCallback: CdvSuccessCallback<T> = noop,
        errorCallback: CdvErrorCallback = noop,
        ...args: any[]
    ): void {
        cordovaExec(PLUGIN_NAME, method, successCallback, errorCallback, args);
    }
};

export const CORDOVA_BRIDGE_MOCKED: CordovaBridge = {
    invoke<T>(method: string, ..._args: any[]): Promise<T> {
        return Promise.resolve(method) as any;
    },
    invokeCb<T>(
        method: string,
        successCallback: CdvSuccessCallback<T> = noop,
        _errorCallback: CdvErrorCallback = noop,
        ..._args: any[]
    ): void {
        successCallback(method as any);
    }
};

export type PeripheralState = 'disconnected' | 'disconnecting' | 'connecting' | 'connected';
export type ConnectionPriority = 'high' | 'balanced' | 'low';

export interface PeripheralCharacteristic {
    service: string;
    characteristic: string;
    properties: string[];
    descriptors?: any[];
}

export interface PeripheralData {
    name: string;
    id: string;
    rssi: number;
    advertising: ArrayBuffer | any;
    state: PeripheralState;
}

export interface PeripheralDataExtended extends PeripheralData {
    services: string[];
    characteristics: PeripheralCharacteristic[];
}

export interface BLEError {
    name: string;
    id: string;
    errorMessage: string;
}

export interface RestoredState {
    peripherals?: PeripheralDataExtended[];
    scanServiceUUIDs?: string[];
    scanOptions?: Record<string, any>;
}

export interface StartScanOptions {
    /* Android only */
    scanMode?: 'lowPower' | 'balanced' | 'lowLatency' | 'opportunistic';
    /* Android only */
    callbackType?: 'all' | 'first' | 'lost';
    /* Android only */
    matchMode?: 'aggressive' | 'sticky';
    /* Android only */
    numOfMatches?: 'one' | 'few' | 'max';
    /* Android only */
    phy?: '1m' | 'coded' | 'all';
    /* Android only */
    legacy?: boolean;
    /* Android only */
    reportDelay?: number;

    reportDuplicates?: boolean;
}

export interface L2CAPOptions {
    psm: number;
    secureChannel?: boolean;
}

export enum BluetoothEventType {
    LE_SCAN_STARTED = 'LE_SCAN_STARTED',
    LE_SCAN_STOPPED = 'LE_SCAN_STOPPED',
    LE_SCAN_RESULT = 'LE_SCAN_RESULT',
    CONNECTED = 'CONNECTED',
    CONNECTION_STATE_CHANGE = 'CONNECTION_STATE_CHANGE',
    CONNECT_ERROR = 'CONNECT_ERROR',
    DISCONNECTED = 'DISCONNECTED',
    MTU_CHANGED = 'MTU_CHANGED',
    NOTIFICATION_STARTED = 'NOTIFICATION_STARTED',
    NOTIFICATION_STOPPED = 'NOTIFICATION_STOPPED',
    NOTIFICATION_RESULT = 'NOTIFICATION_RESULT',
    READ_RESULT = 'READ_RESULT',
}

export interface BluetoothDisconnectEventData {
    message: string;
}

export interface BluetoothMtuChangeEventData {
    mtu: number;
    status: number;
}

export interface BluetoothConnectionStateChangeEventData {
    status: number;
    newState: number;
}

export interface BluetoothNotificationEventData {
    value: ArrayBuffer;
}

export interface BluetoothReadEventData {
    status: number;
    value: ArrayBuffer;
}

export type BluetoothEventData = PeripheralDataExtended
    | BluetoothDisconnectEventData
    | BluetoothMtuChangeEventData
    | BluetoothConnectionStateChangeEventData
    | BluetoothNotificationEventData
    | BluetoothReadEventData;

export interface BluetoothEvent {
    messageId: number;
    type: BluetoothEventType;
    deviceId: string;
    serviceId?: string;
    characteristicId?: string;
    data?: BluetoothEventData;
}

export type BluetoothEventListener = (ev: BluetoothEvent) => void;

export interface BluetoothWatchEndpoint {
    deviceId: string;
    serviceId: string;
    characteristicId: string;
}

function stringToArrayBuffer(str: string): ArrayBuffer {
    const ret = new Uint8Array(str.length);
    for (var i = 0; i < str.length; i++) {
        ret[i] = str.charCodeAt(i);
    }
    return ret.buffer;
};

function base64ToArrayBuffer(b64: string): ArrayBuffer {
    return stringToArrayBuffer(atob(b64));
};

function massageMessageNativeToJs(message: any): any {
    if (message.CDVType == 'ArrayBuffer') {
        message = base64ToArrayBuffer(message.data);
    }
    return message;
}

// Cordova 3.6 doesn't unwrap ArrayBuffers in nested data structures
// https://github.com/apache/cordova-js/blob/94291706945c42fd47fa632ed30f5eb811080e95/src/ios/exec.js#L107-L122
function convertToNativeJS(object: any): any {
    Object.keys(object).forEach(function (key) {
        var value = object[key];
        object[key] = massageMessageNativeToJs(value);
        if (typeof value === 'object') {
            convertToNativeJS(value);
        }
    });
}

// set of auto-connected device ids
let autoconnected: any = {};

export class L2CAPCordovaInterface {

    constructor(
        public bridge: CordovaBridge = CORDOVA_BRIDGE_DEFAULT
    ) {
    }

    public close(
        deviceId: string, 
        psm: number
    ): Promise<void> {
        return this.bridge.invoke('closeL2Cap', deviceId, psm);
    }

    public open(
        deviceId: string,
        psmOrOptions: number | L2CAPOptions
    ): Promise<void> {
        let psm = psmOrOptions;
        let settings = {};

        if (typeof psmOrOptions === 'object' && 'psm' in psmOrOptions) {
            psm = psmOrOptions.psm;
            settings = psmOrOptions;
        }

        return this.bridge.invoke('openL2Cap', deviceId, psm, settings);
    }

    public receiveData(deviceId: string, psm: number): Promise<ArrayBuffer> {
        return this.bridge.invoke('receiveDataL2Cap', deviceId, psm);
    }

    public write(
        deviceId: string,
        psm: number,
        data: ArrayBuffer
    ): Promise<void> {
        return this.bridge.invoke('writeL2Cap', deviceId, psm, data);
    }
}

export class BLEPluginCordovaInterface {

    public readonly l2cap: L2CAPCordovaInterface;

    constructor(
        public bridge: CordovaBridge = CORDOVA_BRIDGE_DEFAULT
    ) {
        this.l2cap = new L2CAPCordovaInterface(bridge);
    }

    public setEventListener(listener: BluetoothEventListener): void {
        this.bridge.invokeCb(`setEventListener`, listener);
    }

    public removeEventListener(): Promise<void> {
        return this.bridge.invoke(`removeEventListener`);
    }

    public watch(endpoints: BluetoothWatchEndpoint[]): Promise<void> {
        return this.bridge.invoke(`watch`, endpoints);
    }

    public unwatch(endpoints: BluetoothWatchEndpoint[]): Promise<void> {
        return this.bridge.invoke(`unwatch`, endpoints);
    }

    public scan(
        services: string[],
        seconds: number,
        success: (data: PeripheralData) => any,
        failure?: (error: string) => any
    ): void {
        const successWrapper = (peripheral: any) => {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        this.bridge.invokeCb('scan', successWrapper, failure, services, seconds);
    }

    public startScan(
        services: string[],
        success: (data: PeripheralData) => any,
        failure?: (error: string | BLEError) => any
    ): void {
        const successWrapper = (peripheral: any) => {
            convertToNativeJS(peripheral);
            success(peripheral);
        };
        this.bridge.invokeCb('startScan', successWrapper, failure, services);
    }

    public stopScan(): Promise<void> {
        return this.bridge.invoke('stopScan');
    }

    public startScanWithOptions(
        services: string[],
        options: StartScanOptions,
        success: (data: PeripheralData) => any,
        failure?: (error: string) => any
    ): void {
        const successWrapper = (peripheral: any) => {
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
    public connectedPeripheralsWithServices(services: string[]): Promise<PeripheralData[]> {
        return this.bridge.invoke('connectedPeripheralsWithServices', services);
    }

    /**
     * Find known (but not necessarily connected) peripherals offering the listed device UUIDs.
     * This function wraps CBCentralManager.retrievePeripheralsWithIdentifiers
     * [Android] peripheralsWithIdentifiers is not supported on Android.
     */
    public peripheralsWithIdentifiers(deviceIds: string[]): Promise<PeripheralData[]> {
        return this.bridge.invoke('peripheralsWithIdentifiers', deviceIds);
    }

    /**
     * Find the bonded devices.
     * [iOS] bondedDevices is not supported on iOS.
     */
    public bondedDevices(): Promise<PeripheralData[]> {
        return this.bridge.invoke('bondedDevices');
    }

    /* Lists all peripherals discovered by the plugin due to scanning or connecting since app launch.
    [iOS] list is not supported on iOS. */
    public list(): Promise<PeripheralData[]> {
        return this.bridge.invoke('list');
    }

    public connect(
        deviceId: string,
        connectCallback: (data: PeripheralDataExtended) => any,
        disconnectCallback: (error: string | BLEError) => any
    ): void {
        const successWrapper = (peripheral: any) => {
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
    public autoConnect(
        deviceId: string,
        connectCallback: (data: PeripheralDataExtended) => any,
        disconnectCallback: (error: string | BLEError) => any
    ): void {

        let disconnectCallbackWrapper: (peripheral: any) => void;
        autoconnected[deviceId] = true;

        // wrap connectCallback so nested array buffers in advertising info are handled correctly
        const connectCallbackWrapper = (peripheral: any) => {
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
        } else {
            // no wrapper for Android
            disconnectCallbackWrapper = disconnectCallback;
        }

        this.bridge.invokeCb('autoConnect', connectCallbackWrapper, disconnectCallbackWrapper, deviceId);
    }

    public disconnect(deviceId: string): Promise<void> {
        try {
            delete autoconnected[deviceId];
        } catch (e) {
            // ignore error
        }
        return this.bridge.invoke('disconnect', deviceId);
    }

    public queueCleanup(deviceId: string): Promise<void> {
        return this.bridge.invoke('queueCleanup', deviceId);
    }

    /**
     * sets the pin when device requires it.
     * [iOS] setPin is not supported on iOS.
     */
    public setPin(pin: string): Promise<void> {
        return this.bridge.invoke('setPin', pin);
    }

    /**
     * May be used to request (on Android) a larger MTU size to be able to send more data at once
     * [iOS] requestMtu is not supported on iOS.
     */
    public requestMtu(deviceId: string, mtu: number): Promise<void> {
        return this.bridge.invoke('requestMtu', deviceId, mtu);
    }

    /**
     * When Connecting to a peripheral android can request for the connection priority for faster communication.
     * [iOS] requestConnectionPriority is not supported on iOS.
     */
    public requestConnectionPriority(
        deviceId: string,
        priority: ConnectionPriority
    ): Promise<void> {
        return this.bridge.invoke('requestConnectionPriority', deviceId, priority);
    }

    /**
     * Clears cached services and characteristics info for some poorly behaved devices.
     * Uses an undocumented API, so it is not guaranteed to work.
     * [iOS] refreshDeviceCache is not supported on iOS.
     */
    public refreshDeviceCache(
        deviceId: string,
        timeoutMillis: number
    ): Promise<PeripheralDataExtended> {
        return this.bridge.invoke('refreshDeviceCache', deviceId, timeoutMillis);
    }

    public read(
        deviceId: string,
        serviceUuid: string,
        characteristicUuid: string
    ): Promise<ArrayBuffer> {
        return this.bridge.invoke('read', deviceId, serviceUuid, characteristicUuid);
    }

    public readRSSI(deviceId: string): Promise<number> {
        return this.bridge.invoke('readRSSI', deviceId);
    }

    public write(
        deviceId: string,
        serviceUuid: string,
        characteristicUuid: string,
        data: ArrayBuffer
    ): Promise<void> {
        return this.bridge.invoke('write', deviceId, serviceUuid, characteristicUuid, data);
    }

    /**
     * Writes data to a characteristic without a response from the peripheral. 
     * You are not notified if the write fails in the BLE stack.
     * The success callback is be called when the characteristic is written.
     */
    public writeWithoutResponse(
        deviceId: string,
        serviceUuid: string,
        characteristicUuid: string,
        data: ArrayBuffer
    ): Promise<void> {
        return this.bridge.invoke('writeWithoutResponse', deviceId, serviceUuid, characteristicUuid, data);
    }

    /**
     * Start notifications on the given characteristic
     * - options
     *      emitOnRegistered  - Default is false. Emit "registered" to success callback 
     *                          when peripheral confirms notifications are active
     */
    public startNotification(
        deviceId: string,
        serviceUuid: string,
        characteristicUuid: string,
        success: (rawData: ArrayBuffer | 'registered') => any,
        failure: (error: string | BLEError) => any,
        options?: { emitOnRegistered: boolean }
    ): void {

        const emitOnRegistered = options && options.emitOnRegistered == true;

        function onEvent(data: any) {
            if (data === 'registered') {
                // For backwards compatibility, don't emit the registered event unless explicitly instructed
                if (emitOnRegistered) success(data);
            } else {
                success(data);
            }
        }

        this.bridge.invokeCb('startNotification', onEvent, failure, deviceId, serviceUuid, characteristicUuid);
    }

    public stopNotification(
        deviceId: string,
        serviceUuid: string,
        characteristicUuid: string,
    ): Promise<void> {
        return this.bridge.invoke('stopNotification', deviceId, serviceUuid, characteristicUuid);
    }

    /**
     * Calls the success callback when the peripheral is connected and the failure callback when not connected.
     */
    public isConnected(deviceId: string): Promise<void> {
        return this.bridge.invoke('isConnected', deviceId);
    }

    public testConnected(deviceId: string): Promise<boolean> {
        return this.isConnected(deviceId)
            .then(() => true)
            .catch(() => false);
    }

    /**
     * Reports if bluetooth is enabled.
     */
    public isEnabled(): Promise<void> {
        return this.bridge.invoke('isEnabled');
    }

    public testEnabled(): Promise<boolean> {
        return this.isEnabled()
            .then(() => true)
            .catch(() => false);
    }

    /**
     * Reports if location services are enabled.
     * [iOS] isLocationEnabled is not supported on iOS.
     */
    public isLocationEnabled(): Promise<void> {
        return this.bridge.invoke('isLocationEnabled');
    }

    public testLocationEnabled(): Promise<boolean> {
        return this.isLocationEnabled()
            .then(() => true)
            .catch(() => false);
    }

    /**
     * Enable Bluetooth on the device.
     * [iOS] enable is not supported on iOS.
     */
    public enable(): Promise<void> {
        return this.bridge.invoke('enable');
    }

    /**
     * Opens the Bluetooth settings for the operating systems.
     * [iOS] showBluetoothSettings is not supported on iOS.
     */
    public showBluetoothSettings(): Promise<void> {
        return this.bridge.invoke('showBluetoothSettings');
    }

    /**
     * Registers a change listener for location-related services.
     * [iOS] startLocationStateNotifications is not supported on iOS. 
     */
    public startLocationStateNotifications(
        change: (isLocationEnabled: boolean) => any,
        failure?: (error: string) => any
    ): void {
        this.bridge.invokeCb('startLocationStateNotifications', change, failure);
    }

    public stopLocationStateNotifications(): Promise<void> {
        return this.bridge.invoke('stopLocationStateNotifications');
    }

    /**
     * Registers a change listener for Bluetooth adapter state changes
     */
    public startStateNotifications(
        success: (state: string) => any, 
        failure?: (error: string) => any
    ): void {
        this.bridge.invokeCb('startStateNotifications', success, failure);
    }

    public stopStateNotifications(): Promise<void> {
        return this.bridge.invoke('stopStateNotifications');
    }

    /**
     * Reports the BLE restoration status if the app was restarted by iOS as a result of a BLE event.
     * See https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html#//apple_ref/doc/uid/TP40013257-CH7-SW10
     * [Android] restoredBluetoothState is not supported on Android.
     */
    public restoredBluetoothState(): Promise<RestoredState> {
        return this.bridge.invoke('restoredBluetoothState');
    }
}

export const BLE = new BLEPluginCordovaInterface();
