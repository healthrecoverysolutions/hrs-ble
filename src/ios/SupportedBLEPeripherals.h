//
//  SupportedPeripherals.h
//  PatientConnect Mobile
//
//  Created by Anubhav Saxena on 10/05/24.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface SupportedBLEPeripherals : NSObject
+ (SupportedBLEPeripherals *)findMatchingDevice:(CBPeripheral *)device;
+ (NSDictionary *)findMatchingDeviceInfo:(CBPeripheral *)device;
@end

NS_ASSUME_NONNULL_END
