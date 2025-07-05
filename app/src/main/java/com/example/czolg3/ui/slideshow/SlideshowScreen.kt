package com.example.czolg3.ui.slideshow // Or your chosen package

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SlideshowScreen(slideshowViewModel: SlideshowViewModel) {
    // Collect the state from the ViewModel
    val textToShow by slideshowViewModel.text.collectAsStateWithLifecycle()

    // Basic UI for the slideshow
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = textToShow,
            style = MaterialTheme.typography.headlineMedium
        )
        // Add more UI elements for your slideshow here
        // e.g., Image composable, navigation buttons (Next/Previous)
    }
}

@Preview(showBackground = true)
@Composable
fun SlideshowScreenPreview() {
    MaterialTheme {
        // For preview, you might pass default data or a dummy ViewModel.
        // For simplicity here, just showing the text.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Slideshow Screen Preview",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
