package com.shortscap.app.auth.navigation

import android.net.Uri

/**
 * Route contract for the auth flow.
 *
 * [OtpVerification] is shared by two flows — Forgot Password and Mobile
 * Number login — so it carries two optional args:
 *  - [OtpVerification.ARG_DESTINATION]: what the code was sent to (email or
 *    phone number), shown on the OTP screen.
 *  - [OtpVerification.ARG_MODE]: which flow originated it, so "Verify" lands
 *    on Reset Password ([OtpVerification.MODE_RESET]) or completes the
 *    mobile login ([OtpVerification.MODE_LOGIN]).
 */
sealed class AuthScreen(val route: String) {
    data object Splash : AuthScreen("splash")
    // First-launch permission gate — shown once between Splash and Welcome on
    // a fresh installation (see FirstLaunchSetupStore); never on later starts.
    data object PermissionSetup : AuthScreen("permission_setup")
    data object Welcome : AuthScreen("welcome")
    data object Login : AuthScreen("login")
    data object MobileLogin : AuthScreen("mobile_login")
    data object CreateAccount : AuthScreen("create_account")
    data object ForgotPassword : AuthScreen("forgot_password")
    data object OtpVerification : AuthScreen("otp_verification?destination={destination}&mode={mode}") {
        const val ARG_DESTINATION = "destination"
        const val ARG_MODE = "mode"

        const val MODE_RESET = "reset"         // Forgot Password flow -> Reset Password
        const val MODE_LOGIN = "login"         // Mobile login flow -> Complete Profile
        const val MODE_EMAIL_VERIFY = "email_verify" // Create Account flow -> Complete Profile

        /** Builds a navigation route carrying what the OTP was sent to + the origin flow. */
        fun createRoute(destination: String = "", mode: String = MODE_RESET): String =
            "otp_verification?destination=${Uri.encode(destination)}&mode=$mode"
    }

    data object ResetPassword : AuthScreen("reset_password")
    data object CompleteProfile : AuthScreen("complete_profile")

    /**
     * Not part of this module — represents where "Continue as Guest" /
     * successful "Sign In" / successful "Create Account" should ultimately
     * take the user. Replace the route string with your real Dashboard
     * graph's start route when integrating.
     */
    data object Dashboard : AuthScreen("dashboard")
}
