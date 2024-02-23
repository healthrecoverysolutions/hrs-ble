package com.megster.cordova.ble.central;

import android.bluetooth.BluetoothDevice;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import timber.log.Timber;

public enum SupportedPeripherals {

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

    SupportedPeripherals(String display, String peripheralType, String patternStr) {
        this.display = display;
        this.peripheralType = peripheralType;
        this.namePattern = getNamePattern(patternStr);
    }

    public static SupportedPeripherals findMatchingDevice(BluetoothDevice device) {
        if (device != null) {
              String deviceName = device.getName();
            if (deviceName != null) {
                for (SupportedPeripherals b : SupportedPeripherals.values()) {
                    if (b.namePattern.matcher(deviceName).matches()) {
                        Timber.i("Found supported device " + device + " which matches enum value " + b.getDisplay());
                        return b;
                    }
                }
            }
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

}



