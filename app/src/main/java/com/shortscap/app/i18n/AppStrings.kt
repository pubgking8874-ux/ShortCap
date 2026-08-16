package com.shortscap.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.shortscap.app.study.StudyDay

/**
 * The complete user-facing string catalog for the logged-in ShortsCap
 * experience. Every screen, dialog, toast and content description in the
 * Dashboard (Home, Activity, Rank, Web, Settings + sub-pages, Drawer +
 * sub-pages, Profile) reads its text from [AppStrings] — never from
 * hardcoded literals.
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

    /** "Press back again to exit" — first Back press at a tab root. */
    val exitConfirmToast: String

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
    /** Home Quick Status card — today's total usage (same data as Activity → Daily). */
    val homeTodayUsage: String
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

    // ---- Rank ----
    /** Bottom-nav label + screen title. */
    val rankTitle: String
    /** "Your Rank" — the user's own ranking label on the status card. */
    val rankYourRank: String
    /** "Your Score" — the user's own score label (fixed wording, NOT "Focus Score"). */
    val rankYourScore: String
    /** Formats the weekly movement hint, e.g. "+3 positions this week". */
    fun rankPositionChange(positions: Int): String
    /** "This Week" — time filter option. */
    val rankThisWeek: String
    /** "This Month" — time filter option. */
    val rankThisMonth: String
    /** "Leaderboard" — heading above the full ranking list. */
    val rankLeaderboard: String
    /** "1st Place" — podium heading. */
    val rankFirstPlace: String
    /** "2nd Place" — podium heading. */
    val rankSecondPlace: String
    /** "3rd Place" — podium heading. */
    val rankThirdPlace: String
    /** "You" — label for the current user's row in the leaderboard. */
    val rankYou: String
    /** Column header / progress metric name — the user's score. */
    val rankScore: String
    /** "Your Progress" — the metrics placeholder section heading. */
    val rankYourProgress: String
    /** "Shorts" — progress metric label. */
    val rankShorts: String
    /** "Distracting Apps" — progress metric label. */
    val rankDistractingApps: String
    /** "Study Sessions" — progress metric label. */
    val rankStudySessions: String
    /** Formats a score value, e.g. "Score 86". */
    fun rankScoreValue(value: Int): String
    /** "Ahead by" — prefix for the movement badge, e.g. "Ahead by 3 positions". */
    val rankAheadBy: String
    /** "Behind by" — prefix for the movement badge, e.g. "Behind by 2 positions". */
    val rankBehindBy: String
    /** "No change this week" — movement badge when the rank is unchanged. */
    val rankNoChange: String
    /** "Loading ranking..." — loading placeholder state. */
    val rankLoading: String
    /** "No ranking data yet" — empty placeholder state. */
    val rankEmpty: String
    /** "Unable to load ranking" — error placeholder state. */
    val rankError: String

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
    val webVerifyChecking: String
    val webVerifyVerified: String
    val webVerifyInvalid: String
    val webVerifyNotFound: String
    val webVerifyTemporary: String
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

    // ---- Sound & Effects (central app-sounds control) ----
    val soundEffectsTitle: String
    val soundEffectsBreakReminder: String
    val soundEffectsScheduleReminder: String
    val soundEffectsLimitWarning: String
    val soundEffectsLimitReached: String
    /** Section heading — "Study Mode" (label only, never navigates). */
    val soundStudySection: String
    /** Section heading — "Monitoring" (label only, never navigates). */
    val soundMonitoringSection: String
    /** Section heading — "Notifications" (label only, never navigates). */
    val soundNotificationsSection: String
    // Section info popups (ⓘ buttons next to each heading)
    /** Content description for the ⓘ button on a section heading. */
    val soundInfoButton: String
    val soundStudyInfoTitle: String
    val soundStudyInfoDesc: String
    val soundMonitoringInfoTitle: String
    val soundMonitoringInfoDesc: String
    val soundNotificationsInfoTitle: String
    val soundNotificationsInfoDesc: String
    // Study sounds
    val soundStudySessionStart: String
    val soundStudySessionStartDesc: String
    val soundStudySessionEnd: String
    val soundStudySessionEndDesc: String
    val soundBreakReminderDesc: String
    val soundBreakSessionStart: String
    val soundBreakSessionStartDesc: String
    val soundBreakSessionEnd: String
    val soundBreakSessionEndDesc: String
    val soundScheduleReminderDesc: String
    // Monitoring sounds
    val soundLimitWarningDesc: String
    val soundLimitReachedDesc: String
    // Notification sound
    val soundNotificationDesc: String
    // Individual sound configuration screen
    /** "Current" — label shown next to the currently selected sound. */
    val soundCurrentLabel: String
    /** "Add from Device" — opens the custom-audio flow for one sound. */
    val soundAddFromDevice: String
    /** Subtitle under the "Add from Device" action. */
    val soundAddFromDeviceDesc: String
    // Add from Device placeholder screen
    val soundAddCustomTitle: String
    val soundAddCustomDesc: String
    val soundAddCustomEmptyTitle: String
    val soundAddCustomEmptyDesc: String
    /** "Choose File" — placeholder action that will open the device picker. */
    val soundChooseFile: String
    // Bundled local sound files (all_sounds assets) — states inside each category
    /** "No sounds available" — empty state when a category folder has no audio files. */
    val soundNoSounds: String
    /** Empty-state explanation telling the user where to put audio files. */
    fun soundNoSoundsDesc(folder: String): String
    /** "Choose" — heading above the list when a category contains multiple sounds. */
    val soundChoose: String
    // Sound library (shared by every category picker)
    val appSoundDefault: String
    val appSoundGentleChime: String
    val appSoundSoftBell: String
    val appSoundCalmTone: String
    val appSoundFocusTone: String
    val appSoundWarningPulse: String
    val appSoundLimitAlert: String
    val appSoundSuccessChime: String
    /** "Preview" — content description of the small ▶ buttons. */
    val soundEffectsPreview: String

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

    // ---- Study Mode (Monitoring section) ----
    val studyTitle: String
    val studySessionSection: String
    val studySettingsSection: String
    val studyStatusLabel: String
    val studyStatusActive: String
    val studyStatusInactive: String
    val studyRemaining: String
    val studyRestrictionNote: String
    val studyStartSession: String
    // Study Mode activation confirmation dialog (shown when the user turns the
    // toggle ON — the session starts only after "Start Study" is confirmed).
    val studyStartConfirmTitle: String
    val studyStartConfirmRestrictions: String
    val studyStartConfirmStart: String
    /** "Duration" — label for the selected-duration row inside the start dialog. */
    val studyDurationLabel: String
    /** Formats the selected Study Duration, e.g. "45 Minutes" / "1 Hour 30 Minutes". */
    fun studyDurationText(minutes: Int): String
    val studyDuration: String
    /** "Hours" — label of the Hours wheel in the duration/clock pickers. */
    val studyDurationHours: String
    /** "Save" — confirms the wheel picker selection (time/duration/reminder). */
    val studyDurationSave: String
    /** "Custom" — duration selector option opening the wheel picker. */
    val studyDurationCustom: String
    /** "How long should Study Mode stay active?" — duration selector subtitle. */
    val studyDurationPickerSubtitle: String
    /** "Select a duration greater than zero." — shown when the wheel picker is at 0h 0m. */
    val studyDurationRequired: String
    val studyBreakReminder: String
    /** "Every" — prefix of the Break Reminder summary, e.g. "Every 25 Minutes". */
    val studyBreakReminderEvery: String
    /** "OFF" — Break Reminder summary when disabled. */
    val studyBreakReminderOff: String
    /** Break Reminder info (ⓘ) popup — explains the feature. */
    val studyBreakReminderInfoDesc: String
    /** "Remind Me After" — first-reminder interval label. */
    val studyBreakReminderIntervalLabel: String
    /** "Reminder Pattern" — Once / Repeat label. */
    val studyBreakReminderPatternLabel: String
    /** "Once" — the reminder fires a single time. */
    val studyBreakReminderPatternOnce: String
    /** "Repeat" — the reminder repeats every interval while Study Mode is active. */
    val studyBreakReminderPatternRepeat: String
    /** "Reminder Sound" — sound preference label. */
    val studyBreakReminderSoundLabel: String
    val studyBreakSoundDefault: String
    val studyBreakSoundSoftBell: String
    val studyBreakSoundGentleChime: String
    val studyBreakSoundFocusTone: String
    val studyBreakSoundCustom: String
    /** "Custom" — opens the wheel selector for a custom interval. */
    val studyBreakReminderCustom: String
    /** "Save Reminder" — saves the Break Reminder configuration. */
    val studyBreakReminderSave: String
    /** "Schedule Conflict" — warning when reminders overlap a scheduled session. */
    val studyBreakConflictTitle: String
    /** "Adjust Reminder" — return to editing the interval. */
    val studyBreakConflictAdjustReminder: String
    /** "Keep Schedule" — keep the schedule as-is and save the reminder anyway. */
    val studyBreakConflictKeepSchedule: String
    /** "Scheduled Study" — label above the conflict timeline. */
    val studyBreakConflictScheduledStudy: String
    /** "Conflict" — label above the conflicting reminder times. */
    val studyBreakConflictLabel: String
    /** "No schedule conflicts found." — quiet caption when the reminder cycle is clear. */
    val studyBreakConflictNone: String
    /** "Your selected break reminder may overlap with a scheduled study session at <time>." */
    fun studyBreakConflictMessage(time: String): String
    val studyBreakDuration: String
    val studySoundMode: String
    val studySoundSound: String
    val studySoundVibrate: String
    val studySoundSilent: String
    // Sound Mode → Android device ringer mode (system audio access)
    val soundModeAccessRequiredTitle: String
    val soundModeAccessRequiredDesc: String
    val soundModeOpenSettings: String
    val soundModeChangeFailedToast: String
    val studySchedule: String
    val studyScheduleStart: String

    // ---- Study Schedule — multiple schedules, each with its own subject,
    //      days, start time, duration, reminder and enabled state ----
    /** "Schedules" — count label on the Study Mode screen schedule row. */
    val studyScheduleLabel: String
    /** "Add Schedule" — primary action + create-screen title. */
    val studyScheduleAdd: String
    /** "Add Schedule" — title of the create screen. */
    val studyScheduleNewTitle: String
    /** "Edit" — schedule card action. */
    val studyScheduleEdit: String
    /** "No schedules yet" — schedule row subtitle + list empty-state title. */
    val studyScheduleEmptyTitle: String
    /** Empty-state description on the schedule list screen. */
    val studyScheduleEmptyDesc: String
    /** "Edit Schedule" — title of the edit screen. */
    val studyScheduleEditTitle: String
    /** "Subject" — schedule subject field label. */
    val studyScheduleSubject: String
    /** Subject placeholder, e.g. "Mathematics". */
    val studyScheduleSubjectPlaceholder: String
    /** Validation error when the subject is empty. */
    val studyScheduleSubjectRequired: String
    /** "Days" — the days-of-week selector label. */
    val studyScheduleDays: String
    /** Validation error when no day is selected. */
    val studyScheduleDaysRequired: String
    /** "Reminder" — schedule reminder row label. */
    val studyScheduleReminder: String
    /** "No Reminder" — reminder picker option. */
    val studyScheduleReminderNone: String
    /** Formats a reminder lead time, e.g. "15 Minutes Before" / "1 Hour Before". */
    fun studyScheduleReminderLabel(minutes: Int): String
    /** Toast after a schedule is saved/updated. */
    val studyScheduleSavedToast: String
    /** Toast after a schedule is deleted. */
    val studyScheduleDeletedToast: String
    /** Short weekday label for schedule days, e.g. "Mon". */
    fun studyDayShort(day: StudyDay): String

    /** "Allow Apps / Website" — Study Mode section + page title (the slash is intentional). */
    val studyAllowedItems: String
    val studyAllowedApps: String
    val studyAllowedWebsites: String
    val studyAllowedWebsitePlaceholder: String
    val studyAllowedAdd: String
    val studyAllowedInvalid: String
    val studyAllowedDuplicate: String
    /** "Add Website" — section heading above the add-website input. */
    val studyAllowedAddTitle: String
    /** "Add App" — header three-dot menu action + Add App picker title. */
    val studyAllowedMenuAddApp: String
    /** "Manage Apps" — header three-dot menu action opening the allowed-apps manager. */
    val studyAllowedMenuManageApps: String
    /** "No apps available to add." — empty state in the Add App picker. */
    val studyAllowedPickerEmpty: String
    /** "No apps allowed yet." — empty state in the Manage Apps dialog. */
    val studyAllowedManageEmpty: String
    /** "Study Mode Access" — title of the Allow Websites info popup. */
    val studyAllowedInfoTitle: String
    /** Info popup: the websites allowed here remain available while Study Mode is active. */
    val studyAllowedInfoDesc: String
    val studySummary: String
    val studySummarySessionsToday: String
    val studySummaryTimeToday: String
    val studySummaryLastSession: String
    val studySummaryNone: String
    val studySessionStartedToast: String
    val studySessionCompleteToast: String
    val studyHomeTitle: String

    // "Stop Study Mode?" confirmation — the shared early-exit gate on the Home
    // page AND Study Mode (leads to the Exit Passcode).
    val studyStopTitle: String
    val studyStopMessage: String
    val studyStopAction: String

    // ---- Exit Passcode (Study Mode protection & recovery) ----
    val studyFocusProtection: String
    val focusPasscodeTitle: String
    val focusPasscodeSetupTitle: String
    val focusPasscodeSetupDesc: String
    val focusPasscodeSetupFieldLabel: String
    val focusPasscodeSetupSave: String
    /** Status shown on the Study Mode card when no Exit Passcode exists yet. */
    val focusPasscodeNotSet: String
    /** Green success status — "Passcode Set" on the card + status screen (no checkmark glyph — the green styling alone communicates success). */
    val focusPasscodeSetStatus: String
    /** "Change Passcode" — opens the existing recovery flow from the status screen. */
    val focusPasscodeChange: String
    val focusPasscodeCreatedToast: String
    val focusPasscodeVerifyTitle: String
    val focusPasscodeVerifyDesc: String
    val focusPasscodeVerifyPlaceholder: String
    val focusPasscodeVerifyButton: String
    val focusPasscodeVerifyOnly: String
    val focusPasscodeForgot: String
    val focusPasscodeIncorrect: String
    val focusPasscodeEndedToast: String
    val focusPasscodeVerifiedToast: String
    val focusPasscodeLockedNote: String
    val focusPasscodeRecoverTitle: String
    val focusPasscodeRecoverDesc: String
    val focusPasscodeRecoverEmail: String
    val focusPasscodeRecoverMobile: String
    val focusPasscodeEmailTitle: String
    val focusPasscodeEmailLabel: String
    val focusPasscodeEmailPlaceholder: String
    val focusPasscodeEmailSend: String
    val focusPasscodeEmailInvalid: String
    val focusPasscodeMobileTitle: String
    val focusPasscodeMobileLabel: String
    val focusPasscodeMobilePlaceholder: String
    val focusPasscodeMobileSend: String
    val focusPasscodeMobileInvalid: String
    val focusPasscodeOtpTitle: String
    val focusPasscodeOtpEmailSent: String
    val focusPasscodeOtpMobileSent: String
    val focusPasscodeOtpEnterLabel: String
    val focusPasscodeOtpVerify: String
    val focusPasscodeOtpResend: String
    val focusPasscodeOtpIncorrect: String
    val focusPasscodeCreateTitle: String
    val focusPasscodeNewLabel: String
    val focusPasscodeConfirmLabel: String
    val focusPasscodeCreateSave: String
    val focusPasscodeUpdatedToast: String
    val focusPasscodeTooShort: String
    val focusPasscodeMismatch: String
    /** "Delete" — three-dot menu action that removes the Exit Passcode configuration. */
    val focusPasscodeDelete: String
    /** Toast after the Exit Passcode configuration is deleted. */
    val focusPasscodeDeletedToast: String

    /** "Set on: Aug 8, 2026" — device-local date the Exit Passcode was set. */
    fun focusPasscodeSetOn(date: String): String

    /** "Set at: 7:42 PM" — device-local time the Exit Passcode was set. */
    fun focusPasscodeSetAt(time: String): String

    /** "Resend code in 45s" — resend countdown on the OTP page. */
    fun focusPasscodeOtpResendIn(seconds: Int): String

    /** "Demo code: 123456" — development-only line for the LOCAL mock OTP. */
    fun focusPasscodeOtpDemo(code: String): String

    // ---- Monitoring ----
    val monitoringTitle: String
    val monitoringSection: String
    val monitoringDevice: String
    val monitoringDeviceDesc: String
    val monitoringDeviceInfoTitle: String
    val monitoringDeviceInfoMessage: String
    val monitoringDeviceInfoPermission: String
    val monitoringDeviceInfoDisabled: String
    val monitoringBlockedApps: String
    val monitoringAllowedApps: String
    val monitoringStrictMode: String
    val monitoringStrictModeDesc: String
    val monitoringShortsSection: String
    val monitoringShortsControl: String
    val monitoringShortsControlDesc: String

    // ---- Shorts HUD ----
    val shortsHudTitle: String
    val shortsHudDesc: String
    val shortsHudEnabled: String
    val shortsHudAppearance: String
    val shortsHudAppearanceShortsCap: String
    val shortsHudAppearanceBrain: String
    /** "Counter" — the clean numeric HUD mode (display name, NOT "Live Counter"). */
    val shortsHudAppearanceLiveCounter: String
    /** The Counter mode's compact mock preview value, e.g. "127 / 200". */
    val shortsHudPreviewCounterValue: String
    /** Accessibility description of the Brain appearance preview. */
    val shortsHudPreviewBrain: String
    /** Accessibility description of the Counter appearance preview. */
    val shortsHudPreviewCounter: String
    /** Accessibility description of the ShortsCap appearance preview. */
    val shortsHudPreviewShortsCap: String
    /** Accessibility "selected" state read for the appearance radio options. */
    val shortsHudSelected: String
    /** Accessibility "not selected" state read for the appearance radio options. */
    val shortsHudNotSelected: String
    val shortsHudPermissionMissing: String
    val shortsHudPermissionDesc: String
    val shortsHudOpenSettings: String


    val monitoringSchedule: String

    // ---- Home Monitoring Paused section (priority page) + resume popup ----
    val homeMonitoringPausedTitle: String
    val resumeMonitoringDialogTitle: String
    val resumeMonitoringDialogMessage: String
    val resumeMonitoringDialogRequired: String
    val resumeMonitoringDialogContinue: String
    val resumeMonitoringDialogNotNow: String

    /** Toast when no Android settings screen could be opened for a missing permission. */
    val permissionSettingsUnavailableToast: String

    /** Toast when monitoring auto-resumes after the required permissions return. */
    val monitoringStartedToast: String

    // ---- Time option labels ----
    val minutesLabel: String
    /** "AM" — period label in the 12-hour wheel clock picker. */
    val studyTimeAm: String
    /** "PM" — period label in the 12-hour wheel clock picker. */
    val studyTimePm: String

    // ---- Permissions (status overview) ----
    val permissionsTitle: String

    // Permission statuses — ONE consistent system app-wide: Enabled (active /
    // working) and Disabled (missing / denied / inactive). Nothing else is
    // shown as a permission status.
    val permStatusEnabled: String
    val permStatusDisabled: String

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
    /** Overlay purpose — the small monitoring Brain indicator above supported short-video apps. */
    val permOverlayDesc: String
    val permNotifications: String
    val permNotificationsDesc: String
    val permBattery: String
    val permBatteryDesc: String
    val permStorage: String
    val permStorageDesc: String
    /** "System Audio Access" — Android Notification Policy Access needed by Study Mode's Sound Mode (ring mode control). */
    val permSystemAudioAccess: String
    /** Purpose shown on the System Audio Access permission detail page. */
    val permSystemAudioAccessDesc: String

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
    /** Compact timeline toggle — "Show more" / "Show less". */
    val chartShowMore: String
    val chartShowLess: String

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
