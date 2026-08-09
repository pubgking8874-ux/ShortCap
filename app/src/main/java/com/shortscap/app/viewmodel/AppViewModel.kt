package com.shortscap.app.viewmodel

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shortscap.app.activity.ActivityPeriod
import com.shortscap.app.activity.ActivityRange
import com.shortscap.app.activity.ActivityRepository
import com.shortscap.app.appearance.AppearanceRepository
import com.shortscap.app.appearance.FontMode
import com.shortscap.app.appearance.TextSizeMode
import com.shortscap.app.charts.ChartStyle
import com.shortscap.app.theme.ScFonts
import com.shortscap.app.theme.ThemeMode
import com.shortscap.app.theme.ThemePreferenceStore
import com.shortscap.app.i18n.AppLanguage
import com.shortscap.app.i18n.AppStrings
import com.shortscap.app.i18n.LanguagePreferenceStore
import com.shortscap.app.icons.IconRepository
import com.shortscap.app.icons.IconStyle
import com.shortscap.app.model.DrawerScreen
import com.shortscap.app.model.MonitoringSettings
import com.shortscap.app.model.ProfileData
import com.shortscap.app.monitoring.MonitoringService
import com.shortscap.app.notifications.NotificationRepository
import com.shortscap.app.notifications.NotificationSetting
import com.shortscap.app.notifications.NotificationSettingId
import com.shortscap.app.permissions.MonitoringRequiredPermissionIds
import com.shortscap.app.permissions.PermissionId
import com.shortscap.app.permissions.PermissionInfo
import com.shortscap.app.permissions.PermissionRepository
import com.shortscap.app.permissions.PermissionStatus
import com.shortscap.app.settings.SettingsManager
import com.shortscap.app.sounds.AppSound
import com.shortscap.app.sounds.SoundEffectCategory
import com.shortscap.app.sounds.SoundEffectsConfig
import com.shortscap.app.sounds.SoundEffectsRepository
import com.shortscap.app.study.DeviceSoundModeResult
import com.shortscap.app.study.BreakReminderConfig
import com.shortscap.app.study.FocusPasscodeEntry
import com.shortscap.app.study.FocusPasscodePreferenceStore
import com.shortscap.app.study.FocusPasscodeRepository
import com.shortscap.app.study.FocusRecoveryMethod
import com.shortscap.app.study.StudyDay
import com.shortscap.app.study.StudyModeSettings
import com.shortscap.app.study.StudyPreferenceStore
import com.shortscap.app.study.StudyScheduleEntry
import com.shortscap.app.study.StudySession
import com.shortscap.app.study.StudySoundMode
import com.shortscap.app.study.StudySummary
import com.shortscap.app.study.applySoundMode
import com.shortscap.app.study.currentSoundMode
import com.shortscap.app.study.hasSoundModeAccess
import com.shortscap.app.study.maskContact
import com.shortscap.app.model.ScCircularMetric
import com.shortscap.app.model.ScScreen
import com.shortscap.app.model.SettingsDestination
import com.shortscap.app.favicon.FaviconRepository
import com.shortscap.app.web.WebAnalyticsPeriod
import com.shortscap.app.web.WebRepository
import com.shortscap.app.web.WebRule
import com.shortscap.app.web.WebRuleStatus
import com.shortscap.app.web.WebsiteBlockingEngine
import com.shortscap.app.web.PlaceholderBlockingEngine
import com.shortscap.app.web.WebUsageRecord
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of truth for app-wide UI state.
 * Each field below corresponds 1:1 to a `useState` call in the RN root
 * (`ShortsCapApp`) or one of its screens, per the "state management" and
 * "MVVM Architecture / StateFlow / ViewModel" requirements of the migration.
 */
data class AppUiState(
    // Root: useState("home"), drawerOpen, profileScreenOpen, loading, toastMsg
    val screen: ScScreen = ScScreen.HOME,
    val drawerOpen: Boolean = false,
    val profileScreenOpen: Boolean = false,
    val homeLoading: Boolean = true,
    val toastMessage: String? = null,

    // Home hero circular analytics — mocked here for the frontend; the
    // ViewModel will replace these with backend API responses later
    // (only the data source changes, the UI stays the same).
    val homeMetrics: List<ScCircularMetric> = listOf(
        ScCircularMetric(id = "shorts-watch-time", label = "Today's Shorts Watch Time", value = "1h 30m", progress = 0.75f),
        ScCircularMetric(id = "shorts-watched", label = "Today's Shorts Watched", value = "245", unit = "Shorts", progress = 0.65f),
    ),

    // Home Quick Stats "Apps Used" — apps with real usage today, derived from
    // the SAME ActivityRepository data the Activity → Daily screen renders
    // (one centralized source; the future backend feeds both through the same
    // repository seam, so Home and Activity can never drift apart).
    val homeAppsUsedToday: Int =
        ActivityRepository.reportFor(ActivityPeriod.DAILY).distribution.count { it.minutes > 0 },

    // ActivityScreen: range chip + dedicated report screens. The report data
    // itself is derived in ActivityRepository.reportFor(period) /
    // rangeReportFor(range) — the UI only renders it, and the global chart
    // style changes the visualization only.
    val activityRange: String = "Weekly",
    val activityReport: ActivityPeriod? = null,
    // Per-day detail for a tapped monthly date range (Aug 1–7, …).
    val activityRangeDetail: ActivityRange? = null,

    // Web section: analytics period + website rules + raw usage records. All
    // data flows from WebRepository (seeds today; backend/database later) —
    // never hardcoded in the UI. Analytics summaries are derived from
    // [webUsageRecords] via WebRepository.analyticsSummary().
    val webPeriod: WebAnalyticsPeriod = WebAnalyticsPeriod.TODAY,
    val webRules: List<WebRule> = WebRepository.seedRules(),
    val webUsageRecords: List<WebUsageRecord> = WebRepository.seedUsageRecords(),

    // Deep link for the Web tab: when a Home Quick Stats card (Blocked Sites /
    // Allowed Websites) opens the Web tab, WebNavHost starts at this route
    // instead of the blocking root. Consumed (cleared) once it has been shown.
    val webStartRoute: String? = null,

    // Settings: dedicated sub-screen currently open (null = none). The
    // Monitoring settings live in [monitoring] — the single source of truth
    // for the Monitoring screen (backend GET/UPDATE APIs plug in later via a
    // repository seam without UI changes).
    val settingsDestination: SettingsDestination? = null,
    val monitoring: MonitoringSettings = MonitoringSettings(),

    // ---- Study Mode (General section) — SEPARATE from Device Monitoring,
    // Shorts Monitoring, Activity and History data. Lives in its own
    // study/ package with its own repository seam; the active session is
    // timestamp-based so the countdown stays exact across backgrounding. ----
    val studySettings: StudyModeSettings = StudyModeSettings(),
    val activeStudySession: StudySession? = null,
    val studySummary: StudySummary = StudySummary(),

    // Exit Passcode (Study Mode protection) — controls ONLY the
    // ability to end an active session early. [focusPasscodeSet] is loaded
    // from the salted-hash store; [focusPasscodeSetAtMillis] is the device
    // wall-clock timestamp of when it was set (displayed as "Set on / Set at"
    // — never the credential itself); [focusOtpDemoCode] /
    // [focusOtpContactMasked] exist purely for the LOCAL MOCK recovery flow
    // (the UI shows the demo code + masked contact because there is no
    // email/SMS backend yet) and disappear when the backend OTP APIs land —
    // the UI code does not change.
    val focusPasscodeSet: Boolean = false,
    /** Device wall-clock millis when the Exit Passcode was last set. */
    val focusPasscodeSetAtMillis: Long = 0L,
    val focusOtpDemoCode: String? = null,
    val focusOtpContactMasked: String? = null,

    // The Focus Passcode flow overlay (SETUP first-time create, or VERIFY to
    // end an active session). ONE shared flow — opened from BOTH the Home
    // page and General → Study Mode, so every exit path uses the exact same
    // verification + recovery screens (single Study Mode state, single
    // passcode, single recovery system — never two).
    val focusPasscodeFlow: FocusPasscodeEntry? = null,

    // Notifications module — every option's on/off state, persisted locally
    // by [NotificationRepository] (backend-ready: same shape maps 1:1 to a
    // future GET/POST /notifications/settings API).
    val notificationSettings: List<NotificationSetting> = NotificationRepository.seedSettings(),

    // Sound & Effects — the CENTRAL app-sounds configuration (master switch +
    // one sound per category). Persisted locally by SoundEffectsRepository;
    // every feature reads its sound from here (Break Reminder, Study Schedule,
    // Shorts limits, break start/end) — never per-feature sound systems.
    val soundEffects: SoundEffectsConfig = SoundEffectsRepository.defaults(),

    // Permissions management center — live statuses resolved from the Android
    // OS by [PermissionRepository]; refreshed automatically whenever a
    // Permissions screen resumes (e.g. after returning from Android Settings).
    val permissions: List<PermissionInfo> = PermissionRepository.seedPermissions(),

    // Theme preference (persisted via ThemePreferenceStore)
    val themeMode: ThemeMode = ThemeMode.DARK,

    // Appearance preference — global text scale, applied app-wide via a root
    // LocalDensity fontScale override (only typography changes; layouts,
    // icons, cards and spacing stay untouched). Persisted locally by
    // AppearanceRepository (backend-ready: maps 1:1 to a future
    // GET/POST /settings/appearance API).
    val textSizeMode: TextSizeMode = TextSizeMode.MEDIUM,

    // Global Chart Style preference — which visualization every supported
    // analytics chart uses (Bar Chart or Circular Chart). Presentation-only:
    // it never touches usage data. Persisted locally by AppearanceRepository
    // (backend-ready: maps 1:1 to a future UserPreferences.chartStyle entry).
    val chartStyle: ChartStyle = ChartStyle.DEFAULT,

    // Global Font preference — which bundled family the centralized typography
    // system (ScTextStyles → ScFonts) renders app-wide. Independent from the
    // Language setting. Persisted locally by AppearanceRepository
    // (backend-ready: maps 1:1 to a future UserPreferences.fontFamily entry).
    val fontMode: FontMode = FontMode.DEFAULT,

    // Icon Style preference — which icon system renders app-wide (ShortsCap
    // Original blue/black, or the Vibrant colorful category system). Held in
    // [LocalIconStyle] at the app root so every screen updates instantly on
    // Apply. Persisted locally by IconRepository (backend-ready: maps 1:1 to
    // a future user_id / selected_icon_style / updated_at backend entry).
    val iconStyle: IconStyle = IconStyle.ORIGINAL,

    // Language preference (persisted via LanguagePreferenceStore and applied
    // to the whole logged-in experience through LocalAppStrings). The Auth
    // flow always stays English and does not read this.
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,

    // True while a language change is being applied — ShortsCapApp shows a
    // smooth transition overlay instead of an abrupt screen reload.
    val languageApplying: Boolean = false,

    // Full-screen drawer sub-screen currently open (null = none). Modular:
    // each destination maps to a dedicated UI screen; backend APIs plug in
    // later without redesigning any screen.
    val drawerScreen: DrawerScreen? = null,

    // Local profile shown/edited on the Profile screen. Load / Update / Upload
    // Picture will come from backend APIs (ProfileRepository seam) later.
    val profile: ProfileData = ProfileData(),

    // Session placeholder — false shows the Auth flow (Splash -> Welcome ->
    // Login/CreateAccount/Guest) on launch. When AWS Cognito / the Python
    // backend / JWT are connected, set this from the session state so the
    // app opens straight to the Dashboard; no UI changes are required.
    val sessionActive: Boolean = false,
) {
    // Derived web counts — the SAME source of truth the Web section uses (the
    // Blocked / Allowed screens filter this exact webRules list), so the Home
    // Quick Stats numbers always match the actual website data. Computed as
    // body properties (custom getters can't live in constructor params) and
    // always in sync with [webRules].
    val blockedWebCount: Int get() = webRules.count { it.status == WebRuleStatus.BLOCKED }
    val allowedWebCount: Int get() = webRules.count { it.status == WebRuleStatus.ALLOWED }

    // Home Quick Stats "Today Usage" — today's TOTAL usage minutes, derived
    // live from the exact same Daily report (ActivityRepository Daily
    // aggregation) that powers Activity → Daily chart + timeline. Because it
    // is a computed getter over the single data seam, the Home card always
    // reflects the current-day total — never a separate hardcoded value.
    val homeTodayUsageMinutes: Int
        get() = ActivityRepository.reportFor(ActivityPeriod.DAILY).totalMinutes

    // ---- Centralized monitoring-paused state (derived, never stored) ----
    // Monitoring is considered PAUSED whenever it is switched on but a
    // required permission is missing. Both properties are derived from the
    // SAME live [permissions] list the Permissions screen renders (refreshed
    // automatically on every app resume), so Home, Monitoring, Permissions
    // and the future backend can never disagree or hold duplicate state.
    val missingRequiredMonitoringPermissions: List<PermissionId> get() =
        permissions
            .filter { it.id in MonitoringRequiredPermissionIds && it.status != PermissionStatus.GRANTED }
            .map { it.id }

    val monitoringPaused: Boolean get() =
        monitoring.enabled && missingRequiredMonitoringPermissions.isNotEmpty()

    // ---- Study Mode derived state (timestamp-based, never a visual-only
    //      countdown). The session carries wall-clock start/end; these are
    //      pure derivations consumed by Home and the Study Mode screen. ----
    val studyModeActive: Boolean get() = activeStudySession != null && !activeStudySession.finished

    /** Exact remaining milliseconds (endTime - currentTime). */
    val studyRemainingMillis: Long get() = activeStudySession?.remainingMillis ?: 0L

    /** Total session length, used to draw the countdown ring progress. */
    val studyTotalMillis: Long get() = (activeStudySession?.durationMinutes ?: 0) * 60_000L

    // Restricted Mode is the enforcement state Study Mode activates: it is
    // simply "Study Mode is active", and while it is on the user cannot
    // disable the restriction controls (guarded in the setters below).
    val restrictedModeActive: Boolean get() = studyModeActive
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val themeStore = ThemePreferenceStore(application)
    private val languageStore = LanguagePreferenceStore(application)
    private val studyStore = StudyPreferenceStore(application)
    // Exit Passcode — salted-hash storage + mock OTP repository seam.
    private val focusPasscodeStore = FocusPasscodePreferenceStore(application)
    private val focusPasscodeRepo = FocusPasscodeRepository()
    private val _uiState = MutableStateFlow(
        AppUiState(
            focusPasscodeSet = focusPasscodeStore.isPasscodeSet(),
            focusPasscodeSetAtMillis = focusPasscodeStore.getPasscodeSetAtMillis() ?: 0L,
            themeMode = themeStore.loadThemeMode(),
            appLanguage = languageStore.loadLanguage(),
            notificationSettings = NotificationRepository.loadSettings(application),
            soundEffects = SoundEffectsRepository.loadSettings(application),
            textSizeMode = AppearanceRepository.loadTextSizeMode(application),
            chartStyle = AppearanceRepository.loadChartStyle(application),
            iconStyle = IconRepository.loadIconStyle(application),
            fontMode = AppearanceRepository.loadFontMode(application),
        // Apply the persisted font to the centralized typography system BEFORE
        // the first frame renders, so there is never a default-font flash.
        ).also { ScFonts.apply(it.fontMode) },
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        // Resolve the REAL Android permission state on launch so the derived
        // monitoring-paused state is correct from the very first frame
        // (seeded placeholders are replaced by live OS checks before the Home
        // skeleton clears). The same refresh also runs on every app resume.
        refreshPermissions()

        // Restore any Study Mode session that was running before the process
        // died / app was reopened — the countdown is timestamp-based, so the
        // remaining duration is still exact after reload. An already-expired
        // session is ended immediately (summary recorded, restriction states
        // restored); a live one resumes ticking. Mirrors the
        // ThemePreferenceStore restore pattern.
        studyStore.loadActiveSession()?.let { stored ->
            strictModeBeforeStudy = stored.previousStrictMode
            monitoringBeforeStudy = stored.previousMonitoringEnabled
            _uiState.update { it.copy(activeStudySession = stored.session) }
            if (stored.session.finished) finishStudySession()
            else startStudyTicker()
        }

        // Mirrors: useEffect(() => { setTimeout(() => setLoading(false), 900) }, [])
        viewModelScope.launch {
            delay(900)
            _uiState.update { it.copy(homeLoading = false) }
        }
    }

    // ---- Navigation / chrome ----
    fun setScreen(screen: ScScreen) = _uiState.update { it.copy(screen = screen) }
    fun openDrawer() = _uiState.update { it.copy(drawerOpen = true) }
    fun closeDrawer() = _uiState.update { it.copy(drawerOpen = false) }

    // ---- Profile screen (opened from the Dashboard top bar) ----
    fun openProfileScreen() = _uiState.update { it.copy(profileScreenOpen = true) }
    fun closeProfileScreen() = _uiState.update { it.copy(profileScreenOpen = false) }

    // Localized toast — resolves the message through the active language's
    // catalog so every toast follows the selected language.
    fun showToast(message: (AppStrings) -> String) {
        showToast(message(AppStrings.forLanguage(uiState.value.appLanguage)))
    }

    // ---- Profile (local-only today; backend seam: ProfileRepository) ----
    fun saveProfile(fullName: String, gender: String?, dateOfBirth: String?) {
        _uiState.update { state ->
            state.copy(
                profile = state.profile.copy(
                    fullName = fullName.trim(),
                    gender = gender,
                    dateOfBirth = dateOfBirth,
                ),
            )
        }
        showToast { it.toastProfileSaved }
    }

    // Picked via the Android Photo Picker on the Profile screen. Future: Crop
    // step + upload through ProfileRepository.uploadProfilePicture.
    fun updateProfilePicture(uri: String) {
        _uiState.update { state ->
            state.copy(profile = state.profile.copy(pictureUri = uri))
        }
        showToast { it.toastProfilePictureUpdated }
    }

    // ---- Theme (persists; applies instantly, no restart needed) ----
    fun setThemeMode(mode: ThemeMode) {
        themeStore.saveThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    // ---- Appearance (persists locally; future: AppearanceRepository cloud
    //      sync / analytics seams) ----
    fun setTextSizeMode(mode: TextSizeMode) {
        AppearanceRepository.saveTextSizeMode(getApplication(), mode)
        _uiState.update { it.copy(textSizeMode = mode) }
        // Future backend: AppearanceRepository.syncAppearanceToCloud(mode).
    }

    // ---- Chart Style (global visualization preference; persists locally and
    //      updates instantly — Activity, Web Analytics and every future chart
    //      read AppUiState.chartStyle and re-render with the same data) ----
    fun setChartStyle(style: ChartStyle) {
        if (style == uiState.value.chartStyle) return
        AppearanceRepository.saveChartStyle(getApplication(), style)
        _uiState.update { it.copy(chartStyle = style) }
        // Future backend: sync as a user preference (UserPreferences.chartStyle),
        // never inside usage/analytics records.
    }

    // ---- Font (persists locally; applies instantly through the centralized
    //      typography system — the whole app re-renders via ScFonts) ----
    fun setFontMode(mode: FontMode) {
        if (mode == uiState.value.fontMode) return
        AppearanceRepository.saveFontMode(getApplication(), mode)
        _uiState.update { it.copy(fontMode = mode) }
        showToast { it.toastFontApplied }
        // Future backend: sync as a user preference (UserPreferences.fontFamily),
        // never inside usage/analytics/account records.
    }

    // ---- Icon Style (persists locally; updates the global icon provider
    //      instantly — the whole app reflects the new style via LocalIconStyle) ----
    fun setIconStyle(style: IconStyle) {
        if (style == uiState.value.iconStyle) return
        IconRepository.saveIconStyle(getApplication(), style)
        _uiState.update { it.copy(iconStyle = style) }
        // Future backend: IconRepository.syncIconStyleToCloud(style);
        // Future analytics: IconRepository.trackIconStyleAnalytics(style).
    }

    // ---- Session (mock seam for the auth flow) ----
    // Called by the auth graph's onExitToDashboard (Continue as Guest / mock
    // Sign In / mock Create Account) to enter the Dashboard. Backend login
    // will replace this with real session state — the UI stays the same.
    fun setSessionActive(active: Boolean) = _uiState.update { it.copy(sessionActive = active) }

    // ---- Drawer sub-screens (modular; backend-ready) ----
    fun openDrawerScreen(screen: DrawerScreen) =
        _uiState.update { it.copy(drawerOpen = false, drawerScreen = screen) }

    fun closeDrawerScreen() = _uiState.update { it.copy(drawerScreen = null) }

    // ---- Language (persists locally; future: LanguageRepository cloud sync) ----
    private var languageJob: Job? = null

    // Applies a new language across the entire logged-in experience. The
    // language is persisted and swapped immediately; a brief applying overlay
    // covers the transition so the UI refresh feels smooth, not abrupt.
    fun applyLanguage(language: AppLanguage) {
        if (language == uiState.value.appLanguage) return
        languageStore.saveLanguage(language)
        _uiState.update { it.copy(appLanguage = language, languageApplying = true) }
        languageJob?.cancel()
        languageJob = viewModelScope.launch {
            delay(650)
            _uiState.update { it.copy(languageApplying = false) }
        }
        // Future backend: LanguageRepository.syncLanguageToCloud(language)
    }

    // ---- Toast (mirrors showToast + clearTimeout/setTimeout dance) ----
    private var toastJob: Job? = null
    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2200)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    // ---- Home Quick Stats (cards open the real screens, same data) ----

    /** Apps Used card → Activity page with the Daily tab selected. */
    fun openActivityDaily() = _uiState.update {
        it.copy(
            screen = ScScreen.ACTIVITY,
            activityRange = "Daily",
            // Close any report / range-detail page so the Daily page is shown.
            activityReport = null,
            activityRangeDetail = null,
        )
    }

    /** Blocked Sites / Allowed Websites cards → Web tab, opening that screen. */
    fun openWebSection(route: String) =
        _uiState.update { it.copy(screen = ScScreen.WEB, webStartRoute = route) }

    /** Called by WebNavHost after the deep-linked route has been shown. */
    fun clearWebStartRoute() = _uiState.update { it.copy(webStartRoute = null) }

    // ---- Activity screen ----
    fun setActivityRange(range: String) = _uiState.update { it.copy(activityRange = range) }

    // Dedicated Weekly / Monthly report screens — opened from the Activity
    // page's Reports section; closed via the in-app back arrow or system Back.
    fun openActivityReport(period: ActivityPeriod) =
        _uiState.update { it.copy(activityReport = period) }

    fun closeActivityReport() = _uiState.update { it.copy(activityReport = null) }

    // Per-day detail opened by tapping a monthly date-range bar. Back returns
    // to the Activity page (system Back is wired in ScNavHost).
    fun openActivityRangeDetail(range: ActivityRange) =
        _uiState.update { it.copy(activityRangeDetail = range) }

    fun closeActivityRangeDetail() = _uiState.update { it.copy(activityRangeDetail = null) }

    // ---- Web section (rules + analytics; backend-ready via WebRepository) ----

    // The website blocking engine. Today this is the honest placeholder that
    // performs NO network filtering (see web/BlockingEngine.kt) — the UI only
    // manages the local rule list and never claims to block. Swap this field
    // for a real VPN/DNS engine implementation when it is available; the UI
    // and WebRule data model do not change.
    private val blockingEngine: WebsiteBlockingEngine = PlaceholderBlockingEngine()

    /**
     * Propagates a rule change to the blocking engine. No-op with the
     * placeholder engine (no fake blocking) — a real engine's apply/remove
     * is async, so this is fire-and-forget; failures are non-fatal because
     * the local rule list remains the source of truth for the UI.
     */
    private fun pushRuleToEngine(domain: String, status: WebRuleStatus) {
        viewModelScope.launch {
            val result = when (status) {
                WebRuleStatus.BLOCKED -> blockingEngine.applyBlock(domain)
                WebRuleStatus.ALLOWED -> blockingEngine.removeBlock(domain)
            }
            result.onFailure { /* log / future sync analytics here */ }
        }
    }

    fun setWebPeriod(period: WebAnalyticsPeriod) =
        _uiState.update { it.copy(webPeriod = period) }

    /**
     * Adds a website rule (display name derived from the domain). Returns
     * false when the domain is invalid or a rule already exists — the UI
     * validates first, this is a defensive guard.
     */
    fun addWebRule(domain: String, status: WebRuleStatus): Boolean {
        val d = domain.trim()
        if (d.isBlank() || !d.contains(".")) return false
        if (uiState.value.webRules.any { it.domain.equals(d, ignoreCase = true) }) return false
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            state.copy(
                webRules = state.webRules + WebRule(
                    id = d.lowercase(),
                    domain = d,
                    displayName = WebRepository.displayNameFor(d),
                    status = status,
                    createdAt = now,
                    updatedAt = now,
                    // Website identity: store the favicon URL + cache key only —
                    // pixels live in the local favicon cache, never in the model.
                    faviconUrl = FaviconRepository.faviconUrl(d),
                    localIconPath = FaviconRepository.cacheKey(d),
                ),
            )
        }
        if (status == WebRuleStatus.BLOCKED) pushRuleToEngine(d, status)
        showToast { if (status == WebRuleStatus.BLOCKED) it.webToastBlocked else it.webToastAllowed }
        return true
    }

    /**
     * Primary blocking action (main Web screen): blocks [domain], creating a
     * BLOCKED rule when it is new, or flipping an existing ALLOWED rule to
     * BLOCKED. Returns false only for invalid input or when it is already
     * blocked (the UI shows the reason inline).
     */
    fun blockWebsite(domain: String): Boolean {
        val d = domain.trim()
        if (d.isBlank() || !d.contains(".")) return false
        val existing = uiState.value.webRules.firstOrNull { it.domain.equals(d, ignoreCase = true) }
        if (existing != null) {
            if (existing.status == WebRuleStatus.BLOCKED) return false
            setWebRuleStatus(existing.domain, WebRuleStatus.BLOCKED)
            return true
        }
        return addWebRule(d, WebRuleStatus.BLOCKED)
    }

    /**
     * Moves a website between the blocked and allowed lists. The website
     * identity (favicon refs) is untouched — the same cached icon is shown
     * in both states, never re-downloaded.
     */
    fun setWebRuleStatus(domain: String, status: WebRuleStatus) {
        _uiState.update { state ->
            state.copy(
                webRules = state.webRules.map {
                    if (it.domain.equals(domain, ignoreCase = true)) {
                        it.copy(status = status, updatedAt = System.currentTimeMillis())
                    } else it
                },
            )
        }
        pushRuleToEngine(domain, status)
        showToast { if (status == WebRuleStatus.BLOCKED) it.webToastBlocked else it.webToastUnblocked }
    }

    // ---- Settings sub-screens (dedicated screens; Navigation Compose back
    //      stack hosted in SettingsNavHost; backend-ready) ----
    fun openSettingsScreen(destination: SettingsDestination) =
        _uiState.update { it.copy(settingsDestination = destination) }

    fun closeSettingsScreen() = _uiState.update { it.copy(settingsDestination = null) }

    // ---- Focus Passcode flow overlay (shared by Home + General → Study Mode) ----
    // One overlay, one passcode UI, one recovery system. Rendered at the app
    // root on top of everything; closing it returns to whatever screen opened
    // it (Home or Study Mode), both of which observe the SAME Study Mode state.
    fun openFocusPasscodeFlow(entry: FocusPasscodeEntry) =
        _uiState.update { it.copy(focusPasscodeFlow = entry) }

    fun closeFocusPasscodeFlow() = _uiState.update { it.copy(focusPasscodeFlow = null) }

    // ---- Monitoring settings (local today; GET/UPDATE Monitoring Settings
    //      backend APIs + SettingsRepository seam later) ----
    // App Blocking / Daily Screen Time Limit settings no longer live on the
    // Monitoring screen (moved off it as configuration-only UI). Their model
    // fields stay reserved for the future Settings/Restriction section.
    fun setMonitoringEnabled(on: Boolean) {
        // Restricted Mode stays locked while a Study Mode session is active
        // — monitoring cannot be switched off mid-session.
        if (_uiState.value.studyModeActive && !on) return
        _uiState.update { it.copy(monitoring = it.monitoring.copy(enabled = on)) }
        syncMonitoringService()
    }

    /**
     * Keeps the foreground MonitoringService in sync with the real
     * configuration: it runs only while monitoring is enabled AND every
     * required monitoring permission is granted. The enabled flag is
     * persisted so a START_STICKY restart (or the task-removed alarm
     * restart) can self-heal without the UI being open; the service stops
     * itself if the prerequisites disappear.
     */
    private fun syncMonitoringService() {
        val state = _uiState.value
        MonitoringService.saveMonitoringEnabled(getApplication(), state.monitoring.enabled)
        val shouldRun = state.monitoring.enabled && state.missingRequiredMonitoringPermissions.isEmpty()
        if (shouldRun) MonitoringService.start(getApplication())
        else MonitoringService.stop(getApplication())
    }

    fun setStrictMode(on: Boolean) {
        // The user must NOT be able to manually disable Restricted Mode
        // (Strict Mode) while Study Mode is active — the toggle is ignored
        // until the countdown finishes, so the switch visually stays on.
        if (_uiState.value.studyModeActive && !on) return
        _uiState.update { it.copy(monitoring = it.monitoring.copy(strictModeEnabled = on)) }
    }

    fun togglePlatform(id: String) = _uiState.update { state ->
        state.copy(
            monitoring = state.monitoring.copy(
                platforms = state.monitoring.platforms.map {
                    if (it.id == id) it.copy(enabled = !it.enabled) else it
                },
            ),
        )
    }

    // ---- Study Mode (General section; backend seam: StudyRepository) ----
    // State is held in AppUiState (studySettings / activeStudySession /
    // studySummary) and is fully separate from monitoring, shorts, activity
    // and history data. The session is timestamp-based: remaining time is
    // always derived from wall-clock start/end, never from a ticking UI.
    // The ACTIVE session is additionally persisted by StudyPreferenceStore so
    // a process restart / app reopen never loses or resets a running session.
    private var studyTickerJob: Job? = null
    private var strictModeBeforeStudy: Boolean? = null
    private var monitoringBeforeStudy: Boolean? = null

    fun setStudyDuration(minutes: Int) =
        _uiState.update { it.copy(studySettings = it.studySettings.copy(studyDurationMinutes = minutes)) }

    /** Replaces the whole Break Reminder configuration (saved from its page). */
    fun setBreakReminderConfig(config: BreakReminderConfig) =
        _uiState.update { it.copy(studySettings = it.studySettings.copy(breakReminder = config)) }

    fun setStudyBreakDuration(minutes: Int) =
        _uiState.update { it.copy(studySettings = it.studySettings.copy(breakDurationMinutes = minutes)) }

    // ---- Sound Mode → REAL Android device ringer mode ----
    // Changing the ringer requires Notification Policy Access (system
    // authorization). A pending selection is remembered so that returning from
    // the system settings page (after granting access) applies the requested
    // mode automatically; the ACTUAL Android state is the source of truth.

    // The pending selection is in-memory ONLY: if the OS kills the process
    // while the user is in system settings, it is lost and the UI re-syncs
    // to the real Android state on next launch (never restores a stale
    // ShortsCap value over the user's newer system setting).
    private var pendingSoundMode: StudySoundMode? = null
    private var pendingSoundModeAtMillis: Long = 0L
    private val pendingSoundModeTtlMillis = 5 * 60_000L

    /**
     * Applies the selected Sound Mode to the ACTUAL Android ringer mode.
     * Returns the result so the UI can react: APPLIED (verified against
     * getRingerMode), POLICY_ACCESS_REQUIRED (the UI explains why + offers
     * "Open Settings"), or FAILED (nothing changed; a clear error is toasted
     * — success is never claimed before Android confirms the new state).
     */
    fun setStudySoundMode(mode: StudySoundMode): DeviceSoundModeResult {
        val app = getApplication<Application>()
        if (!app.hasSoundModeAccess()) {
            // Remember the request; it is applied automatically once the user
            // returns from system settings with access granted (see
            // syncSoundModeWithSystem). No state changes until then.
            pendingSoundMode = mode
            pendingSoundModeAtMillis = System.currentTimeMillis()
            return DeviceSoundModeResult.POLICY_ACCESS_REQUIRED
        }
        pendingSoundMode = null
        return when (app.applySoundMode(mode)) {
            DeviceSoundModeResult.APPLIED -> {
                _uiState.update { it.copy(studySettings = it.studySettings.copy(soundMode = mode)) }
                DeviceSoundModeResult.APPLIED
            }
            DeviceSoundModeResult.FAILED -> {
                showToast { it.soundModeChangeFailedToast }
                DeviceSoundModeResult.FAILED
            }
            DeviceSoundModeResult.POLICY_ACCESS_REQUIRED -> DeviceSoundModeResult.POLICY_ACCESS_REQUIRED
        }
    }

    /** Opens the system settings page where Notification Policy Access is granted. */
    fun openSoundModeAccessSettings() {
        val app = getApplication<Application>()
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (app.packageManager.resolveActivity(intent, 0) == null) {
            showToast { it.permissionSettingsUnavailableToast }
            return
        }
        if (!runCatching { app.startActivity(intent) }.isSuccess) {
            showToast { it.permissionSettingsUnavailableToast }
        }
    }

    /**
     * Re-synchronizes Sound Mode with the REAL Android ringer state. Runs on
     * every app launch + resume: (1) a pending selection is applied once the
     * user has granted policy access (within a short freshness window — a
     * stale pending request is never applied over newer system state), and
     * (2) the UI mirrors the actual ringer mode, so external changes (e.g.
     * Android Quick Settings) are reflected automatically.
     */
    private fun syncSoundModeWithSystem() {
        val app = getApplication<Application>()
        val pending = pendingSoundMode
        val fresh = pending != null && System.currentTimeMillis() - pendingSoundModeAtMillis <= pendingSoundModeTtlMillis
        if (pending != null && fresh && app.hasSoundModeAccess()) {
            pendingSoundMode = null
            if (app.applySoundMode(pending) == DeviceSoundModeResult.FAILED) {
                showToast { it.soundModeChangeFailedToast }
            }
        } else if (pending != null && !fresh) {
            pendingSoundMode = null // stale — never override the user's newer state
        }
        // Source of truth: the actual Android ringer mode.
        _uiState.update {
            it.copy(studySettings = it.studySettings.copy(soundMode = app.currentSoundMode()))
        }
    }

    // ---- Study Schedule — MULTIPLE schedules, each with its own subject,
    //      days, start time, duration, reminder and enabled state. Editing
    //      one schedule never touches another (each keeps a stable id).
    //      Configuration-only today; the future scheduler reads
    //      reminderTimeMinutes (start − reminder) to fire a notification and
    //      startMinutes + durationMinutes to auto-start the EXISTING Study
    //      Mode session — no second Study Mode system. ----

    /** Creates a new schedule (enabled by default). */
    fun addStudySchedule(
        subject: String,
        days: Set<StudyDay>,
        startMinutes: Int,
        durationMinutes: Int,
        reminderMinutesBefore: Int?,
    ) {
        val entry = StudyScheduleEntry(
            id = UUID.randomUUID().toString(),
            subject = subject.trim(),
            days = days,
            startMinutes = startMinutes,
            durationMinutes = durationMinutes,
            reminderMinutesBefore = reminderMinutesBefore,
            enabled = true,
        )
        _uiState.update {
            it.copy(studySettings = it.studySettings.copy(schedules = it.studySettings.schedules + entry))
        }
        showToast { it.studyScheduleSavedToast }
    }

    /** Updates ONLY the schedule with [id]; all other schedules stay untouched. */
    fun updateStudySchedule(
        id: String,
        subject: String,
        days: Set<StudyDay>,
        startMinutes: Int,
        durationMinutes: Int,
        reminderMinutesBefore: Int?,
    ) {
        _uiState.update { state ->
            state.copy(
                studySettings = state.studySettings.copy(
                    schedules = state.studySettings.schedules.map {
                        if (it.id == id) {
                            it.copy(
                                subject = subject.trim(),
                                days = days,
                                startMinutes = startMinutes,
                                durationMinutes = durationMinutes,
                                reminderMinutesBefore = reminderMinutesBefore,
                            )
                        } else it
                    },
                ),
            )
        }
        showToast { it.studyScheduleSavedToast }
    }

    /** Removes ONLY the schedule with [id]. */
    fun deleteStudySchedule(id: String) {
        _uiState.update {
            it.copy(studySettings = it.studySettings.copy(schedules = it.studySettings.schedules.filterNot { s -> s.id == id }))
        }
        showToast { it.studyScheduleDeletedToast }
    }

    /** Flips the enabled state of ONLY the schedule with [id]. */
    fun toggleStudyScheduleEnabled(id: String) {
        _uiState.update { state ->
            state.copy(
                studySettings = state.studySettings.copy(
                    schedules = state.studySettings.schedules.map {
                        if (it.id == id) it.copy(enabled = !it.enabled) else it
                    },
                ),
            )
        }
    }

    /** Removes/restores one app in the Study Mode allowed-app list. */
    fun toggleStudyAllowedApp(id: String) = _uiState.update { state ->
        val current = state.studySettings.allowedApps
        state.copy(
            studySettings = state.studySettings.copy(
                allowedApps = if (id in current) current - id else current + id,
            ),
        )
    }

    /** Adds one installed app to the Study Mode allowed list (Add App picker). */
    fun addStudyAllowedApp(id: String) = _uiState.update { state ->
        val current = state.studySettings.allowedApps
        if (id in current) state
        else state.copy(studySettings = state.studySettings.copy(allowedApps = current + id))
    }

    /** Removes one app from the Study Mode allowed list (Manage Apps). */
    fun removeStudyAllowedApp(id: String) = _uiState.update { state ->
        val current = state.studySettings.allowedApps
        if (id !in current) state
        else state.copy(studySettings = state.studySettings.copy(allowedApps = current - id))
    }

    /** Removes/restores one domain in the Study Mode allowed-websites list. */
    fun toggleStudyAllowedWebsite(domain: String) = _uiState.update { state ->
        val current = state.studySettings.allowedWebsites
        state.copy(
            studySettings = state.studySettings.copy(
                allowedWebsites = if (domain in current) current - domain else current + domain,
            ),
        )
    }

    /**
     * Adds an allowed website domain. Returns false for invalid input or a
     * duplicate — the UI surfaces the reason inline.
     */
    fun addStudyAllowedWebsite(domain: String): Boolean {
        val d = domain.trim().lowercase()
        if (d.isBlank() || !d.contains(".")) return false
        if (d in _uiState.value.studySettings.allowedWebsites) return false
        _uiState.update {
            it.copy(studySettings = it.studySettings.copy(allowedWebsites = it.studySettings.allowedWebsites + d))
        }
        return true
    }

    /**
     * Starts a Study Mode session. Records wall-clock start/end timestamps,
     * snapshots the session configuration, and activates Restricted Mode by
     * forcing Strict Mode ON (the previous value is restored when the
     * session finishes). There is intentionally NO stop/cancel — the session
     * only ends when the countdown reaches 00:00.
     */
    fun startStudySession() {
        if (_uiState.value.studyModeActive) return
        val settings = _uiState.value.studySettings
        val now = System.currentTimeMillis()
        // Snapshot the pre-session restriction states so the normal state is
        // restored exactly when the countdown finishes.
        val previousStrict = _uiState.value.monitoring.strictModeEnabled
        val previousMonitoring = _uiState.value.monitoring.enabled
        strictModeBeforeStudy = previousStrict
        monitoringBeforeStudy = previousMonitoring
        val session = StudySession(
            startTimeMillis = now,
            endTimeMillis = now + settings.studyDurationMinutes * 60_000L,
            durationMinutes = settings.studyDurationMinutes,
            breakReminder = settings.breakReminder,
            breakDurationMinutes = settings.breakDurationMinutes,
            soundMode = settings.soundMode,
            allowedApps = settings.allowedApps,
            allowedWebsites = settings.allowedWebsites,
            currentTimeMillis = now,
        )
        _uiState.update { state ->
            state.copy(
                activeStudySession = session,
                // Restricted Mode: Strict Mode AND Device Monitoring are forced
                // ON for the session — the user cannot switch either off while
                // Study Mode is active (guarded in the setters below).
                monitoring = state.monitoring.copy(strictModeEnabled = true, enabled = true),
            )
        }
        // Study Mode forces Device Monitoring on — make sure the foreground
        // monitoring service is running for the whole session.
        syncMonitoringService()
        // Persist across process death / app reopen (timestamp-based restore).
        studyStore.saveActiveSession(session, previousStrict, previousMonitoring)
        startStudyTicker()
        showToast { it.studySessionStartedToast }
    }

    /**
     * One-second ticker while a session is active: refreshes the session's
     * currentTime from the wall clock and auto-ends the session at 00:00
     * (restoring the normal Strict Mode state and updating the summary).
     */
    private fun startStudyTicker() {
        studyTickerJob?.cancel()
        studyTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val session = _uiState.value.activeStudySession ?: break
                if (now >= session.endTimeMillis) {
                    finishStudySession()
                    break
                }
                _uiState.update { it.copy(activeStudySession = session.copy(currentTimeMillis = now)) }
            }
        }
    }

    /**
     * Ends the session. Reachable naturally when the countdown reaches 00:00
     * ([manualEnd] = false, no passcode involved) or manually when the user
     * enters the correct Exit Passcode ([manualEnd] = true). Both paths
     * restore the exact pre-session restriction states and update the summary
     * — the user's permanent restriction configuration is never changed.
     */
    private fun finishStudySession(manualEnd: Boolean = false) {
        studyTickerJob?.cancel()
        studyTickerJob = null
        // Restore the exact restriction states the user had before the session.
        val restoreStrict = strictModeBeforeStudy ?: false
        val restoreMonitoring = monitoringBeforeStudy ?: true
        strictModeBeforeStudy = null
        monitoringBeforeStudy = null
        _uiState.update { state ->
            val session = state.activeStudySession
            state.copy(
                activeStudySession = null,
                // Restore the normal Restricted Mode state the user had before.
                monitoring = state.monitoring.copy(
                    strictModeEnabled = restoreStrict,
                    enabled = restoreMonitoring,
                ),
                studySummary = state.studySummary.copy(
                    sessionsToday = state.studySummary.sessionsToday + 1,
                    minutesToday = state.studySummary.minutesToday + (session?.durationMinutes ?: 0),
                    lastSessionDurationMinutes = session?.durationMinutes,
                ),
            )
        }
        // The session restored the user's normal monitoring state — resync the
        // foreground service (starts/stops to match the restored config).
        syncMonitoringService()
        // The session is over — nothing to persist anymore.
        studyStore.clearActiveSession()
        showToast { if (manualEnd) it.focusPasscodeEndedToast else it.studySessionCompleteToast }
    }

    // ---- Exit Passcode (Study Mode protection; local mock today,
    //      backend seams: FocusPasscodeRepository / FocusPasscodePreferenceStore) ----

    /**
     * Shared save path for setup (create) and post-recovery (update) — the
     * passcode is hashed+salted before storage, so the old one becomes
     * invalid immediately. [toast] picks the localized success message.
     */
    private fun saveFocusPasscode(passcode: String, toast: (AppStrings) -> String): Boolean {
        if (passcode.length < 8) return false
        focusPasscodeStore.savePasscode(passcode)
        // Set-at timestamp comes straight from the device clock (System.currentTimeMillis)
        // so the Study Mode card + status screen always show the real device date/time.
        _uiState.update {
            it.copy(focusPasscodeSet = true, focusPasscodeSetAtMillis = System.currentTimeMillis())
        }
        showToast(toast)
        return true
    }

    /** Creates the Exit Passcode on first-time setup (min 8 chars). */
    fun createFocusPasscode(passcode: String): Boolean =
        saveFocusPasscode(passcode) { it.focusPasscodeCreatedToast }

    /** Replaces the passcode after successful OTP recovery (old becomes invalid). */
    fun updateFocusPasscode(passcode: String): Boolean =
        saveFocusPasscode(passcode) { it.focusPasscodeUpdatedToast }

    /**
     * Deletes ONLY the Exit Passcode configuration (the salted hash + the
     * set-at timestamp). The Study Mode section returns to its normal
     * "not configured" state and the user can create a new passcode again.
     * No account, Study Mode settings, history, monitoring data or any other
     * app data is touched. While a session is active, ending it early still
     * requires creating a new passcode first (protection is never weakened).
     */
    fun deleteFocusPasscode() {
        focusPasscodeStore.clearPasscode()
        _uiState.update { it.copy(focusPasscodeSet = false, focusPasscodeSetAtMillis = 0L) }
        showToast { it.focusPasscodeDeletedToast }
    }

    /** Checks an entry against the stored hash (used by the verify screen). */
    fun verifyFocusPasscode(passcode: String): Boolean = focusPasscodeStore.verifyPasscode(passcode)

    /**
     * Ends the active Study Mode session ONLY when [passcode] matches the
     * Exit Passcode. On success the countdown stops, Study Mode
     * restrictions are removed and the normal state returns — the user's
     * permanent restriction configuration is untouched. Returns false (and
     * keeps Study Mode fully active) on an incorrect passcode.
     */
    fun endStudySessionWithPasscode(passcode: String): Boolean {
        if (!_uiState.value.studyModeActive) return false
        if (!focusPasscodeStore.verifyPasscode(passcode)) return false
        finishStudySession(manualEnd = true)
        return true
    }

    // ---- Recovery OTP (LOCAL MOCK — no backend yet). The demo code +
    //      masked contact are surfaced in the UI so the flow is testable;
    //      the future backend generates and sends the OTP itself. ----

    /** Requests a recovery code for [method] + [contact]. */
    fun requestFocusOtp(method: FocusRecoveryMethod, contact: String) {
        val code = focusPasscodeRepo.requestOtp(method, contact)
        _uiState.update {
            it.copy(focusOtpDemoCode = code, focusOtpContactMasked = maskContact(method, contact))
        }
    }

    /** Re-sends the pending code (fresh expiry) for the resend countdown. */
    fun resendFocusOtp() {
        val code = focusPasscodeRepo.resendOtp() ?: return
        _uiState.update { it.copy(focusOtpDemoCode = code) }
    }

    /** Verifies the entered code — true only for the pending, unexpired code. */
    fun verifyFocusOtp(code: String): Boolean = focusPasscodeRepo.verifyOtp(code)

    /**
     * Called on every app resume (via refreshPermissions): if a session
     * expired while the app was backgrounded, it is ended immediately and
     * the normal restriction state is restored — no visual-only countdown
     * drift, because everything is timestamp-based.
     */
    fun checkStudySessionExpiry() {
        val session = _uiState.value.activeStudySession ?: return
        if (System.currentTimeMillis() >= session.endTimeMillis) finishStudySession()
        else startStudyTicker() // resume ticking after backgrounding
    }

    // ---- Notifications (local persistence today; future: NotificationRepository
    //      cloud sync / analytics seams) ----
    fun toggleNotificationSetting(id: NotificationSettingId, enabled: Boolean) {
        val updated = uiState.value.notificationSettings.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        NotificationRepository.saveSettings(getApplication(), updated)
        _uiState.update { it.copy(notificationSettings = updated) }
        // Future backend: NotificationRepository.syncSettingsToCloud(updated);
        // Future analytics: NotificationRepository.trackNotificationAnalytics(id, enabled).
    }

    // ---- Sound & Effects (central app-sounds control) ----
    fun setAppSoundsEnabled(enabled: Boolean) {
        val updated = uiState.value.soundEffects.withMaster(enabled)
        SoundEffectsRepository.saveSettings(getApplication(), updated)
        _uiState.update { it.copy(soundEffects = updated) }
    }

    fun setSoundForCategory(category: SoundEffectCategory, sound: AppSound) {
        val updated = uiState.value.soundEffects.withSound(category, sound)
        SoundEffectsRepository.saveSettings(getApplication(), updated)
        _uiState.update { it.copy(soundEffects = updated) }
    }

    // ---- Permissions (live OS checks; backend-ready via PermissionRepository)
    //      Called on every Permissions-screen resume AND at the app root on
    //      every foreground resume, so statuses stay fresh after the user
    //      returns from Android Settings — no manual refresh. ----

    // False until the seeded placeholder statuses have been replaced by real
    // OS checks. Guards the success toast: on cold start the seeded state may
    // look "paused", so we must not fire a bogus "Monitoring Started".
    private var permissionsHaveRealStatus = false

    fun refreshPermissions() {
        val wasPaused = _uiState.value.monitoringPaused
        _uiState.update { state ->
            state.copy(permissions = PermissionRepository.checkAll(getApplication(), state.permissions))
        }
        // Foreground service follows the real configuration: it starts once
        // monitoring is enabled and the required permissions are verified as
        // granted, and stops when monitoring is paused or turned off.
        syncMonitoringService()
        // Study Mode runs on every resume too: an expired session ends and
        // restores the normal restriction state the moment the app returns.
        checkStudySessionExpiry()
        // Sound Mode runs on every resume too: a pending selection is applied
        // once policy access is granted, and the UI mirrors the ACTUAL Android
        // ringer mode (external changes via Quick Settings are picked up).
        syncSoundModeWithSystem()
        // Success feedback: monitoring automatically resumes the moment the
        // required permissions are re-verified as granted (paused → active),
        // with no extra button press — e.g. after returning from Android
        // Settings. Derived state guarantees this only fires on a real change.
        if (permissionsHaveRealStatus && wasPaused && !_uiState.value.monitoringPaused) {
            showToast { it.monitoringStartedToast }
        }
        permissionsHaveRealStatus = true
    }

    // Restores every resettable application setting to its default through the
    // centralized SettingsManager (Theme → System Default, Text Size → Medium,
    // Language → English, Monitoring + Notification preferences → defaults).
    // Account, profile, session, tokens and history are never touched.
    // Future: SettingsManager.resetCloudSettings() clears backend/cloud too.
    fun resetAllSettings() {
        SettingsManager.restoreDefaults(getApplication())
        // Study Mode state is deliberately NOT reset (separate feature), but
        // while a session is ACTIVE the user must not be able to disable
        // Restricted Mode through any path — so the defaults reset must not
        // wipe the forced Strict Mode / Monitoring ON. The pre-session states
        // are still restored exactly at 00:00 (finishStudySession).
        val studyActive = _uiState.value.studyModeActive
        _uiState.update {
            it.copy(
                monitoring = if (studyActive) {
                    SettingsManager.defaultMonitoring().copy(strictModeEnabled = true, enabled = true)
                } else SettingsManager.defaultMonitoring(),
                notificationSettings = SettingsManager.defaultNotificationSettings(),
                soundEffects = SettingsManager.defaultSoundEffects(),
                themeMode = SettingsManager.defaultThemeMode(),
                textSizeMode = SettingsManager.defaultTextSizeMode(),
                chartStyle = SettingsManager.defaultChartStyle(),
                iconStyle = SettingsManager.defaultIconStyle(),
                fontMode = SettingsManager.defaultFontMode(),
                appLanguage = SettingsManager.defaultLanguage(),
            )
        }
        showToast { it.toastSettingsReset }
    }
}
