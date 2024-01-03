package com.megster.cordova.ble.central

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import androidx.annotation.RequiresApi
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import timber.log.Timber
import java.io.IOException
import java.util.Arrays
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class L2CAPContext(private val device: BluetoothDevice, private val psm: Int) {
    private val updateLock = Any()
    private val executor: ExecutorService
    private var socket: BluetoothSocket? = null
    private var l2capReceiver: CallbackContext? = null
    private var l2capConnectContext: CallbackContext? = null

    init {
        executor = Executors.newSingleThreadExecutor()
    }

    @SuppressLint("MissingPermission")
    fun connectL2cap(callbackContext: CallbackContext, secureChannel: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                disconnectL2Cap()
                socket =
                    if (secureChannel) device.createL2capChannel(psm)
                    else device.createInsecureL2capChannel(psm)
                socket!!.connect()
                executor.submit { readL2CapData() }
                val result = PluginResult(PluginResult.Status.OK)
                result.keepCallback = true
                callbackContext.sendPluginResult(result)
                synchronized(updateLock) { l2capConnectContext = callbackContext }
            } else {
                callbackContext.error("L2CAP not supported by platform")
            }
        } catch (e: Exception) {
            Timber.e("connect L2Cap failed %s", e.message)
            callbackContext.error("Failed to open L2Cap connection")
        }
    }

    fun disconnectL2Cap() {
        disconnectL2Cap("L2CAP disconnected")
    }

    val isConnected: Boolean
        get() = socket != null && socket!!.isConnected

    private fun disconnectL2Cap(message: String) {
        try {
            if (socket != null) {
                socket!!.close()
                socket = null
            }
        } catch (e: Exception) {
            Timber.e("disconnect L2Cap failed %s", e.message)
        }
        var callback: CallbackContext?
        synchronized(updateLock) {
            callback = l2capConnectContext
            l2capConnectContext = null
        }
        if (callback != null) {
            callback!!.error(message)
        }
    }

    fun registerL2CapReceiver(callbackContext: CallbackContext?) {
        synchronized(updateLock) { l2capReceiver = callbackContext }
    }

    fun writeL2CapChannel(callbackContext: CallbackContext, data: ByteArray?) {
        if (socket == null || !socket!!.isConnected) {
            callbackContext.error("L2CAP PSM $psm not connected.")
            return
        }
        try {
            val outputStream = socket!!.outputStream
            outputStream.write(data)
            callbackContext.success()
        } catch (e: IOException) {
            Timber.e("L2Cap write failed %s", e.message)
            disconnectL2Cap("L2Cap write pipe broken")
            callbackContext.error("L2CAP write failed")
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun readL2CapData() {
        try {
            val lSocket = socket
            val inputStream = lSocket!!.inputStream
            val buffer = ByteArray(lSocket.maxReceivePacketSize)
            while (lSocket.isConnected) {
                val readCount = inputStream.read(buffer)
                var receiver: CallbackContext?
                synchronized(updateLock) { receiver = l2capReceiver }
                if (readCount >= 0 && receiver != null) {
                    val result =
                        PluginResult(PluginResult.Status.OK, Arrays.copyOf(buffer, readCount))
                    result.keepCallback = true
                    receiver!!.sendPluginResult(result)
                }
            }
            disconnectL2Cap("L2Cap channel disconnected")
        } catch (e: Exception) {
            Timber.e("reading L2Cap data failed %s", e.message)
            disconnectL2Cap("L2Cap read pipe broken")
        }
    }

    companion object {
        private const val TAG = "L2CAPContext"
    }
}
