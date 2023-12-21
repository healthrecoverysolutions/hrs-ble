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
    scanMode?: 'lowPower' | 'balanced' | 'lowLatency' | 'opportunistic';
    callbackType?: 'all' | 'first' | 'lost';
    matchMode?: 'aggressive' | 'sticky';
    numOfMatches?: 'one' | 'few' | 'max';
    phy?: '1m' | 'coded' | 'all';
    legacy?: boolean;
    reportDelay?: number;
    reportDuplicates?: boolean;
}
export interface L2CAPOptions {
    psm: number;
    secureChannel?: boolean;
}
export declare class L2CAPCordovaInterface {
    close(deviceId: string, psm: number): Promise<void>;
    open(deviceId: string, psmOrOptions: number | L2CAPOptions): Promise<void>;
    receiveData(deviceId: string, psm: number): Promise<ArrayBuffer>;
    write(deviceId: string, psm: number, data: ArrayBuffer): Promise<void>;
}
export declare class BLEPluginCordovaInterface {
    readonly l2cap: L2CAPCordovaInterface;
    scan(services: string[], seconds: number, success: (data: PeripheralData) => any, failure?: (error: string) => any): void;
    startScan(services: string[], success: (data: PeripheralData) => any, failure?: (error: string | BLEError) => any): void;
    stopScan(): Promise<void>;
    startScanWithOptions(services: string[], options: StartScanOptions, success: (data: PeripheralData) => any, failure?: (error: string) => any): void;
    /**
     * Find connected peripherals offering the listed service UUIDs.
     * This function wraps CBCentralManager.retrieveConnectedPeripheralsWithServices.
     * [Android] peripheralsWithIdentifiers is not supported on Android.
     */
    connectedPeripheralsWithServices(services: string[]): Promise<PeripheralData[]>;
    /**
     * Find known (but not necessarily connected) peripherals offering the listed device UUIDs.
     * This function wraps CBCentralManager.retrievePeripheralsWithIdentifiers
     * [Android] peripheralsWithIdentifiers is not supported on Android.
     */
    peripheralsWithIdentifiers(deviceIds: string[]): Promise<PeripheralData[]>;
    /**
     * Find the bonded devices.
     * [iOS] bondedDevices is not supported on iOS.
     */
    bondedDevices(): Promise<PeripheralData[]>;
    list(): Promise<PeripheralData[]>;
    connect(deviceId: string, connectCallback: (data: PeripheralDataExtended) => any, disconnectCallback: (error: string | BLEError) => any): void;
    /**
     * Automatically connect to a device when it is in range of the phone
     * [iOS] background notifications on ios must be enabled if you want to run in the background
     * [Android] this relies on the autoConnect argument of BluetoothDevice.connectGatt().
     * Not all Android devices implement this feature correctly.
     */
    autoConnect(deviceId: string, connectCallback: (data: PeripheralDataExtended) => any, disconnectCallback: (error: string | BLEError) => any): void;
    disconnect(deviceId: string): Promise<void>;
    queueCleanup(deviceId: string): Promise<void>;
    /**
     * sets the pin when device requires it.
     * [iOS] setPin is not supported on iOS.
     */
    setPin(pin: string): Promise<void>;
    /**
     * May be used to request (on Android) a larger MTU size to be able to send more data at once
     * [iOS] requestMtu is not supported on iOS.
     */
    requestMtu(deviceId: string, mtu: number): Promise<void>;
    /**
     * When Connecting to a peripheral android can request for the connection priority for faster communication.
     * [iOS] requestConnectionPriority is not supported on iOS.
     */
    requestConnectionPriority(deviceId: string, priority: ConnectionPriority): Promise<void>;
    /**
     * Clears cached services and characteristics info for some poorly behaved devices.
     * Uses an undocumented API, so it is not guaranteed to work.
     * [iOS] refreshDeviceCache is not supported on iOS.
     */
    refreshDeviceCache(deviceId: string, timeoutMillis: number): Promise<PeripheralDataExtended>;
    read(deviceId: string, serviceUuid: string, characteristicUuid: string): Promise<ArrayBuffer>;
    readRSSI(deviceId: string): Promise<number>;
    write(deviceId: string, serviceUuid: string, characteristicUuid: string, data: ArrayBuffer): Promise<void>;
    /**
     * Writes data to a characteristic without a response from the peripheral.
     * You are not notified if the write fails in the BLE stack.
     * The success callback is be called when the characteristic is written.
     */
    writeWithoutResponse(deviceId: string, serviceUuid: string, characteristicUuid: string, data: ArrayBuffer): Promise<void>;
    /**
     * Start notifications on the given characteristic
     * - options
     *      emitOnRegistered  - Default is false. Emit "registered" to success callback
     *                          when peripheral confirms notifications are active
     */
    startNotification(deviceId: string, serviceUuid: string, characteristicUuid: string, success: (rawData: ArrayBuffer | 'registered') => any, failure: (error: string | BLEError) => any, options?: {
        emitOnRegistered: boolean;
    }): void;
    stopNotification(deviceId: string, serviceUuid: string, characteristicUuid: string): Promise<void>;
    /**
     * Calls the success callback when the peripheral is connected and the failure callback when not connected.
     */
    isConnected(deviceId: string): Promise<void>;
    testConnected(deviceId: string): Promise<boolean>;
    /**
     * Reports if bluetooth is enabled.
     */
    isEnabled(): Promise<void>;
    testEnabled(): Promise<boolean>;
    /**
     * Reports if location services are enabled.
     * [iOS] isLocationEnabled is not supported on iOS.
     */
    isLocationEnabled(): Promise<void>;
    testLocationEnabled(): Promise<boolean>;
    /**
     * Enable Bluetooth on the device.
     * [iOS] enable is not supported on iOS.
     */
    enable(): Promise<void>;
    /**
     * Opens the Bluetooth settings for the operating systems.
     * [iOS] showBluetoothSettings is not supported on iOS.
     */
    showBluetoothSettings(): Promise<void>;
    /**
     * Registers a change listener for location-related services.
     * [iOS] startLocationStateNotifications is not supported on iOS.
     */
    startLocationStateNotifications(change: (isLocationEnabled: boolean) => any, failure?: (error: string) => any): void;
    stopLocationStateNotifications(): Promise<void>;
    /**
     * Registers a change listener for Bluetooth adapter state changes
     */
    startStateNotifications(success: (state: string) => any, failure?: (error: string) => any): void;
    stopStateNotifications(): Promise<void>;
    /**
     * Reports the BLE restoration status if the app was restarted by iOS as a result of a BLE event.
     * See https://developer.apple.com/library/archive/documentation/NetworkingInternetWeb/Conceptual/CoreBluetooth_concepts/CoreBluetoothBackgroundProcessingForIOSApps/PerformingTasksWhileYourAppIsInTheBackground.html#//apple_ref/doc/uid/TP40013257-CH7-SW10
     * [Android] restoredBluetoothState is not supported on Android.
     */
    restoredBluetoothState(): Promise<RestoredState>;
}
export declare const BLE: BLEPluginCordovaInterface;
