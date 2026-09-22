package app.splitmate.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitmate.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state

    fun login(identifier: String, password: String) {
        if (identifier.isBlank() || password.isBlank()) {
            _state.value = AuthUiState.Error("Enter your email/mobile and password")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                authRepository.login(identifier, password)
                AuthUiState.Success
            } catch (e: Exception) {
                AuthUiState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun register(name: String, email: String, mobile: String, password: String) {
        if (name.isBlank() || (email.isBlank() && mobile.isBlank()) || password.length < 8) {
            _state.value = AuthUiState.Error("Enter your name, an email or mobile number, and a password with 8+ characters")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                authRepository.register(name, email.ifBlank { null }, mobile.ifBlank { null }, password)
                AuthUiState.Success
            } catch (e: Exception) {
                AuthUiState.Error(e.message ?: "Registration failed")
            }
        }
    }
}
