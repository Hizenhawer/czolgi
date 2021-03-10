package com.example.czolgi

import android.app.Activity
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED
import android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import java.util.logging.Logger

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_ENEBLE_BT: Int = 1

    lateinit var btAdapter: BluetoothAdapter

    private val myBroadcastReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_TURNING_OFF)
                    finishAffinity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!btAdapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_CODE_ENEBLE_BT)
        }

        loadTanks()
    }

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(ACTION_STATE_CHANGED)
        registerReceiver(myBroadcastReceiver, intentFilter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(myBroadcastReceiver)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val tag = object {}.javaClass.enclosingMethod?.name.toString()
        Logger.getLogger(tag).info("RequestCode = $requestCode")
        Logger.getLogger(tag).info("ResultCode = $resultCode")
        when (requestCode) {
            REQUEST_CODE_ENEBLE_BT ->
                if (resultCode == Activity.RESULT_CANCELED)
                    finishAffinity()
        }
    }

    private fun loadTanks() {
       val pairedDevices = btAdapter.bondedDevices
        TODO("Przefiltrowane sparowane czołgi nad niesparowanym z opcją sparowania. Jak filtrować?")
//        pairedDevices.filter { btDevice -> btDevice. }
    }
}
