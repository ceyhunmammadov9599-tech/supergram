package com.supergram.app.domain.repository

import com.supergram.app.domain.model.AuthState
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for Telegram authentication operations.
 */
interface TelegramAuthRepository {
    /** Hot stream of the current authorization state. */
    val authState: StateFlow<AuthState>

    /** Sends the phone number to Telegram and requests a login code. */
    suspend fun requestAuthCode(phoneNumber: String): Result<Unit>

    /** Submits the received login code. */
    suspend fun submitAuthCode(code: String): Result<Unit>

    /** Submits the cloud (2FA) password, when required. */
    suspend fun submitPassword(password: String): Result<Unit>
}
