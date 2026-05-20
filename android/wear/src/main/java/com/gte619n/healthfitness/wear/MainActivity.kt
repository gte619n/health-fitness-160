package com.gte619n.healthfitness.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearHelloScreen()
            }
        }
    }
}

@Composable
fun WearHelloScreen() {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
    ) {
        item { Text("Health & Fitness", style = MaterialTheme.typography.titleMedium) }
        item { Text("Hello from Wear", style = MaterialTheme.typography.bodySmall) }
    }
}
