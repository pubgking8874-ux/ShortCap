package com.shortscap.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shortscap.app.components.NavItemSpec
import com.shortscap.app.components.ScAppDrawer
import com.shortscap.app.components.ScBottomNav
import com.shortscap.app.components.ScTopBar
import com.shortscap.app.components.ScToast
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LocalAppLanguage
import com.shortscap.app.i18n.LocalAppStrings
import com.shortscap.app.icons.IconKey
import com.shortscap.app.icons.LocalIconStyle
import com.shortscap.app.model.DrawerItem
import com.shortscap.app.model.DrawerScreen
import com.shortscap.app.model.ScScreen
import com.shortscap.app.navigation.DrawerNavHost
import com.shortscap.app.navigation.FocusPasscodeNavHost
import com.shortscap.app.navigation.ScNavHost
import com.shortscap.app.navigation.SettingsNavHost
import com.shortscap.app.screens.profile.ProfileScreen
import com.shortscap.app.screens.settings.RefreshPermissionsOnResume
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ScFonts
import com.shortscap.app.theme.ScTextStyles
import com.shortscap.app.theme.ShortsCapTheme
import com.shortscap.app.util.ShareUtils
import com.shortscap.app.viewmodel.AppViewModel

// Bottom-nav icons are requested through the centralized icon system, so the
// active IconStyle (ShortsCap Original / Vibrant Colors) renders them app-wide.
private val bottomNavItems = listOf(
    NavItemSpec(ScScreen.HOME, IconKey.HOME),
    NavItemSpec(ScScreen.ACTIVITY, IconKey.ACTIVITY),
    NavItemSpec(ScScreen.RANK, IconKey.RANK),
    NavItemSpec(ScScreen.WEB, IconKey.WEB),
    NavItemSpec(ScScreen.SETTINGS, IconKey.SETTINGS),
)

/**
 * Mirrors `export default function ShortsCapApp()` — the root composition:
 * a fixed-aspect "device frame" card containing TopBar + scrollable content
 * + floating BottomNav, with Drawer / Profile screen / Toast layered on top
 * via a single Box (same layering the RN version achieves with
 * position:absolute inside `.sc-root`).
 *
 * The active theme (Dark / Light / System Default, persisted across restarts)
 * is resolved here so it switches instantly; [ShortsCapTheme] animates the
 * palette colors on switch while keeping the composition tree stable, so no
 * screen state (scroll, pager page, overlays) is lost.
 */
@Composable
fun ShortsCapApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    // Global font — the persisted preference is applied to the centralized
    // typography system on launch and on every change, so the ENTIRE app
    // re-renders in the selected family instantly (no restart needed).
    LaunchedEffect(state.fontMode) {
        ScFonts.apply(state.fontMode)
    }

    ShortsCapTheme(mode = state.themeMode) {
        // Re-checks the live Android permission state every time the app
        // returns to the foreground (e.g. after the user comes back from the
        // Android settings screen). This is the automatic refresh behind the
        // centralized monitoring-paused state: Home, Permissions and
        // Monitoring all read the same refreshed list, so the Monitoring
        // Paused section appears/disappears with NO manual refresh — and only
        // once the permission status has actually been re-verified.
        RefreshPermissionsOnResume(viewModel::refreshPermissions)

        // The whole logged-in experience reads text from the active language
        // catalog; RTL languages (Urdu) also flip the layout direction. The
        // Auth flow lives outside this composable and stays English/LTR.
        val layoutDirection = if (state.appLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(
            LocalAppStrings provides AppStrings.forLanguage(state.appLanguage),
            LocalAppLanguage provides state.appLanguage,
            // Global icon style — every screen resolves its icons through
            // IconTheme + LocalIconStyle; switching the style updates the
            // whole application instantly (no restart, no state loss).
            LocalIconStyle provides state.iconStyle,
            LocalLayoutDirection provides layoutDirection,
        ) {
            val colors = LocalScColors.current
            val strings = LocalAppStrings.current
            val drawerItems = listOf(
                DrawerItem("help", IconKey.HELP_SUPPORT, strings.drawerHelp),
                DrawerItem("privacy", IconKey.PRIVACY_POLICY, strings.drawerPrivacy),
                DrawerItem("terms", IconKey.TERMS_CONDITIONS, strings.drawerTerms),
                DrawerItem("feedback", IconKey.FEEDBACK, strings.drawerFeedback),
                DrawerItem("share", IconKey.SHARE, strings.drawerShare),
            )
            Surface(modifier = Modifier.fillMaxSize(), color = colors.Bg) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.Bg),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(36.dp))
                        .background(colors.Bg)
                        .fillMaxSize(),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ScTopBar(
                            onMenu = viewModel::openDrawer,
                            onProfile = viewModel::openProfileScreen,
                            menuIcon = Icons.Filled.Menu,
                            userIcon = Icons.Filled.Person,
                        )
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScNavHost(state = state, viewModel = viewModel)

                            Box(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
                                ) {
                                    ScBottomNav(
                                        current = state.screen,
                                        onSelect = viewModel::setScreen,
                                        items = bottomNavItems,
                                    )
                                }
                                ScToast(message = state.toastMessage, checkIcon = Icons.Filled.CheckCircle)
                            }
                        }
                    }

                    // Drawer + scrim sit above everything, matching sc-overlay/.sc-drawer z-index
                    // System Back while the drawer is open closes it instead of
                    // exiting the app.
                    BackHandler(enabled = state.drawerOpen) {
                        viewModel.closeDrawer()
                    }
                    val context = LocalContext.current
                    ScAppDrawer(
                        open = state.drawerOpen,
                        onClose = viewModel::closeDrawer,
                        items = drawerItems,
                        onItemClick = { item ->
                            when (item.id) {
                                "help" -> viewModel.openDrawerScreen(DrawerScreen.HELP_SUPPORT)
                                "privacy" -> viewModel.openDrawerScreen(DrawerScreen.PRIVACY_POLICY)
                                "terms" -> viewModel.openDrawerScreen(DrawerScreen.TERMS_CONDITIONS)
                                "feedback" -> viewModel.openDrawerScreen(DrawerScreen.FEEDBACK)
                                "share" -> {
                                    viewModel.closeDrawer()
                                    ShareUtils.shareApp(context, strings.shareMessage, strings.shareChooser)
                                }
                                else -> viewModel.closeDrawer()
                            }
                        },
                        logoIcon = {
                            Image(
                                painter = painterResource(R.drawable.logo_pic),
                                contentDescription = "ShortsCap logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                            )
                        },
                    )

                    // Profile screen — opened from the Dashboard top bar. System
                    // Back returns to the Dashboard (never exits the app).
                    AnimatedVisibility(
                        visible = state.profileScreenOpen,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(180)),
                    ) {
                        BackHandler(enabled = state.profileScreenOpen) {
                            viewModel.closeProfileScreen()
                        }
                        ProfileScreen(
                            onBack = viewModel::closeProfileScreen,
                            profile = state.profile,
                            onSave = viewModel::saveProfile,
                            onUpdatePicture = viewModel::updateProfilePicture,
                        )
                    }

                    // Full-screen drawer sub-screen — on top of everything, shown
                    // while a drawer destination is open. Back-stack navigation:
                    // system Back pops the drawer stack one level at a time and
                    // then closes the overlay back to the Dashboard — it never
                    // exits the app while a drawer screen is open.
                    state.drawerScreen?.let { screen ->
                        // keyed by screen so each drawer destination gets a fresh
                        // NavController/back stack even on an in-place change.
                        key(screen) {
                            DrawerNavHost(
                                screen = screen,
                                onClose = viewModel::closeDrawerScreen,
                                onBugSubmitted = { viewModel.showToast { it.toastBugSubmitted } },
                                onFeedbackSubmitted = { viewModel.showToast { it.toastFeedbackThanks } },
                            )
                        }
                    }

                    // Settings sub-screens (Settings tab → dedicated screens,
                    // e.g. Monitoring). Own Navigation Compose back stack: Back
                    // pops the settings stack one level at a time and then
                    // returns to the Settings tab — it never exits the app.
                    state.settingsDestination?.let { destination ->
                        key(destination) {
                            SettingsNavHost(
                                startDestination = destination,
                                state = state,
                                viewModel = viewModel,
                                onClose = viewModel::closeSettingsScreen,
                            )
                        }
                    }

                    // Exit Passcode flow — ONE shared overlay rendered on top
                    // of everything, opened from BOTH the Home page (tap active
                    // Study Mode) and Study Mode. Both callers land on
                    // the exact same verification / status / recovery screens
                    // and observe the SAME Study Mode state.
                    state.focusPasscodeFlow?.let { entry ->
                        key(entry) {
                            FocusPasscodeNavHost(
                                entry = entry,
                                state = state,
                                viewModel = viewModel,
                                onClose = viewModel::closeFocusPasscodeFlow,
                            )
                        }
                    }

                    // Smooth language-switch transition: a brief full-surface
                    // overlay while the UI re-renders in the newly applied
                    // language — no abrupt screen reload.
                    AnimatedVisibility(
                        visible = state.languageApplying,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(320)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.Bg)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = colors.Accent,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(34.dp),
                                )
                                Text(
                                    strings.languageApplying,
                                    color = colors.TextSecondary,
                                    style = ScTextStyles.Body,
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
