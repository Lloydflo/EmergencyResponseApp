package com.ers.emergencyresponseapp

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLoggedIn: (email: String) -> Unit,
    viewModel: LoginApiViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (uiState.otpSent) "Verify OTP" else "Email Login")
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(0.9f),
            enabled = !uiState.otpSent && !uiState.loading
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (!uiState.otpSent) {
            Button(
                enabled = !uiState.loading,
                onClick = viewModel::sendOtp,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text("Send OTP")
            }
        } else {
            OutlinedTextField(
                value = uiState.otp,
                onValueChange = viewModel::onOtpChanged,
                label = { Text("6-digit OTP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(0.9f),
                enabled = !uiState.loading
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !uiState.loading,
                    onClick = {
                        viewModel.verifyOtp { email ->
                            context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                                .edit()
                                .putString("email", email)
                                .putBoolean("user_verified", true)
                                .apply()
                            onLoggedIn(email)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Verify OTP")
                }

                Button(
                    enabled = !uiState.loading,
                    onClick = viewModel::sendOtp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Resend")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                enabled = !uiState.loading,
                onClick = viewModel::backToEmailStep,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text("Back")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.loading) {
            CircularProgressIndicator()
        }

        uiState.message?.let {
            Text(text = it)
        }
        uiState.error?.let {
            Text(text = it, color = Color.Red)
        }
    }
}
