package com.ers.emergencyresponseapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ers.emergencyresponseapp.network.LoginRequest
import com.ers.emergencyresponseapp.network.RetrofitProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginApiUiState(
    val loading: Boolean = false,
    val success: Boolean? = null,
    val message: String? = null
)

class LoginApiViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginApiUiState())
    val uiState: StateFlow<LoginApiUiState> = _uiState.asStateFlow()

    fun loginWithEmail(email: String) {
        if (email.isBlank()) {
            _uiState.value = LoginApiUiState(success = false, message = "Email is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginApiUiState(loading = true)
            try {
                val response = RetrofitProvider.authApi.login(LoginRequest(email = email.trim()))
                _uiState.value = LoginApiUiState(
                    loading = false,
                    success = response.success,
                    message = response.message
                )
            } catch (e: Exception) {
                _uiState.value = LoginApiUiState(
                    loading = false,
                    success = false,
                    message = e.message ?: "Login request failed"
                )
            }
        }
    }
}
