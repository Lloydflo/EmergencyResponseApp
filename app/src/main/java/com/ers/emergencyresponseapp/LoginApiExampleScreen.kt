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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginApiExampleScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginApiViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = viewModel::sendOtp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send OTP via Retrofit")
        }

        if (uiState.otpSent) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.otp,
                onValueChange = viewModel::onOtpChanged,
                label = { Text("OTP") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.verifyOtp {} },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify OTP")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.loading) {
            CircularProgressIndicator()
        } else {
            Text("Message: ${uiState.message ?: "-"}")
            Text("Error: ${uiState.error ?: "-"}")
        }
    }
}
