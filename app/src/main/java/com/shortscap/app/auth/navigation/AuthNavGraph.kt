package com.shortscap.app.auth.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shortscap.app.auth.screens.CompleteProfileScreen
import com.shortscap.app.auth.screens.CreateAccountScreen
import com.shortscap.app.auth.screens.ForgotPasswordScreen
import com.shortscap.app.auth.screens.LoginScreen
import com.shortscap.app.auth.screens.MobileLoginScreen
import com.shortscap.app.auth.screens.OtpVerificationScreen
import com.shortscap.app.auth.screens.ResetPasswordScreen
import com.shortscap.app.auth.screens.SplashScreen
import com.shortscap.app.auth.screens.WelcomeScreen

/**
 * Full auth flow, mock-navigation only:
 *
 * Splash -> Welcome -> { Login | CreateAccount | Guest->Dashboard }
 * Login -> ForgotPassword -> OtpVerification -> ResetPassword -> Login
 * Email / Google / Mobile -> CompleteProfile -> Dashboard
 * Login -> MobileLogin -> OtpVerification (mode=login) -> CompleteProfile
 *
 * [onExitToDashboard] is the single hook back out to your real app graph —
 * called for "Continue as Guest", a successful "Sign In", and a successful
 * "Create Account". Wire it to your NavController's navigate-to-Dashboard
 * call (with popUpTo to clear the auth back stack) once real auth exists.
 */
@Composable
fun AuthNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AuthScreen.Splash.route,
    onExitToDashboard: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 6 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 6 } }
    ) {
        composable(AuthScreen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(AuthScreen.Welcome.route) {
                        popUpTo(AuthScreen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(AuthScreen.Welcome.route) {
            WelcomeScreen(
                onContinueAsGuest = onExitToDashboard,
                onSignIn = { navController.navigate(AuthScreen.Login.route) },
                onCreateAccount = { navController.navigate(AuthScreen.CreateAccount.route) }
            )
        }

        composable(AuthScreen.Login.route) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onSignIn = { _, _ -> onExitToDashboard() },
                onForgotPassword = { navController.navigate(AuthScreen.ForgotPassword.route) },
                onCreateAccount = {
                    navController.navigate(AuthScreen.CreateAccount.route) {
                        popUpTo(AuthScreen.Login.route) { inclusive = true }
                    }
                },
                onGoogleSignIn = { navController.navigate(AuthScreen.CompleteProfile.route) },
                onMobileSignIn = { navController.navigate(AuthScreen.MobileLogin.route) }
            )
        }

        composable(AuthScreen.MobileLogin.route) {
            MobileLoginScreen(
                onBack = { navController.popBackStack() },
                onSendOtp = { phone ->
                    navController.navigate(
                        AuthScreen.OtpVerification.createRoute(
                            destination = phone,
                            mode = AuthScreen.OtpVerification.MODE_LOGIN
                        )
                    )
                },
                // MobileLogin is always pushed from Login, so popping reveals it.
                onContinueWithEmail = { navController.popBackStack() },
                onContinueWithGoogle = { navController.navigate(AuthScreen.CompleteProfile.route) },
                onCreateAccount = {
                    navController.navigate(AuthScreen.CreateAccount.route) {
                        popUpTo(AuthScreen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(AuthScreen.CreateAccount.route) {
            CreateAccountScreen(
                onBack = { navController.popBackStack() },
                onCreateAccount = { email, _ ->
                    // Email -> Verify (shared OTP screen) -> Complete Profile.
                    navController.navigate(
                        AuthScreen.OtpVerification.createRoute(
                            destination = email,
                            mode = AuthScreen.OtpVerification.MODE_EMAIL_VERIFY
                        )
                    )
                },
                onSignIn = {
                    navController.navigate(AuthScreen.Login.route) {
                        popUpTo(AuthScreen.CreateAccount.route) { inclusive = true }
                    }
                },
                onGoogleSignIn = { navController.navigate(AuthScreen.CompleteProfile.route) },
                onMobileSignIn = { navController.navigate(AuthScreen.MobileLogin.route) }
            )
        }

        composable(AuthScreen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
                onSendOtp = { email ->
                    navController.navigate(
                        AuthScreen.OtpVerification.createRoute(destination = email)
                    )
                }
            )
        }

        composable(
            route = AuthScreen.OtpVerification.route,
            arguments = listOf(
                navArgument(AuthScreen.OtpVerification.ARG_DESTINATION) { defaultValue = "" },
                navArgument(AuthScreen.OtpVerification.ARG_MODE) {
                    defaultValue = AuthScreen.OtpVerification.MODE_RESET
                }
            )
        ) { entry ->
            val destination =
                entry.arguments?.getString(AuthScreen.OtpVerification.ARG_DESTINATION).orEmpty()
            val mode = entry.arguments?.getString(AuthScreen.OtpVerification.ARG_MODE)
                ?: AuthScreen.OtpVerification.MODE_RESET
            OtpVerificationScreen(
                destination = destination,
                onBack = { navController.popBackStack() },
                onVerify = {
                    when (mode) {
                        AuthScreen.OtpVerification.MODE_LOGIN,
                        AuthScreen.OtpVerification.MODE_EMAIL_VERIFY ->
                            navController.navigate(AuthScreen.CompleteProfile.route)

                        else -> navController.navigate(AuthScreen.ResetPassword.route)
                    }
                },
                onResend = { /* UI-only: countdown resets itself in the screen */ }
            )
        }

        composable(AuthScreen.ResetPassword.route) {
            ResetPasswordScreen(
                onBack = { navController.popBackStack() },
                onPasswordUpdated = {
                    navController.navigate(AuthScreen.Login.route) {
                        popUpTo(AuthScreen.Welcome.route) { inclusive = false }
                    }
                }
            )
        }

        composable(AuthScreen.CompleteProfile.route) {
            CompleteProfileScreen(
                onBack = { navController.popBackStack() },
                onContinue = { _, _, _ -> onExitToDashboard() },
                // Skip: no dialog, no validation — straight to the Dashboard.
                onSkip = { onExitToDashboard() }
            )
        }
    }
}
