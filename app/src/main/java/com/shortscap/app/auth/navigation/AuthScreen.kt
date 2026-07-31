package com.shortscap.app.auth.navigation

/**
 * Route contract for the auth flow. Kept as plain strings (no args needed
 * yet since this is UI-only / mock navigation). When you wire in real
 * logic, ForgotPassword -> Otp -> ResetPassword can carry an "email" arg.
 */
sealed class AuthScreen(val route: String) {
    data object Splash : AuthScreen("splash")
    data object Welcome : AuthScreen("welcome")
    data object Login : AuthScreen("login")
    data object CreateAccount : AuthScreen("create_account")
    data object ForgotPassword : AuthScreen("forgot_password")
    data object OtpVerification : AuthScreen("otp_verification")
    data object ResetPassword : AuthScreen("reset_password")

    /**
     * Not part of this module — represents where "Continue as Guest" /
     * successful "Sign In" / successful "Create Account" should ultimately
     * take the user. Replace the route string with your real Dashboard
     * graph's start route when integrating.
     */
    data object Dashboard : AuthScreen("dashboard")
}
