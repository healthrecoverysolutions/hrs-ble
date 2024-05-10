import Foundation
import CoreBluetooth

enum SupportedPeripherals: CaseIterable{
    
    case IR20
    case TD1107
    case TaidocDevice
    case TNGScale
    case UCAD351
    case UCAD352
    case UCAD355
    case WelchSC100
    case TD8255
    case TNGSpo2
    case NoninMedical
    case Nonin3230
    case Nipro
    case TRUEAIR
    case TESTNEGO
    case UAAD651
    case UAAD767
    case WelchBP
    case FORATNGBGM
    
    var rawValue: (display: String, peripheralType: String, namePattern: String) {
      get {
        switch self {
        case .IR20:
          return ("Foracare IR20B", "temperature", "(.*IR20.*$).*")
        case .TD1107:
          return ("Taidoc TD1107", "temperature", "(.*TD1107.*$).*")
        case .TaidocDevice:
          return ("Taidoc-Device", "temperature", "(.*Taidoc-Device.*$).*")
        case .TNGScale:
          return ("TNG 550", "weight", "(.*TNG SCALE.*$).*")
        case .UCAD351:
          return ("A&D UC-351", "weight", "(.*UC-351.*$).*")
        case .UCAD352:
          return ("A&D UC-352 BLE", "weight", "(.*UC-352.*$).*")
        case .UCAD355:
          return ("A&D UC-355", "weight", "(.*UC-355.*$).*")
        case .WelchSC100:
          return ("Welch Allyn Scale", "weight", "(.*SC100.*$).*")
        case .TD8255:
          return ("TD8255", "pulseox", "(.*TD8255.*$).*")
        case .TNGSpo2:
          return ("Foracare TNG SP02", "pulseox", "(.*SPO2.*$).*")
        case .NoninMedical:
          return ("Nonin Medical Inc 9560", "pulseox", "(.*Nonin_Medical.*$).*")
        case .Nonin3230:
          return ("Nonin 3230", "pulseox", "(^Nonin3230.*$).*")
        case .Nipro:
          return ("NiproBGM", "glucose", "(.*Nipro*$).*")
        case .TRUEAIR:
          return ("TRUEAIR", "glucose", "(.*TRUEAIR*$).*")
        case .TESTNEGO:
          return ("TEST-N-GO", "glucose", "(.*TEST-N-GO*$).*")
        case .UAAD651:
          return ("A&D UA-651", "bloodpressure", "(.*UA-651.*$).*")
        case .UAAD767:
          return ("A&D UA-767", "bloodpressure", "(.*UA-767.*$).*")
        case .WelchBP:
          return ("Welch Allyn BP Monitor", "bloodpressure", "(.*BP100.*$).*")
        case .FORATNGBGM:
          return ("TNG", "glucose", "TNG")
        }
      }
    }

    static func findMatchingDevice(_ device: CBPeripheral?) -> SupportedPeripherals? {
        guard let device = device, let deviceName = device.name else {
            return nil
        }

        for peripheral in SupportedPeripherals.allCases{
            if deviceName.matches(peripheral.rawValue.namePattern){
                print("Found supported device \(device) which matches enum value \(peripheral.rawValue.display)")
                return peripheral
            }
        }

        return nil
    }
}

extension String {
    func matches(_ regex: String) -> Bool {
        return self.range(of: regex, options: .regularExpression, range: nil, locale: nil) != nil
    }
}
