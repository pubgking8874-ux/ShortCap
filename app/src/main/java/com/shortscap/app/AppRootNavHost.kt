package com.shortscap.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shortscap.app.auth.navigation.AuthNavGraph
import com.shortscap.app.theme.ShortsCapTheme
import com.shortscap.app.viewmodel.AppViewModel

private const val ROUTE_AUTH = "auth_graph"
private const val ROUTE_DASHBOARD = "dashboard"

/**
 * Application launch-flow root.
 *
 * Launches into the Auth flow (Splash -> Welcome -> Login / Create Account /
 * Continue as Guest). Every successful mock auth action — guest, sign-in, or
 * account creation — routes through [AuthNavGraph]'s `onExitToDashboard`
 * seam, which swaps to the Dashboard and clears the auth back stack, so the
 * system back button on the Dashboard exits the app (standard behavior).
 *
 * Back navigation inside the auth flow is handled by the auth graph's own
 * NavHost: Login -> Welcome, Create Account -> Welcome, Forgot Password ->
 * Login, OTP -> Forgot Password, Reset Password -> OTP.
 *
 * The whole tree runs under the app's [ShortsCapTheme], so the auth screens
 * use the same design system and follow the persisted Dark / Light / System
 * Default setting as the Dashboard.
 *
 * Session placeholder: [AppUiState.sessionActive] defaults to false (always
 * open the auth flow on launch). When AWS Cognito / the Python backend / JWT
 * session state is connected, set it from real session state so the app opens
 * straight to the Dashboard — no UI changes required.
 */
@Composable
fun AppRootNavHost(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    ShortsCapTheme(mode = state.themeMode) {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = if (state.sessionActive) ROUTE_DASHBOARD else ROUTE_AUTH,
        ) {
            composable(ROUTE_AUTH) {
                // Full-bleed theme background + safe-area insets so auth content
                // clears the status/navigation bars (screens themselves untouched).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    AuthNavGraph(
                        onExitToDashboard = {
                            viewModel.setSessionActive(true)
                            navController.navigate(ROUTE_DASHBOARD) {
                                popUpTo(ROUTE_AUTH) { inclusive = true }
                            }
                        },
                    )
                }
            }
            composable(ROUTE_DASHBOARD) {
                ShortsCapApp(viewModel = viewModel)
            }
        }
    }
}
