package com.example.czolgi.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import com.example.czolgi.MainActivity
import com.example.czolgi.Tank
import kotlin.coroutines.coroutineContext

abstract class BluetoothHendler {
    private val REQUEST_CODE_ENEBLE_BT: Int = 1

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
//                    val intent = Intent(context, MainActivity::class.java)
//                    context.startActivity(intent)
                    instance.enable()
                }
                return instance
            }
        }
    }

//    override fun getTanks(): ArrayList<Tank> {
//        TODO("Not yet implemented")
//    }
}