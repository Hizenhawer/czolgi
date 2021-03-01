package com.example.czolgi

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import java.util.logging.Logger

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_ENEBLE_BT: Int = 1

    lateinit var btAdapter: BluetoothAdapter

    //Todo: BluetoothAdapter ACTION_STATE_CHANGED broadcast listener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btAdapter = BluetoothAdapter.getDefaultAdapter()

        if (!btAdapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_CODE_ENEBLE_BT)
        }
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


}
