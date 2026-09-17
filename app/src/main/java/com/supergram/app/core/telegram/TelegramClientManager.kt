package com.supergram.app.core.telegram

import android.os.Build
import com.supergram.app.BuildConfig
import com.supergram.app.domain.model.AuthState
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Singleton owner of the TDLib [Client] lifecycle.
 *
 * Responsibilities:
 *  - Creates and holds the single TDLib [Client] instance (JNI-backed).
 *  - Converts raw TDLib responses into a Kotlin [SharedFlow] of [TdApi.Object] events.
 *  - Maintains the authorization state machine and exposes it as a [StateFlow] of [AuthState].
 *  - Drives the TDLib startup sequence (parameters -> encryption key) transparently.
 *  - Offers a suspend-based [send] wrapper around the callback-based TDLib API.
 */
object TelegramClientManager {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("TelegramClientManager")
    )

    private val _updates = MutableSharedFlow<TdApi.Object>(
        replay = 0,
        extraBufferCapacity = 256
    )

    /** Stream of all TDLib updates (chats, messages, connection state, etc.). */
    val updates: SharedFlow<TdApi.Object> = _updates.asSharedFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Uninitialized)

    /** Authorization state machine — the single source of truth for login UI. */
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Lazily created TDLib client. Touching this property boots the native library. */
    private val client: Client by lazy {
        Client.create(
            { obj -> onRawUpdate(obj) },
            { e -> publishError(e) },
            { e -> publishError(e) }
        )
    }

    /**
     * Boots the client: instantiates it and asks TDLib for the current authorization
     * state, which kicks off the startup/update flow.
     */
    fun start() {
        client.send(TdApi.GetAuthorizationState()) { result ->
            if (result is TdApi.AuthorizationState) {
                onAuthorizationState(result)
            }
        }
    }

    /**
     * Suspends until TDLib answers [query]. TDLib errors (TdApi.Error) are returned
     * as normal values — callers inspect the result.
     */
    suspend fun send(query: TdApi.Function<*>): TdApi.Object =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            try {
                client.send(query, { result ->
                    continuation.resumeWith(kotlin.Result.success(result))
                }, { throwable ->
                    continuation.resumeWith(kotlin.Result.failure(throwable))
                })
            } catch (t: Throwable) {
                continuation.resumeWith(kotlin.Result.failure(t))
            }
        }

    /** Releases the TDLib client and its scope. */
    fun shutdown() {
        client.send(TdApi.Close()) { /* state updates drive the flow */ }
        scope.cancel()
    }

    // ---------------------------------------------------------------- private

    /**
     * Central entry point for every raw TDLib update. Updates run on the TDLib
     * response thread, so the only slow work allowed here is state routing; heavy
     * consumers should collect [updates] instead.
     */
    private fun onRawUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateAuthorizationState) {
            onAuthorizationState(obj.authorizationState)
        }
        scope.launch { _updates.emit(obj) }
    }

    /**
     * The authorization state machine: maps the TDLib state, performs the mandatory
     * startup steps and publishes the domain [AuthState] to collectors.
     */
    private fun onAuthorizationState(state: TdApi.AuthorizationState) {
        // Mandatory TDLib startup handshake.
        if (state is TdApi.AuthorizationStateWaitTdlibParameters) {
            configureTdlib()
        }
        _authState.value = state.toAuthState()
    }

    private fun configureTdlib() {
        val request = TdApi.SetTdlibParameters().apply {
            apiId = BuildConfig.TELEGRAM_API_ID
            apiHash = BuildConfig.TELEGRAM_API_HASH
            databaseDirectory = "tdlib"
            useMessageDatabase = true
            useChatInfoDatabase = true
            useFileDatabase = true
            useSecretChats = true
            systemLanguageCode = "en"
            deviceModel = Build.MODEL
            systemVersion = Build.VERSION.RELEASE
            applicationVersion = "1.0.0"
        }
        client.send(request) { result ->
            if (result !is TdApi.Error) {
                // Modern TDLib (1.8.20+): supply the local database encryption key
                // right after the parameters were accepted.
                val key = TdApi.SetDatabaseEncryptionKey().apply {
                    newEncryptionKey = ByteArray(0) // empty key = default local encryption
                }
                client.send(key) { /* state updates drive the flow */ }
            }
        }
    }

    private fun publishError(e: Throwable) {
        scope.launch {
            _authState.value = AuthState.Error(-1, e.message ?: "Unknown TDLib error")
        }
    }
}

/** Maps a raw TDLib authorization state to the domain [AuthState]. */
fun TdApi.AuthorizationState.toAuthState(): AuthState = when (this) {
    is TdApi.AuthorizationStateWaitTdlibParameters -> AuthState.WaitTdlibParameters
    is TdApi.AuthorizationStateWaitPhoneNumber -> AuthState.WaitPhoneNumber
    is TdApi.AuthorizationStateWaitCode -> AuthState.WaitCode
    is TdApi.AuthorizationStateWaitRegistration -> AuthState.WaitRegistration
    is TdApi.AuthorizationStateWaitPassword -> AuthState.WaitPassword
    is TdApi.AuthorizationStateWaitEmailAddress -> AuthState.WaitEmailAddress
    is TdApi.AuthorizationStateWaitEmailCode -> AuthState.WaitEmailCode
    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> AuthState.WaitOtherDeviceConfirmation
    is TdApi.AuthorizationStateWaitPremiumPurchase -> AuthState.WaitPremiumPurchase
    is TdApi.AuthorizationStateReady -> AuthState.Ready
    is TdApi.AuthorizationStateLoggingOut -> AuthState.LoggingOut
    is TdApi.AuthorizationStateClosing -> AuthState.Closing
    is TdApi.AuthorizationStateClosed -> AuthState.Closed
    else -> AuthState.Uninitialized
}
