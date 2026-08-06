package com.shortscap.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shortscap.app.components.NavItemSpec
import com.shortscap.app.components.ScAppDrawer
import com.shortscap.app.components.ScBottomNav
import com.shortscap.app.components.ScProfileMenu
import com.shortscap.app.components.ScTopBar
import com.shortscap.app.components.ScToast
import com.shortscap.app.model.DrawerItem
import com.shortscap.app.model.DrawerScreen
import com.shortscap.app.model.ProfileMenuItem
import com.shortscap.app.model.ScScreen
import com.shortscap.app.navigation.DrawerNavHost
import com.shortscap.app.navigation.ScNavHost
import com.shortscap.app.theme.LocalScColors
import com.shortscap.app.theme.ShortsCapTheme
import com.shortscap.app.util.ShareUtils
import com.shortscap.app.viewmodel.AppViewModel

private val drawerItems = listOf(
    DrawerItem(Icons.Filled.HelpOutline, "Help & Support"),
    DrawerItem(Icons.Filled.Description, "Privacy Policy"),
    DrawerItem(Icons.Filled.Gavel, "Terms & Conditions"),
    DrawerItem(Icons.Filled.Info, "About ShortsCap"),
    DrawerItem(Icons.Filled.Message, "Feedback"),
    DrawerItem(Icons.Filled.Share, "Share App"),
)

private val profileItems = listOf(
    ProfileMenuItem(Icons.Filled.Edit, "Edit Profile"),
    ProfileMenuItem(Icons.Filled.ManageAccounts, "Account Settings"),
    ProfileMenuItem(Icons.Filled.Notifications, "Notification Preferences"),
    ProfileMenuItem(Icons.Filled.Logout, "Logout", isDanger = true),
)

private val bottomNavItems = listOf(
    NavItemSpec(ScScreen.HOME, Icons.Filled.Home),
    NavItemSpec(ScScreen.ACTIVITY, Icons.Filled.Schedule),
    NavItemSpec(ScScreen.WEB, Icons.Filled.Language),
    NavItemSpec(ScScreen.SETTINGS, Icons.Filled.Settings),
)

/**
 * Mirrors `export default function ShortsCapApp()` — the root composition:
 * a fixed-aspect "device frame" card containing TopBar + scrollable content
 * + floating BottomNav, with Drawer / ProfileMenu / Toast layered on top via
 * a single Box (same layering the RN version achieves with position:absolute
 * inside `.sc-root`).
 *
 * The active theme (Dark / Light / System Default, persisted across restarts)
 * is resolved here so it switches instantly; [ShortsCapTheme] animates the
 * palette colors on switch while keeping the composition tree stable, so no
 * screen state (scroll, pager page, overlays) is lost.
 */
@Composable
fun ShortsCapApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    ShortsCapTheme(mode = state.themeMode) {
        val colors = LocalScColors.current
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
                            onProfile = viewModel::toggleProfileMenu,
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
                            when (item.label) {
                                "Help & Support" -> viewModel.openDrawerScreen(DrawerScreen.HELP_SUPPORT)
                                "Privacy Policy" -> viewModel.openDrawerScreen(DrawerScreen.PRIVACY_POLICY)
                                "Terms & Conditions" -> viewModel.openDrawerScreen(DrawerScreen.TERMS_CONDITIONS)
                                "About ShortsCap" -> viewModel.openDrawerScreen(DrawerScreen.ABOUT_SHORTSCAP)
                                "Feedback" -> viewModel.openDrawerScreen(DrawerScreen.FEEDBACK)
                                "Share App" -> {
                                    viewModel.closeDrawer()
                                    ShareUtils.shareApp(context)
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

                    // Profile popover, top-right, above drawer's z-order visually but non-blocking when closed
                    ScProfileMenu(
                        open = state.profileOpen,
                        onClose = viewModel::closeProfileMenu,
                        items = profileItems,
                    )

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
                                onBugSubmitted = { viewModel.showToast("Bug report submitted") },
                                onFeedbackSubmitted = { viewModel.showToast("Thank you for your feedback.") },
                            )
                        }
                    }
                }
            }
        }
    }
}
