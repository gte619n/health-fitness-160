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
import com.gte619n.healthfitness.feature.workouts.ui.CoverPhotoUploader
import com.gte619n.healthfitness.feature.workouts.ui.LocationForm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGymScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: EditGymViewModel = hiltViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val coverUrl by vm.coverPhotoUrl.collectAsStateWithLifecycle()
    val uploading by vm.uploading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit gym") },
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
            onSubmit = { vm.submit(onSaved) },
            submitLabel = "Save changes",
            modifier = Modifier.fillMaxSize().padding(padding),
            header = {
                CoverPhotoUploader(
                    currentUrl = coverUrl,
                    onPick = vm::uploadCoverPhoto,
                    onDelete = vm::deleteCoverPhoto,
                    uploading = uploading,
                )
            },
        )
    }
}
