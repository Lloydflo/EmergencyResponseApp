package com.ers.emergencyresponseapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ers.emergencyresponseapp.network.RetrofitProvider
import com.ers.emergencyresponseapp.network.SendOtpRequest
import com.ers.emergencyresponseapp.network.VerifyOtpRequest
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
    val error: String? = null
)

class LoginApiViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginApiUiState())
    val uiState: StateFlow<LoginApiUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun onOtpChanged(value: String) {
        _uiState.value = _uiState.value.copy(otp = value.filter(Char::isDigit).take(6), error = null)
    }

    fun sendOtp() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Email is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, message = null, error = null)
            try {
                val response = RetrofitProvider.authApi.sendOtp(SendOtpRequest(email = email))
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    email = email,
                    otpSent = response.success,
                    message = response.message,
                    error = if (response.success) null else response.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to send OTP"
                )
            }
        }
    }

    fun verifyOtp(onSuccess: (String) -> Unit) {
        val email = _uiState.value.email.trim()
        val otp = _uiState.value.otp.trim()
        if (email.isBlank() || otp.length != 6) {
            _uiState.value = _uiState.value.copy(error = "Enter a valid email and 6-digit OTP")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, message = null, error = null)
            try {
                val response = RetrofitProvider.authApi.verifyOtp(VerifyOtpRequest(email = email, otp = otp))
                val success = response.success
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    verified = success,
                    message = response.message,
                    error = if (success) null else response.message
                )
                if (success) {
                    onSuccess(email)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "OTP verification failed"
                )
            }
        }
    }

    fun backToEmailStep() {
        _uiState.value = _uiState.value.copy(otpSent = false, otp = "", error = null, message = null)
    }
}
