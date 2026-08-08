package com.shortscap.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shortscap.app.screens.settings.FocusPasscodeCreateScreen
import com.shortscap.app.screens.settings.FocusPasscodeEmailScreen
import com.shortscap.app.screens.settings.FocusPasscodeMobileScreen
import com.shortscap.app.screens.settings.FocusPasscodeOtpScreen
import com.shortscap.app.screens.settings.FocusPasscodeRecoverScreen
import com.shortscap.app.screens.settings.FocusPasscodeSetupScreen
import com.shortscap.app.screens.settings.FocusPasscodeStatusScreen
import com.shortscap.app.screens.settings.FocusPasscodeVerifyScreen
import com.shortscap.app.study.FocusPasscodeEntry
import com.shortscap.app.study.FocusRecoveryMethod
import com.shortscap.app.viewmodel.AppUiState
import com.shortscap.app.viewmodel.AppViewModel

/**
 * Exit Passcode flow — ONE shared overlay NavHost for the entire passcode UI
 * (setup, verification, status, recovery, email/mobile, OTP, create).
 *
 * It is rendered at the app root (ShortsCapApp) as a full-screen overlay and
 * is opened from BOTH the Home page (tap active Study Mode → verify) and
 * General → Study Mode (active card / Exit Passcode row). Both entry points
 * land on the exact same screens, so there is exactly ONE verification UI,
 * ONE passcode and ONE recovery system in the app — and both callers observe
 * the SAME global Study Mode state, so they can never disagree. Closing the
 * overlay returns to whatever screen opened it.
 *
 * It deliberately does NOT look like the Sign In / Sign Up / auth OTP flows.
 */
object FocusPasscodeDestinations {
    const val SETUP = "focus_passcode_setup"
    const val VERIFY = "focus_passcode_verify"
    const val STATUS = "focus_passcode_status"
    const val RECOVER = "focus_passcode_recover"
    const val EMAIL = "focus_passcode_email"
    const val MOBILE = "focus_passcode_mobile"
    const val OTP = "focus_passcode_otp"
    const val CREATE = "focus_passcode_create"

    /** Route with the recovery method appended, e.g. focus_passcode_otp/EMAIL. */
    fun otpRoute(method: FocusRecoveryMethod): String = "$OTP/${method.name}"
}

/**
 * Pops the passcode back stack one level; at the root of the stack, closes
 * the overlay back to the screen that opened it (Home or Study Mode).
 */
private fun NavHostController.backOrClose(onClose: () -> Unit) {
    if (!popBackStack()) onClose()
}

@Composable
fun FocusPasscodeNavHost(
    entry: FocusPasscodeEntry,
    state: AppUiState,
    viewModel: AppViewModel,
    onClose: () -> Unit,
) {
    val navController = rememberNavController()
    val startRoute = when (entry) {
        FocusPasscodeEntry.SETUP -> FocusPasscodeDestinations.SETUP
        FocusPasscodeEntry.STATUS -> FocusPasscodeDestinations.STATUS
        FocusPasscodeEntry.VERIFY -> FocusPasscodeDestinations.VERIFY
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 8 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 8 } },
    ) {
        // 1. First-time setup — create the passcode (no Forgot here).
        composable(FocusPasscodeDestinations.SETUP) {
            FocusPasscodeSetupScreen(
                onSave = { passcode ->
                    if (viewModel.createFocusPasscode(passcode)) {
                        navController.backOrClose(onClose)
                        true
                    } else false
                },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // 2b. Passcode status — opened from the Study Mode row once a passcode
        //     exists. Shows the green "Passcode Set ✓" status + device date/time
        //     only (the passcode itself is never displayed).
        composable(FocusPasscodeDestinations.STATUS) {
            FocusPasscodeStatusScreen(
                setAtMillis = state.focusPasscodeSetAtMillis,
                onRecover = { navController.navigate(FocusPasscodeDestinations.RECOVER) },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // 2. Verification — the ONLY way to end an active session early.
        composable(FocusPasscodeDestinations.VERIFY) {
            FocusPasscodeVerifyScreen(
                sessionActive = state.studyModeActive,
                onVerify = { passcode ->
                    if (state.studyModeActive) {
                        // Ends Study Mode only when the passcode matches;
                        // incorrect entries keep Study Mode fully active.
                        if (viewModel.endStudySessionWithPasscode(passcode)) {
                            navController.backOrClose(onClose)
                            true
                        } else false
                    } else {
                        // Opened from the Study Mode row (no session): confirm
                        // the passcode, show feedback and return.
                        val ok = viewModel.verifyFocusPasscode(passcode)
                        if (ok) {
                            viewModel.showToast { it.focusPasscodeVerifiedToast }
                            navController.backOrClose(onClose)
                        }
                        ok
                    }
                },
                onForgot = { navController.navigate(FocusPasscodeDestinations.RECOVER) },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // 3. Recovery step 1 — choose Email OR Mobile (two cards, no assumption).
        composable(FocusPasscodeDestinations.RECOVER) {
            FocusPasscodeRecoverScreen(
                onChooseEmail = { navController.navigate(FocusPasscodeDestinations.EMAIL) },
                onChooseMobile = { navController.navigate(FocusPasscodeDestinations.MOBILE) },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // 4. Email verification — request a code, then the OTP page.
        composable(FocusPasscodeDestinations.EMAIL) {
            FocusPasscodeEmailScreen(
                onSendCode = { email ->
                    viewModel.requestFocusOtp(FocusRecoveryMethod.EMAIL, email)
                    navController.navigate(FocusPasscodeDestinations.otpRoute(FocusRecoveryMethod.EMAIL))
                },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // 5. Mobile verification — country code + number, then the OTP page.
        composable(FocusPasscodeDestinations.MOBILE) {
            FocusPasscodeMobileScreen(
                onSendCode = { country, number ->
                    viewModel.requestFocusOtp(FocusRecoveryMethod.MOBILE, "${country.dialCode}$number")
                    navController.navigate(FocusPasscodeDestinations.otpRoute(FocusRecoveryMethod.MOBILE))
                },
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // 6. OTP verification — 6-digit code + resend countdown.
        composable(
            route = "${FocusPasscodeDestinations.OTP}/{method}",
            arguments = listOf(navArgument("method") { type = NavType.StringType }),
        ) { entryArgs ->
            val method = entryArgs.arguments?.getString("method")
                ?.let { name -> FocusRecoveryMethod.entries.firstOrNull { it.name == name } }
                ?: FocusRecoveryMethod.EMAIL
            FocusPasscodeOtpScreen(
                method = method,
                demoCode = state.focusOtpDemoCode,
                contactMasked = state.focusOtpContactMasked,
                onVerify = { code ->
                    if (viewModel.verifyFocusOtp(code)) {
                        navController.navigate(FocusPasscodeDestinations.CREATE)
                        true
                    } else false
                },
                onResend = viewModel::resendFocusOtp,
                onBack = { navController.backOrClose(onClose) },
            )
        }

        // 7. Create new passcode after successful OTP — then return to the
        //    screen that STARTED recovery (VERIFY for an active-session exit,
        //    or STATUS for passcode management) so the new passcode can be
        //    used / seen immediately. Popping back (rather than navigating +
        //    popUpTo) keeps exactly one start screen on the stack, so ending
        //    a session there closes the overlay cleanly instead of landing on
        //    a stale second verify screen.
        composable(FocusPasscodeDestinations.CREATE) {
            FocusPasscodeCreateScreen(
                onSave = { passcode ->
                    if (viewModel.updateFocusPasscode(passcode)) {
                        val poppedVerify = navController.popBackStack(FocusPasscodeDestinations.VERIFY, inclusive = false)
                        if (!poppedVerify) {
                            navController.popBackStack(FocusPasscodeDestinations.STATUS, inclusive = false)
                        }
                        true
                    } else false
                },
                onBack = { navController.backOrClose(onClose) },
            )
        }
    }

    // System Back pops the passcode back stack one level at a time; at its
    // root it closes the overlay. Never exits the app while the flow is open.
    BackHandler {
        navController.backOrClose(onClose)
    }
}
