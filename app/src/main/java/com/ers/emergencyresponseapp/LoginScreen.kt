package com.ers.emergencyresponseapp

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.wrapContentHeight

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLoggedIn: (email: String) -> Unit,
    viewModel: LoginApiViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (uiState.otpSent) Modifier.blur(5.dp).alpha(0.75f) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Email Login", fontSize = 22.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChanged,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(0.9f),
                enabled = !uiState.otpSent && !uiState.loading
            )

            Spacer(Modifier.height(16.dp))

            Button(
                enabled = !uiState.loading,
                onClick = viewModel::sendOtp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Send OTP", fontWeight = FontWeight.Bold)
            }
        }

        if (uiState.otpSent) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.92f)
                    ),
                    elevation = CardDefaults.cardElevation(12.dp)
                ){
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "OTP Verification",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Enter the 6-digit code sent to your email.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Code expires in ${uiState.resendSeconds / 60}:${(uiState.resendSeconds % 60).toString().padStart(2, '0')}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        Spacer(Modifier.height(24.dp))

                        Text(
                            "OTP Code",
                            modifier = Modifier.fillMaxWidth(),
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF263238)
                        )

                        Spacer(Modifier.height(8.dp))

                        val focusRequester = remember { FocusRequester() }

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }

                        BasicTextField(
                            value = uiState.otp,
                            onValueChange = viewModel::onOtpChanged,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = TextStyle(color = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            decorationBox = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    repeat(6) { index ->
                                        val digit = uiState.otp.getOrNull(index)?.toString() ?: ""

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(56.dp)
                                                .border(
                                                    1.dp,
                                                    if (uiState.otp.length == index)
                                                        Color(0xFF4C8A89)
                                                    else
                                                        Color(0xFFD6D6D6),
                                                    RoundedCornerShape(12.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                digit,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.height(20.dp))

                        Button(
                            enabled = !uiState.loading && uiState.otp.length == 6,
                            onClick = {
                                viewModel.verifyOtp { email ->
                                    val user = viewModel.uiState.value.loggedInUser

                                    context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("email", email)
                                        .putBoolean("user_verified", true)
                                        .apply()

                                    context.getSharedPreferences("ers_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("account_full_name", user?.name.orEmpty())
                                        .putString("account_username", user?.name.orEmpty())
                                        .putString("account_email", user?.email.orEmpty())
                                        .apply()

                                    context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("user_id", user?.id?.toString().orEmpty())
                                        .putString("full_name", user?.name.orEmpty())
                                        .putString(
                                            "department",
                                            user?.department?.takeIf { it.isNotBlank() }
                                                ?: user?.role?.takeIf { it.isNotBlank() }
                                                ?: ""
                                        )
                                        .putString("unit_code", user?.unitCode.orEmpty())
                                        .putString("unit_type", user?.unitType.orEmpty())
                                        .putString("unit_status", user?.unitStatus.orEmpty())
                                        .apply()

                                    onLoggedIn(email)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4C8A89),
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFD6D6D6),
                                disabledContentColor = Color.Gray
                            )
                        ) {
                            Text("Verify", fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                enabled = !uiState.loading && uiState.resendSeconds == 0,
                                onClick = viewModel::sendOtp,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (uiState.resendSeconds > 0)
                                        "Resend in ${uiState.resendSeconds}s"
                                    else
                                        "Resend code"
                                )
                            }

                            TextButton(
                                enabled = !uiState.loading,
                                onClick = viewModel::backToEmailStep,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Login again")
                            }
                        }

                        if (uiState.loading) {
                            Spacer(Modifier.height(12.dp))
                            CircularProgressIndicator()
                        }

                        uiState.error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Color.Red, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        if (!uiState.otpSent) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.loading) CircularProgressIndicator()
                uiState.message?.let { Text(it) }
                uiState.error?.let { Text(it, color = Color.Red) }
            }
        }
    }
}