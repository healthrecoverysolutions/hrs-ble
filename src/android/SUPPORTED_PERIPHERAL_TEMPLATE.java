package com.megster.cordova.ble.central;

import android.bluetooth.BluetoothDevice;

import timber.log.Timber;

public enum SUPPORTED_PERIPHERAL_TEMPLATE {

    IR20("IR20", "temperature"),
    TD1107("TD1107", "temperature"),
    Taidoc_Device("Taidoc-Device", "temperature"),
    TNG_SCALE("TNG SCALE", "weight"),
    UC_351("UC-351", "weight"),
    UC_352("UC-352", "weight"),
    UC_355("UC-355", "weight"),
    SC100("SC100", "weight"),
    TD8255("TD8255", "pulseox"),
    SPO2("SPO2", "pulseox"),
    Nonin_Medical("Nonin_Medical", "pulseox"),
    Nonin3230("Nonin3230", "pulseox"),
    Nipro("Nipro", "glucose"),
    TRUEAIR("TRUEAIR", "glucose"),
    TEST_N_GO("TEST-N-GO", "glucose"),
    UA_651("UA-651", "bloodpressure"), // A&D_UA-651BLE_B5583A
    UA_767("UA-767", "bloodpressure"),
    BP100("BP100", "bloodpressure"),
    TNG("TNG", "bloodpressure"); // as TNG SCALE has the name same as TNG, this should be placed at the last. values() will return array in the order they are declared

    private String display;
    private String peripheralType;

    SUPPORTED_PERIPHERAL_TEMPLATE(String display, String peripheralType) {
        this.display = display;
        this.peripheralType = peripheralType;
    }
    public static SUPPORTED_PERIPHERAL_TEMPLATE findMatchingDevice(BluetoothDevice device) {
        if (device != null) {
            String text = device.getName();
            Timber.i("Text ------> " + text);
            if (text != null) {
                for (SUPPORTED_PERIPHERAL_TEMPLATE b : SUPPORTED_PERIPHERAL_TEMPLATE.values()) {
                    // TNG SCALE contain TNG --> true
                    if (text.contains(b.getDisplay())) {
                        Timber.i("Found supported device " + text + " which matches enum value " + b.getDisplay());
                        return b;
                    }
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


}




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

//    export type PeripheralTypes = 'bloodpressure' | 'glucose' | 'pulseox' | 'weight' | 'temperature';


