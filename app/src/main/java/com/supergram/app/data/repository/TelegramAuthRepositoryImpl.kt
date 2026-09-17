package com.supergram.app.data.repository

import com.supergram.app.core.telegram.TelegramClientManager
import com.supergram.app.domain.model.AuthState
import com.supergram.app.domain.repository.TelegramAuthRepository
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi

/**
 * TDLib-backed implementation of [TelegramAuthRepository].
 * All TDLib types stay inside the data layer; the domain sees only [AuthState].
 */
class TelegramAuthRepositoryImpl(
    private val clientManager: TelegramClientManager
) : TelegramAuthRepository {

    override val authState: StateFlow<AuthState> = clientManager.authState

    override suspend fun requestAuthCode(phoneNumber: String): Result<Unit> {
        val request = TdApi.SetAuthenticationPhoneNumber().apply {
            this.phoneNumber = phoneNumber
            settings = TdApi.PhoneNumberAuthenticationSettings().apply {
                allowFlashCall = false
                allowMissedCall = false
                isCurrentPhoneNumber = false
                hasUnknownPhoneNumber = false
                allowSmsRetrieverApi = true
            }
        }
        return sendAndCheck(request)
    }

    override suspend fun submitAuthCode(code: String): Result<Unit> =
        sendAndCheck(TdApi.CheckAuthenticationCode(code))

    override suspend fun submitPassword(password: String): Result<Unit> =
        sendAndCheck(TdApi.CheckAuthenticationPassword(password))

    private suspend fun sendAndCheck(query: TdApi.Function<*>): Result<Unit> {
        return try {
            when (val result = clientManager.send(query)) {
                is TdApi.Error -> Result.failure(TelegramAuthException(result.code, result.message))
                else -> Result.success(Unit)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

/** Domain-meaningful exception carrying the TDLib error code and message. */
class TelegramAuthException(val code: Int, message: String) : Exception(message)
