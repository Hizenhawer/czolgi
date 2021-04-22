package com.example.czolgi.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.Context


abstract class BluetoothHandler {
    companion object{
        @Volatile
        private var INSTANCE: BluetoothAdapter? = null
        fun getInstance(context: Context):BluetoothAdapter{
            synchronized(this){
                var instance = INSTANCE
                if (instance == null){
                    instance = BluetoothAdapter.getDefaultAdapter()
                }
                if (!instance!!.isEnabled) {
                    instance.enable()
                }
                return instance
            }
        }
    }
}