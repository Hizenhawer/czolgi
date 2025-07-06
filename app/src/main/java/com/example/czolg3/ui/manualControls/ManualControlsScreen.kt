package com.example.czolg3.ui.manualControls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ManualControlsScreen(
    manualControlsViewModel: ManualControlsViewModel, modifier: Modifier = Modifier,
    onNavigateToFullScreen: () -> Unit = {}
) {
    val textToShow by manualControlsViewModel.text.collectAsStateWithLifecycle()

    Column(
        modifier = modifier // Apply modifier from NavHost (includes padding from MainActivity's Scaffold)
            .fillMaxSize()
            .padding(16.dp), // Your screen-specific padding
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            content = { Icon(Icons.Filled.Fullscreen, contentDescription = "Full screen") },
            onClick = onNavigateToFullScreen,
            modifier = Modifier.padding(0.dp)
        )
        // Add more Composable elements here as needed
    }
}

// Preview remains the same
@Preview(showBackground = true)
@Composable
fun GalleryScreenPreview() {
    val manualControlsViewModel: ManualControlsViewModel =
        viewModel()
    MaterialTheme {
        ManualControlsScreen(
            manualControlsViewModel = manualControlsViewModel
        )
    }
}
