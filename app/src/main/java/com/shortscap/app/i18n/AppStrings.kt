package com.shortscap.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The complete user-facing string catalog for the logged-in ShortsCap
 * experience. Every screen, dialog, toast and content description in the
 * Dashboard (Home, Activity, Web, Settings + sub-pages, Drawer + sub-pages,
 * Profile) reads its text from [AppStrings] — never from hardcoded literals.
 *
 * One implementation per [AppLanguage] lives in its own file (EnglishStrings,
 * HindiStrings, UrduStrings, ChineseStrings, SpanishStrings). The interface
 * guarantees every language provides every key: adding a string here breaks
 * compilation until all five languages translate it, so nothing can silently
 * fall back.
 *
 * The Auth flow (Splash / Welcome / Sign In / Sign Up / Forgot Password /
 * OTP / Reset Password) intentionally stays in English and does not read this
 * catalog.
 *
 * Adding a new language = a new [AppLanguage] entry + one new file — no UI
 * code changes required.
 */
interface AppStrings {

    // ---- Common ----
    val cancel: String
    val apply: String
    val ok: String
    val submit: String
    val loading: String
    val comingSoon: String
    val comingSoonDesc: String
    val future: String
    val version: String
    val build: String
    val copyrightLine: String
    val allRightsReserved: String
    val digitalWellbeing: String

    // ---- Toasts ----
    val toastProfileSaved: String
    val toastProfilePictureUpdated: String
    val toastBugSubmitted: String
    val toastFeedbackThanks: String
    val toastSettingsReset: String
    val toastWebsiteUpdated: String
    val toastPreferenceSaved: String
    val toastAddWebsite: String

    // ---- Drawer ----
    val drawerHelp: String
    val drawerPrivacy: String
    val drawerTerms: String
    val drawerAbout: String
    val drawerFeedback: String
    val drawerShare: String
    val drawerFooterVersion: String
    val drawerFooterBuild: String

    // ---- Share sheet ----
    val shareMessage: String
    val shareChooser: String

    // ---- Home ----
    val homeGreeting: String
    val homeQuickStats: String
    val homeAppsUsed: String
    val homeRestrictedApps: String
    val homeBlockedSites: String
    val homeFocusTime: String
    val homeFocusStreak: String
    val homeRecentActivity: String
    val homeSeeAll: String
    val homeRecentTime1: String
    val homeRecentTime2: String
    val homeRecentTime3: String

    // ---- Activity ----
    val activityTitle: String
    val activityDaily: String
    val activityWeekly: String
    val activityMonthly: String
    val activityUsageTimeline: String
    val activityMostUsedApps: String
    val activityOther: String
    val activityUnlockCount: String
    val activityAvgSession: String
    val activityReports: String
    val activityWeeklyReport: String
    val activityMonthlyReport: String
    val activityReportSummary: String

    // ---- Activity — period usage titles + dedicated report screens ----
    val activityDailyUsage: String
    val activityWeeklyUsage: String
    val activityMonthlyUsage: String
    val reportSummary: String
    val reportTotalUsage: String
    val reportBusiestDay: String
    val reportMostUsedApp: String
    val reportShortsUsage: String
    val reportShortsWatched: String
    val reportTrend: String

    // ---- Web ----
    val webTitle: String
    val webSearchPlaceholder: String
    val webBlocked: String
    val webAllowed: String
    val webWebToday: String
    val webRecent: String
    val webNoSites: String
    val webNoSitesSubtitle: String
    val webAddWebsite: String

    // Web — analytics + dedicated rule screens
    val webAnalyticsTitle: String
    val webBlockedTitle: String
    val webAllowedTitle: String
    val webPeriodToday: String
    val webPeriodWeek: String
    val webPeriodMonth: String
    val webUsageTotal: String
    val webBreakdownTitle: String
    val webTrendTitle: String
    val webRulesTitle: String
    val webEmptyBlockedTitle: String
    val webEmptyBlockedDesc: String
    val webEmptyAllowedTitle: String
    val webEmptyAllowedDesc: String
    val webNoSearchResults: String
    val webUnblock: String
    val webBlockAction: String
    val webAllow: String
    val webRemove: String
    val webDelete: String
    val webAddDialogTitle: String
    val webAddDialogDomainLabel: String
    val webAddDialogPlaceholder: String
    val webAddDialogAdd: String
    val webAddInvalid: String
    val webAddDuplicate: String
    val webRemoveDialogTitle: String
    val webConfirmRemove: String
    val webHoursShort: String
    val webMinutesShort: String
    val webNoDataTitle: String
    val webNoDataDesc: String
    val webToastBlocked: String
    val webToastUnblocked: String
    val webToastAllowed: String
    val webToastRemoved: String

    /** Confirmation copy for removing a website, e.g. "\"youtube.com\" will be removed…" */
    fun webRemoveMessage(domain: String): String

    // Web — main blocking screen + recent + analytics entry
    val webBlockingTitle: String
    val webBlockWebsite: String
    val webEnterUrlLabel: String
    val webUrlPlaceholder: String
    val webWebTime: String
    val webRecentTitle: String
    val webEmptyRecentTitle: String
    val webEmptyRecentDesc: String
    val webSearchBlocked: String
    val webSearchAllowed: String
    val webTodayUsageTitle: String
    val webWeeklyUsageTitle: String
    val webMonthlyUsageTitle: String

    // ---- Settings home ----
    val settingsTitle: String
    val settingsGeneral: String
    val settingsMonitoring: String
    val settingsPermissions: String
    val settingsNotifications: String
    val settingsAppearance: String
    val settingsDataBackup: String
    val settingsAbout: String
    val settingsResetAll: String

    // ---- Reset All Settings dialog ----
    val resetAction: String
    val resetDialogMessage: String

    // ---- Language ----
    val languageTitle: String
    val languageDefaultSuffix: String
    val languageChangeTitle: String
    val languageCurrent: String
    val languageNew: String
    val languageApplying: String
    val languageEnglish: String
    val languageHindi: String
    val languageUrdu: String
    val languageChinese: String
    val languageSpanish: String

    // ---- Language change dialog (custom premium confirmation) ----
    val languageDialogTitle: String
    val languageChangeSubtitle: String
    val languageCurrentLabel: String
    val languageNewLabel: String
    val languageInfoNote: String
    val applyLanguage: String

    /** "Switching to <language>…" shown while the new language applies. */
    fun switchingTo(languageName: String): String

    // ---- Monitoring ----
    val monitoringTitle: String
    val monitoringSection: String
    val monitoringEnable: String
    val monitoringEnableDesc: String
    val monitoringAppBlocking: String
    val monitoringEnableAppBlocking: String
    val monitoringEnableAppBlockingDesc: String
    val monitoringDailyLimit: String
    val monitoringBlockedApps: String
    val monitoringAllowedApps: String
    val monitoringStrictMode: String
    val monitoringStrictModeDesc: String
    val monitoringShortVideoPlatforms: String
    val monitoringBreakReminder: String
    val monitoringReminderInterval: String
    val monitoringSchedule: String
    val monitoringStatistics: String
    val monitoringTodayUsage: String
    val monitoringBlockedAppsCount: String
    val monitoringCurrentDailyLimit: String
    val monitoringStatus: String
    val monitoringActive: String
    val monitoringPaused: String

    // ---- Home Monitoring Paused section (priority page) + resume popup ----
    val homeMonitoringPausedTitle: String
    val resumeMonitoringDialogTitle: String
    val resumeMonitoringDialogMessage: String
    val resumeMonitoringDialogRequired: String
    val resumeMonitoringDialogContinue: String
    val resumeMonitoringDialogNotNow: String

    /** Toast when monitoring auto-resumes after the required permissions return. */
    val monitoringStartedToast: String

    // ---- Time option labels ----
    val time15Min: String
    val time30Min: String
    val time45Min: String
    val time1Hour: String
    val time2Hours: String
    val timeCustom: String
    val time15Minutes: String
    val time30Minutes: String
    val time45Minutes: String
    val customLimitTitle: String
    val customLimitDesc: String
    val minutesLabel: String
    val setLabel: String

    // ---- Permissions (status overview) ----
    val permissionsTitle: String

    // Permission statuses
    val permStatusGranted: String
    val permStatusEnabled: String
    val permStatusAllowed: String
    val permStatusIgnored: String
    val permStatusNeedsAttention: String
    val permStatusDenied: String
    val permStatusNotAvailable: String
    val permStatusFuture: String

    // Permission detail page
    val permLastChecked: String
    val permNeverChecked: String
    val permDetailWhyTitle: String
    val permDetailStatusTitle: String

    // Permission rows
    val permUsageAccess: String
    val permUsageAccessDesc: String
    val permAccessibility: String
    val permAccessibilityDesc: String
    val permOverlay: String
    val permOverlayDesc: String
    val permNotifications: String
    val permNotificationsDesc: String
    val permBattery: String
    val permBatteryDesc: String
    val permAutoStart: String
    val permAutoStartDesc: String
    val permStorage: String
    val permStorageDesc: String
    val permRoot: String
    val permRootDesc: String

    // ---- Notifications ----
    val notificationsTitle: String

    // Notification categories (main page rows — icon + title + chevron only)
    val notifReminders: String
    val notifLimitAlerts: String
    val notifBlockNotifications: String
    val notifWeeklyInsights: String
    val notifSystemNotifications: String
    val notifSoundVibration: String

    // Reminder Notifications
    val notifDailyUsageReminder: String
    val notifDailyUsageReminderDesc: String
    val notifDailyScreenTimeSummary: String
    val notifDailyScreenTimeSummaryDesc: String
    val notifGoalAchievement: String
    val notifGoalAchievementDesc: String

    // Limit Alerts
    val notifLimit50: String
    val notifLimit50Desc: String
    val notifLimit80: String
    val notifLimit80Desc: String
    val notifLimit100: String
    val notifLimit100Desc: String

    // Block Notifications
    val notifAppBlockedAlert: String
    val notifAppBlockedAlertDesc: String
    val notifRestrictionMessage: String
    val notifRestrictionMessageDesc: String

    // Weekly Insights
    val notifWeeklyProgressReport: String
    val notifWeeklyProgressReportDesc: String
    val notifWeeklyAchievement: String
    val notifWeeklyAchievementDesc: String

    // System Notifications
    val notifPermissionReminder: String
    val notifPermissionReminderDesc: String
    val notifMonitoringStopped: String
    val notifMonitoringStoppedDesc: String
    val notifBackgroundServiceStatus: String
    val notifBackgroundServiceStatusDesc: String

    // Sound & Vibration
    val notifNotificationSound: String
    val notifNotificationSoundDesc: String
    val notifVibration: String
    val notifVibrationDesc: String

    // ---- Appearance ----
    val appearanceTitle: String
    val appearanceTheme: String
    val appearanceDark: String
    val appearanceLight: String
    val appearanceSystem: String
    val appearanceTextSize: String

    // Text size options
    val sizeSmall: String
    val sizeMediumDefault: String
    val sizeLarge: String

    /** Appearance row + dedicated page title for the icon style picker. */
    val appearanceIcons: String

    // ---- Font (Settings → Appearance → Font) ----
    val appearanceFont: String
    /** Toast shown the moment a font is applied app-wide. */
    val toastFontApplied: String
    val fontSimple: String
    val fontNunito: String
    val fontPatrickHand: String
    val fontRoboto: String
    val fontTimesNewRoman: String
    /** Latin specimen line rendered in each font's real typeface (same across languages). */
    val fontPreviewSample: String

    // ---- Chart Style (Settings → Appearance → Chart) ----
    val appearanceChart: String
    val chartBarChart: String
    val chartCircularChart: String
    val chartGraphChart: String

    // ---- Chart tooltip (tap a bar / point / slice for exact time info) ----
    val chartTooltipTime: String
    val chartTooltipUsage: String
    val chartViewDetails: String
    val chartTooltipClose: String

    // ---- Icon Style (Settings → Appearance → Icons) ----
    val iconStyleTitle: String
    val iconStyleOriginal: String
    val iconStyleOriginalDesc: String
    val iconStyleVibrant: String
    val iconStyleVibrantDesc: String
    val iconStylePreview: String
    val iconStyleSelected: String

    // ---- Data Backup ----
    val dataBackupTitle: String

    // ---- Monitoring placeholder pages ----
    val blockedAppsEmptyTitle: String
    val blockedAppsEmptyDesc: String
    val allowedAppsEmptyTitle: String
    val allowedAppsEmptyDesc: String
    val scheduleEmptyTitle: String
    val scheduleEmptyDesc: String

    // ---- Help & Support ----
    val helpTitle: String
    val helpFaq: String
    val helpContact: String
    val helpReportBug: String
    val faqTitle: String
    val faqQ1: String
    val faqA1: String
    val faqQ2: String
    val faqA2: String
    val faqQ3: String
    val faqA3: String
    val faqQ4: String
    val faqA4: String
    val faqQ5: String
    val faqA5: String
    val faqQ6: String
    val faqA6: String
    val faqQ7: String
    val faqA7: String
    val faqQ8: String
    val faqA8: String
    val contactTitle: String
    val contactNeedHelp: String
    val contactEmail: String
    val contactPhone: String
    val contactHours: String
    val contactHoursValue: String
    val bugTitle: String
    val bugReportIssue: String
    val bugSubject: String
    val bugSubjectPlaceholder: String
    val bugDescribe: String
    val bugDescribePlaceholder: String
    val bugSubmit: String
    val bugSuccess: String

    // ---- Feedback ----
    val feedbackTitle: String
    val feedbackQuestion: String
    val feedbackRate: String
    val feedbackTapToRate: String
    val feedbackSorry: String
    val feedbackThanks: String
    val feedbackExcellent: String
    val feedbackYourFeedback: String
    val feedbackPlaceholder: String
    val feedbackSubmit: String
    val feedbackThankYou: String

    // ---- About ShortsCap hub + pages ----
    val aboutHubTitle: String
    val aboutHubAbout: String
    val aboutHubFeatures: String
    val aboutHubTechnologies: String
    val aboutHubVersionBuild: String
    val aboutHubCopyright: String
    val aboutTitle: String
    val aboutMission: String
    val aboutMissionText: String
    val aboutVision: String
    val aboutVisionText: String
    val aboutPurpose: String
    val aboutPurposeText: String
    val aboutIntro: String
    val aboutIntroText: String
    val featuresTitle: String
    val featureAppBlocking: String
    val featureAppBlockingText: String
    val featureUsageTracking: String
    val featureUsageTrackingText: String
    val featureFocusMode: String
    val featureFocusModeText: String
    val featureDigitalWellbeing: String
    val featureDigitalWellbeingText: String
    val featureSecureAuth: String
    val featureSecureAuthText: String
    val techTitle: String
    val techAndroid: String
    val techAndroidText: String
    val techKotlin: String
    val techKotlinText: String
    val techCompose: String
    val techComposeText: String
    val techPython: String
    val techPythonText: String
    val techAws: String
    val techAwsText: String
    val versionBuildTitle: String
    val versionLabel: String
    val buildLabel: String
    val copyrightTitle: String

    // ---- Legal ----
    val legalPrivacy: String
    val legalTerms: String
    val legalLoading: String

    // ---- Profile ----
    val profileTitle: String
    val profileTapToChange: String
    val profileFullName: String
    val profileNamePlaceholder: String
    val profileEmail: String
    val profileEmailPlaceholder: String
    val profileReadOnly: String
    val profileGender: String
    val profileGenderPlaceholder: String
    val profileMale: String
    val profileFemale: String
    val profilePreferNot: String
    val profileDob: String
    val profileDobPlaceholder: String
    val profileSaveChanges: String
    val profileChangePicture: String

    companion object {
        /** Resolves the catalog for [language] — pure function, usable from the ViewModel. */
        fun forLanguage(language: AppLanguage): AppStrings = when (language) {
            AppLanguage.ENGLISH -> EnglishStrings
            AppLanguage.HINDI -> HindiStrings
            AppLanguage.URDU -> UrduStrings
            AppLanguage.CHINESE -> ChineseStrings
            AppLanguage.SPANISH -> SpanishStrings
        }
    }
}

/** Active string catalog for the current language; provided in ShortsCapApp. */
val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }
