package com.shortscap.app.activity

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager

/**
 * PackageClassifier — the single source of truth for deciding whether a
 * package recorded in `screen_activity_usage` is a reportable user-facing
 * application or an internal/system component that must not appear in the
 * Activity / Home user-usage reports (Phase 1.2).
 *
 * CONSERVATIVE BY DESIGN:
 *  - Anything we do not recognize is [Category.UNKNOWN], which [isReportable]
 *    treats as reportable — an unknown package is never hidden.
 *  - Only KNOWN system components are excluded. There is NO broad
 *    `com.android.*` ban and NO blanket `FLAG_SYSTEM` rule, because many
 *    preinstalled packages (Chrome, YouTube, …) are genuine user apps.
 *  - Explicit known packages are checked first (deterministic and identical
 *    on every device — the layer ActivityRepository uses), then a
 *    context-aware pass additionally recognizes the device's real launcher
 *    and its enabled input methods through Android metadata.
 *  - Bare package strings without a dot ("android", "app", "launcher3", …)
 *    are not valid installed application packages and are classified as
 *    SYSTEM_SERVICE — a package-identity rule that can never hide a real app.
 *
 * Every reporting surface (ActivityRepository, AppViewModel/Home) MUST use
 * this single classifier so the same package gets the same classification
 * everywhere — never a second hardcoded list.
 */
object PackageClassifier {

    /** Reporting category of a recorded package. */
    enum class Category {
        /** A normal user-facing application (YouTube, Chrome, WhatsApp, …). */
        USER_APP,

        /** Android system UI surfaces (keyguard, notification shade, quick settings). */
        SYSTEM_UI,

        /** Android system services/dialogs (e.g. the bare `android` package). */
        SYSTEM_SERVICE,

        /** Input methods / keyboards (Gboard, SwiftKey, …). */
        INPUT_METHOD,

        /** The device's launcher/home application. */
        LAUNCHER,

        /** ShortsCap's own package — the monitoring/reporting app itself. */
        SHORTSCAP_INTERNAL,

        /** Not recognized — treated as reportable (conservative default). */
        UNKNOWN,
    }

    /** Only these categories may appear in user-facing Activity/Home reporting. */
    fun isReportable(category: Category): Boolean =
        category == Category.USER_APP || category == Category.UNKNOWN

    // ------------------------------------------------------------------
    // Deterministic static classification (no Android runtime — unit-testable
    // and identical on every device; this is the layer the pure reporting
    // aggregation in ActivityRepository uses).
    // ------------------------------------------------------------------

    /**
     * Explicitly known system/internal packages.
     *
     * - `com.android.systemui` → SYSTEM_UI — keyguard / shade / quick-settings
     *   windows (the observed 13h "systemui" session was this package staying
     *   "foreground" across locked/idle time; the screen-off boundary fix in
     *   ScreenActivityEngine stops that, and this rule keeps any remaining
     *   system-UI rows out of user reports).
     * - `android` → SYSTEM_SERVICE — system process/dialog windows carry the
     *   bare `android` package.
     * - IME packages → INPUT_METHOD — keyboards are windows, not apps
     *   (Gboard = `com.google.android.inputmethod.latin` was observed as
     *   "latin"). This is a small static baseline; the context-aware pass
     *   also detects the device's enabled IMEs dynamically.
     * - `com.google.android.googlequicksearchbox` → SYSTEM_SERVICE — on the
     *   observed device this PREINSTALLED system package is recorded when the
     *   launcher's Google search widget / Discover surface is the active
     *   window, which is not meaningful standalone app usage. Phase 1.2
     *   product decision (documented): exclude it. This is NOT a broad
     *   `com.google.android.*` rule — other Google apps stay reportable.
     * - `com.shortscap.app` → SHORTSCAP_INTERNAL — ShortsCap is the
     *   monitoring/reporting app itself and must not inflate the user's
     *   "apps I used" metric. Recording continues internally; only
     *   reporting excludes it.
     * - Launchers → LAUNCHER — the home app is a window the user passes
     *   through, not an app they use. `com.android.launcher3` is the launcher
     *   confirmed on the observed device (its Most Used Apps entry was
     *   "launcher3"); the list covers the common launchers as a static
     *   baseline, and the context-aware classify() ALSO resolves the
     *   device's actual launcher dynamically via ACTION_MAIN + CATEGORY_HOME.
     */
    private val knownCategories: Map<String, Category> = mapOf(
        "com.android.systemui" to Category.SYSTEM_UI,
        "android" to Category.SYSTEM_SERVICE,
        "com.google.android.googlequicksearchbox" to Category.SYSTEM_SERVICE,
        // Input methods (keyboards) — Gboard plus a small set of common IME
        // packages; the context-aware classify() also detects the device's
        // enabled IMEs dynamically, so this list is only the static baseline.
        "com.google.android.inputmethod.latin" to Category.INPUT_METHOD,
        "com.touchtype.swiftkey" to Category.INPUT_METHOD,
        "com.sohu.inputmethod.sogou" to Category.INPUT_METHOD,
        "com.baidu.input" to Category.INPUT_METHOD,
        "com.menny.android.anysoftkeyboard" to Category.INPUT_METHOD,
        "org.pocketworkstation.pckeyboard" to Category.INPUT_METHOD,
        "com.shortscap.app" to Category.SHORTSCAP_INTERNAL,
        // Launchers / home applications (static baseline for common devices;
        // the dynamic pass identifies the current device's launcher too).
        "com.android.launcher3" to Category.LAUNCHER,
        "com.android.launcher" to Category.LAUNCHER,
        "com.google.android.apps.nexuslauncher" to Category.LAUNCHER,
        "com.sec.android.app.launcher" to Category.LAUNCHER,
        "com.miui.home" to Category.LAUNCHER,
        "com.vivo.launcher" to Category.LAUNCHER,
        "com.bbk.launcher2" to Category.LAUNCHER,
        "com.oppo.launcher" to Category.LAUNCHER,
        "com.coloros.launcher" to Category.LAUNCHER,
        "com.oneplus.launcher" to Category.LAUNCHER,
        "com.huawei.android.launcher" to Category.LAUNCHER,
        "com.motorola.launcher" to Category.LAUNCHER,
        "com.nothing.launcher" to Category.LAUNCHER,
        "org.lineageos.trebuchet" to Category.LAUNCHER,
    )

    /**
     * Static classification from the package name alone (pure).
     *
     * Phase 1.3: a package string WITHOUT a dot is not a valid installed
     * application package (every real app has a reverse-domain name).
     * Accessibility events can carry such bare strings for system processes,
     * dialogs or OEM artifacts (e.g. "android", "app", "launcher3").
     * Classifying them as SYSTEM_SERVICE can never hide a legitimate user
     * application, so this is a safe, package-identity-based rule — NOT a
     * display-label guess.
     */
    fun classify(packageName: String): Category {
        knownCategories[packageName]?.let { return it }
        if ('.' !in packageName) return Category.SYSTEM_SERVICE
        return Category.UNKNOWN
    }

    // ------------------------------------------------------------------
    // Context-aware classification — adds the device's real launcher and
    // enabled input methods via Android metadata (cached; never crashes).
    // Used by context-capable callers such as AppViewModel (Home surfaces).
    // ------------------------------------------------------------------

    @Volatile
    private var cachedLauncher: String? = null

    @Volatile
    private var cachedImePackages: Set<String>? = null

    /**
     * Classification with Android metadata: static rules first, then the
     * device's launcher (ACTION_MAIN + CATEGORY_HOME) and its enabled input
     * methods (InputMethodManager). Safe by construction: resolution is
     * wrapped in runCatching, a missing/uninstalled package can never crash
     * reporting, and anything still unknown stays reportable.
     */
    fun classify(packageName: String, context: Context): Category {
        val static = classify(packageName)
        if (static != Category.UNKNOWN) return static
        if (packageName == deviceLauncher(context)) return Category.LAUNCHER
        if (packageName in enabledImePackages(context)) return Category.INPUT_METHOD
        return Category.UNKNOWN
    }

    private fun deviceLauncher(context: Context): String? {
        cachedLauncher?.let { return it }
        val launcher = runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        }.getOrNull()
        cachedLauncher = launcher
        return launcher
    }

    private fun enabledImePackages(context: Context): Set<String> {
        cachedImePackages?.let { return it }
        val imes = runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
        }.getOrNull() ?: emptySet()
        cachedImePackages = imes
        return imes
    }

    /**
     * Phase 1.4: resolves the DEVICE-SPECIFIC non-reportable packages (the
     * device's real launcher + every enabled input method) ONCE at app start.
     * The pure reporting path in ActivityRepository installs this set so the
     * Activity aggregation excludes exactly the same device packages as the
     * context-aware Home path — no display labels, no per-screen lists.
     * Crash-safe: resolution failures simply yield an empty set.
     */
    fun resolveDeviceNonReportablePackages(context: Context): Set<String> {
        val launcher = deviceLauncher(context)
        val imes = enabledImePackages(context)
        return buildSet {
            launcher?.let { add(it) }
            addAll(imes)
        }
    }
}