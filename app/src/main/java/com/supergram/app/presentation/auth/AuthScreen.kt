package com.supergram.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.supergram.app.domain.model.AuthState

/**
 * Material 3 authentication screen. Reacts to the TDLib authorization state
 * machine: phone number -> login code -> cloud password -> ready.
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "SuperGram",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stateLabel(authState),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            when (authState) {
                AuthState.WaitPhoneNumber -> PhoneStep(busy) { viewModel.sendPhoneNumber(it) }
                AuthState.WaitCode -> CodeStep(busy) { viewModel.sendAuthCode(it) }
                AuthState.WaitPassword -> PasswordStep(busy) { viewModel.sendCloudPassword(it) }
                AuthState.Ready -> Text(
                    text = "Authorized. Telegram client is ready.",
                    style = MaterialTheme.typography.titleMedium,
                )
                AuthState.WaitRegistration -> Text(
                    text = "Account registration required on first device.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> CircularProgressIndicator()
            }

            error?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PhoneStep(busy: Boolean, onSubmit: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    AuthField(
        value = phone,
        onValueChange = { phone = it },
        label = "Phone number",
        placeholder = "+994501234567",
        keyboardType = KeyboardType.Phone,
        busy = busy,
        buttonText = "Send code",
        onSubmit = { onSubmit(phone) },
    )
}

@Composable
private fun CodeStep(busy: Boolean, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AuthField(
        value = code,
        onValueChange = { code = it },
        label = "Login code",
        placeholder = "12345",
        keyboardType = KeyboardType.NumberPassword,
        busy = busy,
        buttonText = "Verify",
        onSubmit = { onSubmit(code) },
    )
}

@Composable
private fun PasswordStep(busy: Boolean, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AuthField(
        value = password,
        onValueChange = { password = it },
        label = "Cloud password (2FA)",
        placeholder = "",
        keyboardType = KeyboardType.Password,
        visualTransformation = PasswordVisualTransformation(),
        busy = busy,
        buttonText = "Unlock",
        onSubmit = { onSubmit(password) },
    )
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    busy: Boolean,
    buttonText: String,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    onSubmit: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 420.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = !busy && value.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Please wait..." else buttonText)
        }
    }
}

private fun stateLabel(state: AuthState): String = when (state) {
    AuthState.Uninitialized -> "Connecting to Telegram..."
    AuthState.WaitTdlibParameters -> "Initializing TDLib..."
    AuthState.WaitPhoneNumber -> "Sign in with your phone number"
    AuthState.WaitCode -> "Enter the code sent to your Telegram"
    AuthState.WaitRegistration -> "Registration required"
    AuthState.WaitEmailAddress -> "Confirm your email address"
    AuthState.WaitEmailCode -> "Enter the email verification code"
    AuthState.WaitOtherDeviceConfirmation -> "Confirm this login on another device"
    AuthState.WaitPremiumPurchase -> "A Premium purchase is required"
    AuthState.WaitPassword -> "Enter your cloud password"
    AuthState.Ready -> "Signed in"
    AuthState.LoggingOut -> "Logging out..."
    AuthState.Closing -> "Closing..."
    AuthState.Closed -> "Closed"
    is AuthState.Error -> "Error: ${state.message}"
}
