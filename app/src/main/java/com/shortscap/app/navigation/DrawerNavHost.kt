package com.shortscap.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shortscap.app.model.DrawerScreen
import com.shortscap.app.screens.about.AboutInfoScreen
import com.shortscap.app.screens.about.AboutShortsCapScreen
import com.shortscap.app.screens.about.CopyrightScreen
import com.shortscap.app.screens.about.FeaturesScreen
import com.shortscap.app.screens.about.TechnologiesScreen
import com.shortscap.app.screens.about.VersionBuildScreen
import com.shortscap.app.screens.feedback.FeedbackScreen
import com.shortscap.app.screens.help.ContactSupportScreen
import com.shortscap.app.screens.help.FaqScreen
import com.shortscap.app.screens.help.HelpSupportScreen
import com.shortscap.app.screens.help.ReportBugScreen
import com.shortscap.app.screens.legal.LegalDocument
import com.shortscap.app.screens.legal.LegalDocumentScreen

/** Route constants for every drawer destination hosted by [DrawerNavHost]. */
object DrawerDestinations {
    const val HELP_SUPPORT = "help_support"
    const val FAQ = "faq"
    const val CONTACT_SUPPORT = "contact_support"
    const val REPORT_BUG = "report_bug"

    const val ABOUT_SHORTSCAP = "about_shortscap"
    const val ABOUT = "about_info"
    const val FEATURES = "features"
    const val TECHNOLOGIES = "technologies"
    const val VERSION_BUILD = "version_build"
    const val COPYRIGHT = "copyright"

    const val PRIVACY_POLICY = "privacy_policy"
    const val TERMS_CONDITIONS = "terms_conditions"
    const val FEEDBACK = "feedback"
}

private fun DrawerScreen.startRoute(): String = when (this) {
    DrawerScreen.HELP_SUPPORT -> DrawerDestinations.HELP_SUPPORT
    DrawerScreen.PRIVACY_POLICY -> DrawerDestinations.PRIVACY_POLICY
    DrawerScreen.TERMS_CONDITIONS -> DrawerDestinations.TERMS_CONDITIONS
    DrawerScreen.ABOUT_SHORTSCAP -> DrawerDestinations.ABOUT_SHORTSCAP
    DrawerScreen.FEEDBACK -> DrawerDestinations.FEEDBACK
}

/**
 * Back-stack navigation for every full-screen destination opened from the
 * Dashboard drawer.
 *
 * Dashboard → drawer item → sub-page (e.g. Help & Support → FAQ). The system
 * Back button pops this NavHost's stack one level at a time; at the root of
 * the drawer stack it closes the overlay back to the Dashboard. Back never
 * exits the app while a drawer screen is open.
 */
@Composable
fun DrawerNavHost(
    screen: DrawerScreen,
    onClose: () -> Unit,
    onBugSubmitted: () -> Unit,
    onFeedbackSubmitted: () -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = screen.startRoute(),
        enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 8 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 8 } },
    ) {
        // ---- Help & Support hub + child pages ----
        composable(DrawerDestinations.HELP_SUPPORT) {
            HelpSupportScreen(
                onBack = onClose,
                onOpenFaq = { navController.navigate(DrawerDestinations.FAQ) },
                onOpenContactSupport = { navController.navigate(DrawerDestinations.CONTACT_SUPPORT) },
                onOpenReportBug = { navController.navigate(DrawerDestinations.REPORT_BUG) },
            )
        }
        composable(DrawerDestinations.FAQ) {
            FaqScreen(onBack = { navController.popBackStack() })
        }
        composable(DrawerDestinations.CONTACT_SUPPORT) {
            ContactSupportScreen(onBack = { navController.popBackStack() })
        }
        composable(DrawerDestinations.REPORT_BUG) {
            ReportBugScreen(
                onBack = { navController.popBackStack() },
                onSubmitted = onBugSubmitted,
            )
        }

        // ---- About ShortsCap hub + child pages ----
        composable(DrawerDestinations.ABOUT_SHORTSCAP) {
            AboutShortsCapScreen(
                onBack = onClose,
                onOpenAbout = { navController.navigate(DrawerDestinations.ABOUT) },
                onOpenFeatures = { navController.navigate(DrawerDestinations.FEATURES) },
                onOpenTechnologies = { navController.navigate(DrawerDestinations.TECHNOLOGIES) },
                onOpenVersionBuild = { navController.navigate(DrawerDestinations.VERSION_BUILD) },
                onOpenCopyright = { navController.navigate(DrawerDestinations.COPYRIGHT) },
            )
        }
        composable(DrawerDestinations.ABOUT) {
            AboutInfoScreen(onBack = { navController.popBackStack() })
        }
        composable(DrawerDestinations.FEATURES) {
            FeaturesScreen(onBack = { navController.popBackStack() })
        }
        composable(DrawerDestinations.TECHNOLOGIES) {
            TechnologiesScreen(onBack = { navController.popBackStack() })
        }
        composable(DrawerDestinations.VERSION_BUILD) {
            VersionBuildScreen(onBack = { navController.popBackStack() })
        }
        composable(DrawerDestinations.COPYRIGHT) {
            CopyrightScreen(onBack = { navController.popBackStack() })
        }

        // ---- Other drawer destinations (content untouched; hosted here so
        //      the system Back button navigates instead of exiting) ----
        composable(DrawerDestinations.PRIVACY_POLICY) {
            LegalDocumentScreen(
                document = LegalDocument.PRIVACY_POLICY,
                onBack = onClose,
            )
        }
        composable(DrawerDestinations.TERMS_CONDITIONS) {
            LegalDocumentScreen(
                document = LegalDocument.TERMS_CONDITIONS,
                onBack = onClose,
            )
        }
        composable(DrawerDestinations.FEEDBACK) {
            FeedbackScreen(
                onBack = onClose,
                onFeedbackSubmitted = onFeedbackSubmitted,
            )
        }
    }

    // System Back while a drawer screen is open: pop the drawer back stack;
    // at its root, close the overlay back to the Dashboard. Never exits the
    // app (composed after the NavHost so it takes precedence).
    BackHandler {
        if (!navController.popBackStack()) {
            onClose()
        }
    }
}
