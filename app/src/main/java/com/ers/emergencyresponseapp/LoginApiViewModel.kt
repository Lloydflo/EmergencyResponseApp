package com.ers.emergencyresponseapp

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ers.emergencyresponseapp.firebase.repository.FirebaseChatRepository
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.network.UserDto
import com.ers.emergencyresponseapp.notification.PushTokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


data class LoginApiUiState(
    val loading: Boolean = false,
    val email: String = "",
    val otp: String = "",
    val otpSent: Boolean = false,
    val verified: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val loggedInUser: UserDto? = null,
    val resendSeconds: Int = 0
)

class LoginApiViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginApiUiState())
    val uiState: StateFlow<LoginApiUiState> = _uiState.asStateFlow()

    private val firebasePresenceRepository = FirebaseChatRepository()
    private var resendTimerJob: Job? = null

    fun onEmailChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            email = value.trim().replace("'", ""),
            error = null
        )
    }

    fun onOtpChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            otp = value.filter(Char::isDigit).take(6),
            error = null
        )
    }

    fun sendOtp() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Email is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, message = null, error = null)
            runCatching { RetrofitProvider.authApi.sendOtp(email) }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        email = email,
                        otpSent = response.success,
                        otp = if (response.success) "" else _uiState.value.otp,
                        message = response.message,
                        error = response.message.takeUnless { response.success }
                    )
                    if (response.success) startResendTimer()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message ?: "Failed to send OTP"
                    )
                }
        }
    }

    fun verifyOtp(context: Context, onSuccess: (String) -> Unit) {
        val email = _uiState.value.email.trim()
        val otp = _uiState.value.otp.trim()
        if (email.isBlank() || otp.length != 6) {
            _uiState.value = _uiState.value.copy(error = "Enter a valid email and 6-digit OTP")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, message = null, error = null)
            try {
                val response = RetrofitProvider.authApi.verifyOtp(email, otp)
                val user = response.user
                if (!response.success || user == null || user.id <= 0) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        verified = false,
                        loggedInUser = null,
                        error = response.message.ifBlank { "The server did not return a valid responder account." }
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    verified = true,
                    message = response.message,
                    error = null,
                    loggedInUser = user
                )

                val resolvedPhotoUrl = resolveProfileImageUrl(user.profileImagePath)
                context.applicationContext
                    .getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("user_id", user.id.toString())
                    .apply()
                context.applicationContext
                    .getSharedPreferences("ers_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .apply {

                        putString(
                            "account_full_name",
                            user.name.orEmpty()
                        )

                        putString(
                            "account_username",
                            user.username?.takeIf { it.isNotBlank() }
                                ?: user.name.orEmpty()
                        )

                        putString(
                            "account_email",
                            user.email
                        )

                        putString(
                            "department",
                            user.department.orEmpty()
                        )

                        putString(
                            "unit_code",
                            user.unitCode.orEmpty()
                        )

                        putString(
                            "unit_type",
                            user.unitType.orEmpty()
                        )

                        putString(
                            "unit_status",
                            user.unitStatus.orEmpty()
                        )

                        if (resolvedPhotoUrl.isNullOrBlank())
                            remove("account_photo")
                        else
                            putString("account_photo", resolvedPhotoUrl)

                    }
                    .apply()

                runCatching {
                    firebasePresenceRepository.saveUserToFirebase(
                        userId = user.id.toString(),
                        fullName = user.name.orEmpty(),
                        email = user.email,
                        department = user.department?.takeIf { it.isNotBlank() }
                            ?: user.role.orEmpty()
                    )
                }.onFailure { error ->
                    // MySQL login remains authoritative; presence can recover on next app start.
                    Log.e("Login", "Firebase responder presence sync failed", error)
                }

                viewModelScope.launch {
                    PushTokenManager.registerCurrentToken(
                        context = context.applicationContext,
                        responderId = user.id
                    )
                }

                resendTimerJob?.cancel()
                onSuccess(email)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    verified = false,
                    error = error.message ?: "OTP verification failed"
                )
            }
        }
    }

    private fun resolveProfileImageUrl(path: String?): String? {
        val clean = path?.trim().orEmpty()
        if (clean.isBlank()) return null
        return if (clean.startsWith("http://", true) || clean.startsWith("https://", true)) {
            clean
        } else {
            BuildConfig.BASE_URL.trimEnd('/') + "/" + clean.trimStart('/')
        }
    }

    private fun startResendTimer() {
        resendTimerJob?.cancel()
        resendTimerJob = viewModelScope.launch {
            // Server OTP lifetime is five minutes.
            for (seconds in 300 downTo 0) {
                _uiState.value = _uiState.value.copy(resendSeconds = seconds)
                delay(1_000)
            }
        }
    }

    fun backToEmailStep() {
        resendTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            otpSent = false,
            otp = "",
            verified = false,
            error = null,
            message = null,
            resendSeconds = 0
        )
    }
}
