//
//  PeripheralInformation.swift
//  PatientConnect Mobile
//
//  Created by Anubhav Saxena on 08/05/24.
//

import UIKit
import CoreBluetooth

@objc class PeripheralInformation: NSObject {
    @objc public static func findMatchingDevice(_ device: CBPeripheral?) -> NSDictionary? {
        if let foundPeriheral = SupportedPeripherals.findMatchingDevice(device){
            return ["DeviceName": foundPeriheral.rawValue.display, "DeviceType": foundPeriheral.rawValue.peripheralType]
        }
        return nil
    }
}
