package com.gte619n.healthfitness.mobile.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gte619n.healthfitness.data.auth.AuthState

@Composable
fun SignInScreen(
    state: AuthState,
    onSignIn: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("tesseta", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(
                "Sign in with Google to continue.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            when (state) {
                AuthState.Loading -> CircularProgressIndicator()
                is AuthState.Failed -> Text(
                    "Sign-in failed: ${state.cause}",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }
            Button(onClick = onSignIn, enabled = state !is AuthState.Loading) {
                Text("Continue with Google")
            }
        }
    }
}
