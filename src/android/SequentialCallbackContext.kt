// (c) 2018 Tim Burke
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.megster.cordova.ble.central

import android.bluetooth.BluetoothGatt
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult

class SequentialCallbackContext(private val context: CallbackContext) {
    private var subscribed = false
    private var sequence = 0

    private val nextSequenceNumber: Int
        private get() {
            synchronized(this) { return sequence++ }
        }

    private fun createSequentialResult(data: ByteArray): PluginResult {
        val resultList: MutableList<PluginResult> = ArrayList(2)
        val dataResult = PluginResult(PluginResult.Status.OK, data)
        val sequenceResult = PluginResult(
            PluginResult.Status.OK,
            nextSequenceNumber
        )
        resultList.add(dataResult)
        resultList.add(sequenceResult)
        return PluginResult(PluginResult.Status.OK, resultList)
    }

    fun sendSequentialResult(data: ByteArray) {
        val result = createSequentialResult(data)
        result.keepCallback = true
        context.sendPluginResult(result)
    }

    fun completeSubscription(status: Int): Boolean {
        if (subscribed) {
            return true
        }
        subscribed = true
        val success = status == BluetoothGatt.GATT_SUCCESS
        val result: PluginResult
        if (success) {
            result = PluginResult(PluginResult.Status.OK, "registered")
            result.keepCallback = true
        } else {
            result = PluginResult(PluginResult.Status.ERROR, "Write descriptor failed: $status")
        }
        context.sendPluginResult(result)
        return success
    }
}
