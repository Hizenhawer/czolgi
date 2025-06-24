package com.example.czolg3.ui.home

import android.bluetooth.le.BluetoothLeScanner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragment"
    }
    private val _connectionStatus = MutableLiveData<String>().apply {
        value = "Disconnected"
    }

    val text: LiveData<String> = _text
}