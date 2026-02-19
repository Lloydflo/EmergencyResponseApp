package com.ers.emergencyresponseapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.network.SendOtpRequest
import kotlinx.coroutines.launch

/**
 * Activity example: sends POST http://192.168.1.7:3000/api/send-otp using Retrofit.
 */
class SendOtpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var email by remember { mutableStateOf("") }
                    var loading by remember { mutableStateOf(false) }
                    var message by remember { mutableStateOf("Idle") }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            enabled = !loading,
                            onClick = {
                                if (email.isBlank()) {
                                    Toast.makeText(this@SendOtpActivity, "Email is required", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                loading = true
                                message = "Sending OTP..."

                                lifecycleScope.launch {
                                    try {
                                        val response = RetrofitProvider.apiService.sendOtp(
                                            SendOtpRequest(email = email.trim())
                                        )
                                        val body = response.body()
                                        val isSuccess = body?.success ?: response.isSuccessful
                                        val serverMessage = body?.message ?: "HTTP ${response.code()}"
                                        message = serverMessage

                                        if (isSuccess) {
                                            Toast.makeText(
                                                this@SendOtpActivity,
                                                "OTP sent successfully",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                this@SendOtpActivity,
                                                "Failed: $serverMessage",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        message = "Request failed: ${e.message ?: "Unknown error"}"
                                        Toast.makeText(
                                            this@SendOtpActivity,
                                            message,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Send OTP")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (loading) {
                            CircularProgressIndicator()
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = message)
                    }
                }
            }
        }
    }
}
