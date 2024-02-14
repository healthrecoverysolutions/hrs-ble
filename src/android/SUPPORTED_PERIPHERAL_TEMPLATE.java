package com.megster.cordova.ble.central;

import android.bluetooth.BluetoothDevice;
import android.util.Pair;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import timber.log.Timber;

public class SUPPORTED_PERIPHERAL_TEMPLATE {

    IR20("Foracare IR20B", "temperature","(.*IR20.*$).*"),
    TD1107("Taidoc TD1107", "temperature","(.*TD1107.*$).*"),
    Taidoc_Device("Taidoc-Device", "temperature","(.*Taidoc-Device.*$).*"),
    TNG_SCALE("TNG 550", "weight","(.*TNG SCALE.*$).*"),
    UC_351("A&D UC-351", "weight","(.*UC-351.*$).*"),
    UC_352("A&D UC-352 BLE", "weight","(.*UC-352.*$).*"),
    UC_355("A&D UC-355", "weight", "(.*UC-355.*$).*"),
    WELCH_SC100("Welch Allyn Scale", "weight","(.*SC100.*$).*"),
    TD8255("Taidoc TD8255", "pulseox","(.*TD8255.*$).*"),
    TNG_SPO2("Foracare TNG SP02", "pulseox","(.*SPO2.*$).*"),
    Nonin_Medical("Nonin Medical Inc 9560", "pulseox","(.*Nonin_Medical.*$).*"),
    Nonin3230("Nonin 3230", "pulseox", "(^Nonin3230.*$).*"),
    Nipro("NiproBGM", "glucose","(.*Nipro*$).*"),
    TRUEAIR("TRUEAIR", "glucose","(.*TRUEAIR*$).*"),
    TEST_N_GO("TEST-N-GO", "glucose","(.*TEST-N-GO*$).*"),
    UA_651("A&D UA-651", "bloodpressure","(.*UA-651.*$).*"), // A&D_UA-651BLE_B5583A
    UA_767("A&D UA-767", "bloodpressure", "(.*UA-767.*$).*"),
    WELCH_BP("Welch Allyn BP Monitor", "bloodpressure","(.*BP100.*$).*"),
    FORA_TNG_BGM("TNG", "glucose", "TNG"); // as TNG SCALE has the name same as TNG, this should be placed at the last. values() will return array in the order they are declared

    private String display;
    private String peripheralType;
    public Pattern namePattern;

    SUPPORTED_PERIPHERAL_TEMPLATE(String display, String peripheralType, String patternStr) {
        this.display = display;
        this.peripheralType = peripheralType;
        this.namePattern = getNamePattern(patternStr);
    }

    public static SUPPORTED_PERIPHERAL_TEMPLATE findMatchingDevice(BluetoothDevice device) {
        if (device != null) {
              String deviceName = device.getName();
//            Timber.i("Text ------> " + text);
            if (deviceName != null) {
                for (SUPPORTED_PERIPHERAL_TEMPLATE b : SUPPORTED_PERIPHERAL_TEMPLATE.values()) {
                    if (b.namePattern.matcher(deviceName).matches()) {
                        Timber.i("Found supported device " + device + " which matches enum value " + b.getDisplay());
                        return b;
                    }
                }
            }
            // return false;
            return null;
        }

        public String getDisplay() {
            return display;
        }

        public String getPeripheralType() {
            return peripheralType;
        }
        return null;
    }

    public String getDisplay() {
        return display;
    }

    public String getPeripheralType() {
        return peripheralType;
    }

    /**
     * get name pattern for device
     *
     * @param
     * @return Pattern - name pattern
     */
    private static Pattern getNamePattern(String pattern) {

        try {
            if (pattern!=null) {
                /*Check if the pattern is case in sensitive*/
                if (pattern.indexOf("/") == 0 && pattern.contains("/i")) {
                    pattern = pattern.substring(1, pattern.indexOf("/i"));
                    pattern = quotePattern(pattern);
                    System.out.println("using case insensitive namePattern: " + pattern);
                    // Logger.log(true, "i", TAG, "using case insensitive namePattern: " + pattern);
                    return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                }
                System.out.println("using namePattern: " + pattern);
                // Logger.log(true, "i", TAG, "using namePattern: " + pattern);
                pattern = quotePattern(pattern);
                return Pattern.compile(pattern);
            }
        } catch (PatternSyntaxException e) {
            // e.printStacktrace();
            System.out.println("PatternSyntaxException in getNamePattern(): " + e);
            // Logger.log(true, "e", TAG, "PatternSyntaxException in getNamePattern(): " + e);
        }
        return null;
    }

    /**
     * Apply quotes if pattern is not compilable
     * */
    private static String quotePattern(String namePattern) {
        namePattern = Pattern.quote(namePattern);
        if (namePattern.startsWith("\\Q") && namePattern.endsWith("\\E")) {
            namePattern = namePattern.substring(2, namePattern.length() - 2);
        }
        return namePattern;
    }

//
//    IR20("IR20", "temperature"),
//    TD1107("TD1107", "temperature"),
//    Taidoc_Device("Taidoc-Device", "temperature"),
//    TNG_SCALE("TNG SCALE", "weight"),
//    UC_351("UC-351", "weight"),
//    UC_352("UC-352", "weight"),
//    UC_355("UC-355", "weight"),
//    SC100("SC100", "weight"),
//    TD8255("TD8255", "pulseox"),
//    SPO2("SPO2", "pulseox"),
//    Nonin_Medical("Nonin_Medical", "pulseox"),
//    Nonin3230("Nonin3230", "pulseox"),
//    Nipro("Nipro", "glucose"),
//    TRUEAIR("TRUEAIR", "glucose"),
//    TEST_N_GO("TEST-N-GO", "glucose"),
//    UA_651("UA-651", "bloodpressure"), // A&D_UA-651BLE_B5583A
//    UA_767("UA-767", "bloodpressure"),
//    BP100("BP100", "bloodpressure"),
//    TNG("TNG", "bloodpressure"); // as TNG SCALE has the name same as TNG, this should be placed at the last. values() will return array in the order they are declared
//
//    private String display;
//    private String peripheralType;
//
//    SUPPORTED_PERIPHERAL_TEMPLATE(String display, String peripheralType) {
//        this.display = display;
//        this.peripheralType = peripheralType;
//    }
//    public static SUPPORTED_PERIPHERAL_TEMPLATE findMatchingDevice(BluetoothDevice device) {
//        if (device != null) {
//            String text = device.getName();
//            Timber.i("Text ------> " + text);
//            if (text != null) {
//                for (SUPPORTED_PERIPHERAL_TEMPLATE b : SUPPORTED_PERIPHERAL_TEMPLATE.values()) {
//                    // TNG SCALE contain TNG --> true
//                    if (text.contains(b.getDisplay())) {
//                        Timber.i("Found supported device " + text + " which matches enum value " + b.getDisplay());
//                        return b;
//                    }
//                }
//            }
//        }
//        // return false;
//        return null;
//    }
//
//    public String getDisplay() {
//        return display;
//    }
//
//    public String getPeripheralType() {
//        return peripheralType;
//    }


}

//
//
//    const templateDevices: TemplateDevices = {
//        bloodpressure: [
//        {
//            name: 'UA-651',
//                display: 'A&D UA-651',
//            hrsTabOnly: false
//        },
//        {
//            name: 'UA-767',
//                display: 'A&D UA-767',
//            hrsTabOnly: true
//        },
//        {
//            name: 'BP100',
//                display: 'Welch Allyn BP Monitor',
//            hrsTabOnly: false
//        }
//    ],
//        glucose: [
//        {
//            name: 'Nipro',
//                display: 'NiproBGM',
//            hrsTabOnly: false
//        },
//        {
//            name: 'TNG',
//                display: 'TNG',
//            hrsTabOnly: false
//        },
//        {
//            name: 'TRUEAIR',
//                display: 'TRUEAIR',
//            hrsTabOnly: false
//        },
//        {
//            name: 'TEST-N-GO',
//                display: 'TEST-N-GO',
//            hrsTabOnly: true
//        }
//    ],
//        pulseox: [
//        {
//            name: 'Nonin3230',
//                display: 'Nonin 3230',
//            hrsTabOnly: false
//        },
//        {
//            name: 'TNG SPO2',
//                display: 'Foracare TNG SP02',
//            hrsTabOnly: false
//        },
//        {
//            name: 'TAIDOC TD8255',
//                display: 'Taidoc TD8255',
//            hrsTabOnly: false
//        },
//        {
//            name: 'Nonin_Medical',
//                display: 'Nonin Medical Inc 9560',
//            hrsTabOnly: true
//        }
//    ],
//        weight: [
//        {
//            name: 'TNG SCALE',
//                display: 'TNG 550',
//            hrsTabOnly: false
//        },
//        {
//            name: 'UC-352',
//                display: 'A&D UC-352 BLE',
//            hrsTabOnly: false
//        },
//        {
//            name: 'UC-351',
//                display: 'A&D UC-351',
//            hrsTabOnly: true
//        },
//        {
//            name: 'UC-355',
//                display: 'A&D UC-355',
//            hrsTabOnly: true
//        },
//        {
//            name: 'SC100',
//                display: 'Welch Allyn Scale',
//            hrsTabOnly: false
//        }
//    ],
//        temperature: [
//        {
//            name: 'IR20',
//                display: 'Foracare IR20B',
//            hrsTabOnly: false
//        },
//        {
//            name: 'TD1107',
//                display: 'Taidoc TD1107',
//            hrsTabOnly: false
//        },
//        {
//            name: 'Taidoc-Device',
//                display: 'Taidoc-Device',
//            hrsTabOnly: true
//        }
//    ]
//    };
//
//    export type TemplateDevices = {
//    [key in PeripheralTypes]: TemplateDevice[];
//    };
//
//    export type TemplateDevice = {
//        name: string;
//        display: string;
//        hrsTabOnly: boolean;
//    };
//
//    export type PeripheralTypes = 'bloodpressure' | 'glucose' | 'pulseox' | 'weight' | 'temperature';
//
//    export const fitbitDevice = {
//        type: 'FitBit',
//            imageURL: '/../../../assets/icon/Fitbit_logo_RGB.svg'
//    };
//
//    export const activityDevices = [
//    fitbitDevice
//];
//
//    export interface ActivityDevice {
//        type: string,
//        name?: string,
//        imageURL: string
//    }

