package com.example.czolg3.ui.manualControls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ManualControlsViewModel : ViewModel() {

    private val _text = MutableStateFlow("This is gallery Fragment")
    val text: StateFlow<String> = _text.asStateFlow()

    // Example function to update the text if needed
    fun updateText(newText: String) {
        viewModelScope.launch {
            _text.value = newText
        }
    }
}