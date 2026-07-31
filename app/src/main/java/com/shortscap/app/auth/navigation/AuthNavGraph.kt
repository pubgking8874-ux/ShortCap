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
import com.shortscap.app.auth.screens.CreateAccountScreen
import com.shortscap.app.auth.screens.ForgotPasswordScreen
import com.shortscap.app.auth.screens.LoginScreen
import com.shortscap.app.auth.screens.OtpVerificationScreen
import com.shortscap.app.auth.screens.ResetPasswordScreen
import com.shortscap.app.auth.screens.SplashScreen
import com.shortscap.app.auth.screens.WelcomeScreen

/**
 * Full auth flow, mock-navigation only:
 *
 * Splash -> Welcome -> { Login | CreateAccount | Guest->Dashboard }
 * Login -> ForgotPassword -> OtpVerification -> ResetPassword -> Login
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
                }
            )
        }

        composable(AuthScreen.CreateAccount.route) {
            CreateAccountScreen(
                onBack = { navController.popBackStack() },
                onCreateAccount = { _, _, _ -> onExitToDashboard() },
                onSignIn = {
                    navController.navigate(AuthScreen.Login.route) {
                        popUpTo(AuthScreen.CreateAccount.route) { inclusive = true }
                    }
                }
            )
        }

        composable(AuthScreen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
                onSendOtp = { navController.navigate(AuthScreen.OtpVerification.route) }
            )
        }

        composable(AuthScreen.OtpVerification.route) {
            OtpVerificationScreen(
                onBack = { navController.popBackStack() },
                onVerify = { navController.navigate(AuthScreen.ResetPassword.route) },
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
    }
}
