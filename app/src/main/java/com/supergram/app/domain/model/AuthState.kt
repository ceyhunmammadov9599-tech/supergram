package com.supergram.app.domain.model

/**
 * Domain-level representation of the Telegram (TDLib) authorization state machine.
 * The presentation layer observes this and reacts to each phase of the flow.
 */
sealed interface AuthState {
    /** TDLib has not reported any state yet. */
    data object Uninitialized : AuthState

    /** Waiting for TDLib parameters (api_id / api_hash) to be set. */
    data object WaitTdlibParameters : AuthState

    /** The user must enter their phone number. */
    data object WaitPhoneNumber : AuthState

    /** A login code has been sent; the user must enter it. */
    data object WaitCode : AuthState

    /** The account is new; the user must complete registration. */
    data object WaitRegistration : AuthState

    /** Two-step verification is enabled; the user must enter their cloud password. */
    data object WaitPassword : AuthState

    /** TDLib asked to confirm the login from another device / email address. */
    data object WaitEmailAddress : AuthState

    /** The user must enter the email verification code. */
    data object WaitEmailCode : AuthState

    /** Login must be confirmed on another logged-in device. */
    data object WaitOtherDeviceConfirmation : AuthState

    /** A Premium purchase is required to complete the login. */
    data object WaitPremiumPurchase : AuthState

    /** Authorization succeeded — the client is fully usable. */
    data object Ready : AuthState

    /** The user is logging out. */
    data object LoggingOut : AuthState

    /** TDLib is closing. */
    data object Closing : AuthState

    /** TDLib is closed. */
    data object Closed : AuthState

    /** An unexpected error was reported by TDLib. */
    data class Error(val code: Int, val message: String) : AuthState
}
