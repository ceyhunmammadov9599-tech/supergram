package com.supergram.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.supergram.app.di.AppContainer
import com.supergram.app.domain.model.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * MVVM ViewModel driving the authentication flow.
 * Holds no TDLib knowledge — it talks to use cases and domain states only.
 */
class AuthViewModel(
    observeAuthState: com.supergram.app.domain.usecase.ObserveAuthStateUseCase,
    private val requestAuthCode: com.supergram.app.domain.usecase.RequestAuthCodeUseCase,
    private val submitAuthCode: com.supergram.app.domain.usecase.SubmitAuthCodeUseCase,
    private val submitPassword: com.supergram.app.domain.usecase.SubmitPasswordUseCase,
) : ViewModel() {

    /** Current authorization phase (exposed to the UI). */
    val authState: StateFlow<AuthState> = observeAuthState()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Uninitialized)

    /** One-shot error message surfaced by snackbars / inline text. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** True while an auth request is in flight (drives button progress). */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun sendPhoneNumber(phoneNumber: String) = launchAuth { requestAuthCode(phoneNumber) }

    fun sendAuthCode(code: String) = launchAuth { submitAuthCode(code) }

    fun sendCloudPassword(password: String) = launchAuth { submitPassword(password) }

    fun dismissError() {
        _error.value = null
    }

    private fun launchAuth(block: suspend () -> Result<Unit>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = block()
            _busy.value = false
            result.exceptionOrNull()?.let { _error.value = it.message ?: "Unknown error" }
        }
    }

    companion object {
        fun create(): AuthViewModel = AuthViewModel(
            AppContainer.observeAuthState,
            AppContainer.requestAuthCode,
            AppContainer.submitAuthCode,
            AppContainer.submitPassword,
        )
    }
}
