package com.ers.emergencyresponseapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Example usage: call Retrofit POST /api/login from UI through a ViewModel.
 */
@Composable
fun LoginApiExampleScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginApiViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val email = remember { mutableStateOf("") }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = email.value,
            onValueChange = { email.value = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.loginWithEmail(email.value) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login via Retrofit")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.loading) {
            CircularProgressIndicator()
        } else {
            Text("Success: ${uiState.success ?: "-"}")
            Text("Message: ${uiState.message ?: "-"}")
        }
    }
}
