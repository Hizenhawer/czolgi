package com.example.czolg3.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    // Example: If Home Screen had a specific title or message it managed
    private val _screenMessage = MutableStateFlow("Welcome to the Home Screen!")
    val screenMessage: StateFlow<String> = _screenMessage.asStateFlow()

    // Example: If Home Screen had some UI state
    private val _isInfoPanelVisible = MutableStateFlow(false)
    val isInfoPanelVisible: StateFlow<Boolean> = _isInfoPanelVisible.asStateFlow()

    fun toggleInfoPanel() {
        viewModelScope.launch {
            _isInfoPanelVisible.value = !_isInfoPanelVisible.value
        }
    }

    fun updateMessage(newMessage: String) {
        viewModelScope.launch {
            _screenMessage.value = newMessage
        }
    }

    // The old '_text' and '_connectionStatus' are likely no longer needed here
    // if HomeScreen gets that information from BleViewModel or displays static text directly.
}