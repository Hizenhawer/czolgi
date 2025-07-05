package com.example.czolg3.ui.slideshow // Or your chosen package

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SlideshowViewModel : ViewModel() {

    private val _text = MutableStateFlow("This is Slideshow Screen")
    val text: StateFlow<String> = _text.asStateFlow()

    // Add any specific logic or data for your slideshow here
    // For example, a list of images, current image index, etc.
}
