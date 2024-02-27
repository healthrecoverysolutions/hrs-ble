package com.megster.cordova.ble.central;

public enum BTAnalyticsLogTypes {
    BT_CONNECTION("BT_CONNECTION"),
    BT_PAIRING_SUCCESS("BT_PAIRING_SUCCESS"),
    BT_PAIRING_FAILURE("BT_PAIRING_FAILURE");

    private final String text;

    BTAnalyticsLogTypes(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
