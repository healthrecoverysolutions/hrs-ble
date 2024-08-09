//
//  SupportedBLEPeripherals.m
//  PatientConnect Mobile
//
//  Created by Anubhav Saxena on 10/05/24.
//

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import "CocoaLumberjack.h"

static const DDLogLevel ddLogLevel = DDLogLevelInfo;

@interface SupportedBLEPeripherals : NSObject

+ (SupportedBLEPeripherals *)findMatchingDevice:(CBPeripheral *)device;

@property (nonatomic, readonly) NSString *display;
@property (nonatomic, readonly) NSString *peripheralType;
@property (nonatomic, readonly) NSRegularExpression *namePattern;

+ (NSArray<SupportedBLEPeripherals *> *)allValues;

@end

@implementation SupportedBLEPeripherals

+ (NSDictionary *)findMatchingDeviceInfo:(CBPeripheral *)device {
    SupportedBLEPeripherals *foundPeripheral = [SupportedBLEPeripherals findMatchingDevice:device];
    if (foundPeripheral) {
        return @{
            @"DeviceName": foundPeripheral.display,
            @"DeviceType": foundPeripheral.peripheralType
        };
    }
    return nil;
}

+ (SupportedBLEPeripherals *)findMatchingDevice:(CBPeripheral *)device {
    if (device != nil) {
        NSString *deviceName = device.name;
        if (deviceName != nil) {
            for (SupportedBLEPeripherals *b in [SupportedBLEPeripherals allValues]) {
                if([b.namePattern matchesInString:deviceName options:0 range:NSMakeRange(0, [deviceName length])].count > 0){
                    DDLogInfo(@"Found supported device %@ which matches enum value %@", device, b.display);
                    return b;
                }
            }
        }
        return nil;
    }
    return nil;
}

+ (NSArray<SupportedBLEPeripherals *> *)allValues {
    static NSArray<SupportedBLEPeripherals *> *allValues = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        allValues = @[
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"Foracare IR20B" peripheralType:@"temperature" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*IR20.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"Taidoc TD1107" peripheralType:@"temperature" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*TD1107.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"Taidoc-Device" peripheralType:@"temperature" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*Taidoc-Device.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"TNG 550" peripheralType:@"weight" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*TNG SCALE.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"A&D UC-351" peripheralType:@"weight" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*UC-351.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"A&D UC-352 BLE" peripheralType:@"weight" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*UC-352.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"A&D UC-355" peripheralType:@"weight" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*UC-355.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"Welch Allyn Scale" peripheralType:@"weight" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*SC100.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"Taidoc TD8255" peripheralType:@"pulseox" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*TD8255.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"Foracare TNG SP02" peripheralType:@"pulseox" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*SPO2.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"Nonin Medical Inc 9560" peripheralType:@"pulseox" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*Nonin_Medical.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"Nonin 3230" peripheralType:@"pulseox" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(^Nonin3230.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"NiproBGM" peripheralType:@"glucose" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*Nipro.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"TRUEAIR" peripheralType:@"glucose" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*TRUEAIR*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"TEST-N-GO" peripheralType:@"glucose" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*TEST-N-GO*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"A&D UA-651" peripheralType:@"bloodpressure" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*UA-651.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"A&D UA-767" peripheralType:@"bloodpressure" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*UA-767.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"Welch Allyn BP Monitor" peripheralType:@"bloodpressure" namePattern:[NSRegularExpression regularExpressionWithPattern:@"(.*BP100.*$).*" options:0 error:nil]],
            [[SupportedBLEPeripherals alloc] initWithDisplay:@"TNG" peripheralType:@"glucose" namePattern:[NSRegularExpression regularExpressionWithPattern:@"TNG" options:0 error:nil]]
        ];
    });
    return allValues;
}

- (instancetype)initWithDisplay:(NSString *)display peripheralType:(NSString *)peripheralType namePattern:(NSRegularExpression *)namePattern {
    self = [super init];
    if (self) {
        _display = display;
        _peripheralType = peripheralType;
        _namePattern = namePattern;
    }
    return self;
}

@end
