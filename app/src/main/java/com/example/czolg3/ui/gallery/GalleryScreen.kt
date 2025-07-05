package com.example.czolg3.ui.gallery

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
// import com.example.czolg3.ui.gallery.GalleryViewModel // Ensure import

// @OptIn(ExperimentalMaterial3Api::class) // Not needed if Scaffold is removed

@Composable
fun GalleryScreen(galleryViewModel: GalleryViewModel, modifier: Modifier = Modifier) {
    val textToShow by galleryViewModel.text.collectAsStateWithLifecycle()

    Column(
        modifier = modifier // Apply modifier from NavHost (includes padding from MainActivity's Scaffold)
            .fillMaxSize()
            .padding(16.dp), // Your screen-specific padding
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = textToShow,
            style = MaterialTheme.typography.headlineMedium
        )
        // Add more Composable elements here as needed
    }
}

// Preview remains the same
@Preview(showBackground = true)
@Composable
fun GalleryScreenPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Gallery Preview Text",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
