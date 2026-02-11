package com.ers.emergencyresponseapp

import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Email + OTP login screen for the PHP backend running on your LAN.
 *
 * Backend endpoints:
 * - POST {base}/login.php      { email }
 * - POST {base}/send_otp.php   { email }
 * - POST {base}/verify_otp.php { email, otp_code }
 *
 * Base URL logic:
 * - Emulator: http://10.0.2.2:8080
 * - Real device: http://{backend_host from strings.xml}
 */

private fun getBackendBase(context: Context): String {
    val fingerprint = android.os.Build.FINGERPRINT ?: ""
    val model = android.os.Build.MODEL ?: ""
    val manufacturer = android.os.Build.MANUFACTURER ?: ""
    val brand = android.os.Build.BRAND ?: ""
    val device = android.os.Build.DEVICE ?: ""
    val product = android.os.Build.PRODUCT ?: ""

    val isEmulator = fingerprint.contains("generic", ignoreCase = true)
            || fingerprint.contains("emulator", ignoreCase = true)
            || model.contains("google_sdk", ignoreCase = true)
            || model.contains("Emulator", ignoreCase = true)
            || model.contains("Android SDK built for", ignoreCase = true)
            || manufacturer.contains("Genymotion", ignoreCase = true)
            || (brand.startsWith("generic") && device.startsWith("generic"))
            || product.contains("sdk", ignoreCase = true)

    val host = if (isEmulator) "10.0.2.2:8080" else context.getString(R.string.backend_host)
    return if (host.startsWith("http")) host else "http://$host"
}

private fun safeParseJsonObject(raw: String): JSONObject? {
    return try {
        JSONObject(raw)
    } catch (_: Exception) {
        null
    }
}

private suspend fun httpPostJson(url: String, payload: JSONObject, timeoutMs: Int = 8000): Pair<Int, JSONObject> {
    return withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }

        BufferedOutputStream(conn.outputStream).use { out ->
            out.write(payload.toString().toByteArray(Charsets.UTF_8))
            out.flush()
        }

        val status = conn.responseCode
        val rawResp = BufferedReader(
            InputStreamReader(if (status in 200..299) conn.inputStream else conn.errorStream)
        ).use { it.readText() }
        conn.disconnect()

        val json = safeParseJsonObject(rawResp)
        if (json != null) {
            return@withContext status to json
        }

        // If it's not JSON (often an Apache HTML error page), return a JSON wrapper so
        // the rest of the app can show a meaningful message.
        val preview = rawResp
            .replace("\r", "")
            .replace("\n", " ")
            .take(200)

        status to JSONObject(
            mapOf(
                "status" to "error",
                "message" to "Non-JSON response from server (HTTP $status). Preview: $preview"
            )
        )
    }
}

private suspend fun loginCheckEmailPhp(context: Context, email: String): Pair<Boolean, String?> {
    val base = getBackendBase(context)
    return try {
        val (code, json) = httpPostJson("$base/login.php", JSONObject().put("email", email))
        if (code in 200..299 && json.optString("status") == "success") return true to null
        false to json.optString("message").ifBlank { "Account not found" }
    } catch (e: Exception) {
        false to ("Can't reach backend at $base\n${e.message}")
    }
}

private suspend fun sendOtpPhp(context: Context, email: String): Pair<Boolean, String?> {
    val base = getBackendBase(context)
    return try {
        val (code, json) = httpPostJson("$base/send_otp.php", JSONObject().put("email", email))
        if (code in 200..299 && json.optString("status") == "success") return true to null
        false to json.optString("message").ifBlank { "Failed to send OTP" }
    } catch (e: Exception) {
        false to ("Can't reach backend at $base\n${e.message}")
    }
}

private suspend fun verifyOtpPhp(context: Context, email: String, otpCode: String): Pair<Boolean, String?> {
    val base = getBackendBase(context)
    return try {
        val (code, json) = httpPostJson(
            "$base/verify_otp.php",
            JSONObject().put("email", email).put("otp_code", otpCode)
        )
        if (code in 200..299 && json.optString("status") == "success") return true to null
        false to json.optString("message").ifBlank { "Invalid OTP" }
    } catch (e: Exception) {
        false to ("Can't reach backend at $base\n${e.message}")
    }
}

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLoggedIn: (email: String) -> Unit
) {
    val context = LocalContext.current

    val email = remember { mutableStateOf("") }
    val otp = remember { mutableStateOf("") }
    val loading = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf<String?>(null) }

    val step = remember { mutableStateOf(1) } // 1=email, 2=otp
    val sentEmail = remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (step.value == 1) {
            Text("Login", modifier = Modifier.padding(bottom = 12.dp))

            OutlinedTextField(
                value = email.value,
                onValueChange = { email.value = it.trim() },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(0.9f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (loading.value) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        val e = email.value.trim()
                        if (e.isBlank() || !e.contains("@")) {
                            Toast.makeText(context, "Enter a valid email", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        loading.value = true
                        error.value = null

                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                // Single source-of-truth call:
                                // - checks if email exists in DB
                                // - if exists: generates + stores OTP
                                // - if not: returns {status:error, message:"Account not found"}
                                val (okSend, msgSend) = sendOtpPhp(context, e)
                                if (!okSend) {
                                    error.value = msgSend
                                    return@launch
                                }

                                sentEmail.value = e
                                step.value = 2
                                Toast.makeText(context, "OTP sent", Toast.LENGTH_SHORT).show()
                            } finally {
                                loading.value = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text("Send OTP")
                }

                // TEMPORARY: allow navigation while backend/DB is not working.
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val e = email.value.trim().ifBlank { "test@example.com" }
                        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("user_email", e)
                            .putBoolean("user_verified", true)
                            .apply()

                        onLoggedIn(e)
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text("Continue (temporary)")
                }
            }

            error.value?.let {
                Text(it, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            Text("Enter verification code", modifier = Modifier.padding(bottom = 12.dp))

            OutlinedTextField(
                value = otp.value,
                onValueChange = { otp.value = it.filter(Char::isDigit).take(6) },
                label = { Text("6-digit code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(0.9f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (loading.value) {
                CircularProgressIndicator()
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (otp.value.length != 6) {
                                Toast.makeText(context, "Enter the 6-digit OTP", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            loading.value = true
                            error.value = null

                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val (ok, msg) = verifyOtpPhp(context, sentEmail.value, otp.value)
                                    if (ok) {
                                        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                                        prefs.edit()
                                            .putString("user_email", sentEmail.value)
                                            .putBoolean("user_verified", true)
                                            .apply()

                                        onLoggedIn(sentEmail.value)
                                    } else {
                                        error.value = msg
                                    }
                                } finally {
                                    loading.value = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Verify")
                    }

                    Button(
                        onClick = {
                            loading.value = true
                            error.value = null

                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val (okSend, msgSend) = sendOtpPhp(context, sentEmail.value)
                                    if (okSend) {
                                        Toast.makeText(context, "OTP resent", Toast.LENGTH_SHORT).show()
                                    } else {
                                        error.value = msgSend
                                    }
                                } finally {
                                    loading.value = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Resend")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { step.value = 1 }, modifier = Modifier.fillMaxWidth(0.9f)) {
                Text("Back")
            }

            error.value?.let {
                Text(it, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
