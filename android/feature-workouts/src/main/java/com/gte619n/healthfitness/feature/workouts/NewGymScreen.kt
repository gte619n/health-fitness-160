package com.gte619n.healthfitness.feature.workouts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gte619n.healthfitness.feature.workouts.ui.LocationForm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGymScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    vm: NewGymViewModel = hiltViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New gym") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LocationForm(
            state = form,
            onChange = vm::update,
            onSubmit = { vm.submit(onCreated) },
            submitLabel = "Create gym",
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
