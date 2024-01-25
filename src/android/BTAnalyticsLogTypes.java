package com.megster.cordova.ble.central;

public enum BTAnalyticsLogTypes {
    BT_CONNECTION("BT_CONNECTION"),
    BT_PAIRING_SUCCESS("BT_PAIRING_SUCCESS"),
    BT_PAIRING_FAILURE("BT_PAIRING_FAILURE"),
    BT_READING_SUCCESS("BT_READING_SUCCESS"),
    BT_READING_FAILURE("BT_READING_ERROR"),
    BT_WRITE_SUCCESS("BT_WRITE_SUCCESS"),
    BT_WRITE_FAILURE("BT_WRITE_ERROR");

    private final String text;

    BTAnalyticsLogTypes(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}