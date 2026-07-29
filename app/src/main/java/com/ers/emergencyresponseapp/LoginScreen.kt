package com.ers.emergencyresponseapp

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

private val LoginBrand = Color(0xFF4C8A89)
private val LoginBrandDark = Color(0xFF285F5E)
private val LoginBackground = Color(0xFFF3F8F7)
private val LoginTextPrimary = Color(0xFF132A2A)
private val LoginTextSecondary = Color(0xFF637171)
private val LoginBorder = Color(0xFFD6E2E0)

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLoggedIn: (email: String) -> Unit,
    networkAvailable: Boolean = true,
    viewModel: LoginApiViewModel = viewModel(),
    // Uses the app's current launcher foreground by default.
    // Replace this with R.drawable.<your_logo_file> when your official logo
    // is stored inside app/src/main/res/drawable.
    logoResId: Int = R.drawable.ic_launcher_foreground
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LoginBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .then(
                    if (uiState.otpSent) {
                        Modifier.blur(5.dp).alpha(0.75f)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 8.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(112.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = "Emergency Response app logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Emergency Response",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = LoginTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "RESPONDER ACCESS",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = LoginBrandDark,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 24.dp,
                        vertical = 26.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Email Login",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = LoginTextPrimary
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Enter your registered email to receive a one-time password.",
                        fontSize = 14.sp,
                        color = LoginTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    if (!networkAvailable && !uiState.otpSent) {
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "No internet connection. Reconnect before requesting an OTP.",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = { Text("Email address") },
                        placeholder = { Text("name@example.com") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.otpSent && !uiState.loading,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LoginBrand,
                            unfocusedBorderColor = LoginBorder,
                            focusedLabelColor = LoginBrandDark,
                            cursorColor = LoginBrand
                        )
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(
                        enabled = networkAvailable && !uiState.loading,
                        onClick = viewModel::sendOtp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LoginBrand,
                            contentColor = Color.White,
                            disabledContainerColor = LoginBorder,
                            disabledContentColor = LoginTextSecondary
                        )
                    ) {
                        if (uiState.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = "Send OTP",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    uiState.message
                        ?.takeIf { it.isNotBlank() }
                        ?.let { message ->
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = message,
                                fontSize = 13.sp,
                                color = LoginBrandDark,
                                textAlign = TextAlign.Center
                            )
                        }

                    uiState.error
                        ?.takeIf { it.isNotBlank() }
                        ?.let { error ->
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = error,
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp,
                                        vertical = 10.dp
                                    ),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Secure access for authorized responders",
                fontSize = 12.sp,
                color = LoginTextSecondary,
                textAlign = TextAlign.Center
            )
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
                        val context = LocalContext.current

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
                                viewModel.verifyOtp(context = context) { email ->
                                    val user = viewModel.uiState.value.loggedInUser

                                    context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("email", email)
                                        .putBoolean("user_verified", true)
                                        .apply()

                                    val rawPhotoPath = user?.profileImagePath?.takeIf { it.isNotBlank() }
                                    val fullPhotoUrl = rawPhotoPath?.let { path ->
                                        if (path.startsWith("http", ignoreCase = true)) path
                                        else BuildConfig.BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
                                    }

                                    context.getSharedPreferences("ers_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("account_full_name", user?.name.orEmpty())

                                        .putString(
                                            "account_username",
                                            user?.username?.takeIf { it.isNotBlank() }
                                                ?: user?.name.orEmpty()
                                        )
                                        .putString("account_email", user?.email.orEmpty())
                                        .apply {
                                            if (fullPhotoUrl != null) {
                                                putString("account_photo", fullPhotoUrl)
                                            }
                                        }
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
    }
}