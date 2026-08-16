# ShortsCap — Android Digital Wellbeing App

**ShortsCap** is a native **Android** application built with **Kotlin + Jetpack Compose + Material Design 3 (Material You-ready)**. It is a digital-wellbeing companion that helps users understand and control their short-form video (Shorts) and general screen usage. The app is a faithful 1:1 native port of an original React Native web app ("ShortsCap"), mirroring its visual identity, screens, interactions, and design tokens exactly.

This document explains everything from scratch: what the app does, the tech stack, every dependency, how to build and run it, the project structure, and a complete change log of what was modified, added, and when.

---

## Table of Contents

1. [Features](#features)
2. [Tech Stack](#tech-stack)
3. [Prerequisites](#prerequisites)
4. [Getting Started (build & run)](#getting-started--build--run-)
5. [Project Structure](#project-structure)
6. [Architecture](#architecture)
7. [Theme System (Dark / Light / System Default)](#theme-system-dark--light--system-default-)
8. [Home Circular Analytics & Future Backend](#home-circular-analytics--future-backend)
9. [Dependencies](#dependencies)
10. [Change Log — what was modified and when](#change-log--what-was-modified-and-when)
11. [Troubleshooting](#troubleshooting)
12. [Backend Implementation](#backend-implementation)

---

## Features

- **5 main screens** (bottom navigation): Home, Activity, **Rank** (center), Web, Settings.
- **Rank**: dedicated leaderboard tab — Your Rank / Your Score status card, This Week | This Month filter, Top 3 podium, full leaderboard (current user highlighted), and a Your Progress metrics section. Trophy icon + calm count-up / entrance animations; **mock data only** (backend, score engine and database come later).
- **Home dashboard**: greeting, a large animated **circular analytics widget** (swipeable metric pages — "Today's Shorts Watch Time" and "Today's Shorts Watched" — with page indicators), Quick Stats cards, and Recent Activity.
- **Activity**: usage timeline bar chart, most-used-apps donut chart, unlock/session stat cards, and expandable reports.
- **Web**: searchable Blocked / Allowed / Recent site lists with per-site toggles and a website count summary.
- **Settings**: dedicated screens for every section (Monitoring, Permissions, Notifications, Appearance, …) with global **Theme** and **Text Size** preferences under *Appearance*.
- **App chrome**: navigation drawer (slide-in with scrim), profile popover menu, and toast feedback.
- **Complete theme system**: dark + light palettes, system-follow mode, persisted selection, smooth animated switching, and automatic system-bar icon adaptation.
- **Authentication (mock, backend-ready)**: Splash → Welcome → Sign In / Create Account / Continue as Guest. Sign In offers **Email + Password**, **Continue with Google**, and **Continue with Mobile Number**; the mobile flow uses a country-code + phone-number field and reuses the shared **OTP Verification** screen (Forgot Password shares it too).

---

## Tech Stack

| Area | Choice |
| --- | --- |
| Language | Kotlin **1.9.24** |
| UI toolkit | Jetpack Compose (Compose BOM **2024.09.00**) + **Material 3** |
| Architecture | MVVM — `ViewModel` + `StateFlow` (single source of truth) |
| Dependency injection | **Hilt 2.51.1** (build-wired and ready; `@HiltAndroidApp` not yet applied — DI not used yet) |
| Navigation | Navigation Compose **2.8.0** (reserved for future drill-down screens; tabs use state-driven dispatch) |
| Async | Kotlin Coroutines **1.8.1** |
| Local persistence | SharedPreferences (theme preference) |
| Build system | Android Gradle Plugin **8.5.2**, Gradle **8.10.2**, Kotlin Android plugin 1.9.24 |
| SDK levels | compileSdk **35**, targetSdk **35**, minSdk **26** |
| JVM target | **17** |

---

## Prerequisites

- **Android Studio** (any recent version; the project was built with Gradle 8.10.2).
- **JDK 17+** (the local build was verified with JDK 22).
- **Android SDK Platform 35** installed via the SDK Manager.
- An Android device or emulator running **API 26 or newer** to run the app.

---

## Getting Started (build & run)

From the project root (`ShortCap/`):

```bash
./gradlew :app:compileDebugKotlin   # quick compile check (validates the code)
./gradlew assembleDebug             # build the debug APK
./gradlew installDebug              # build and install on a connected device/emulator
./gradlew test                      # run unit tests
```

Alternatively, open the project folder in **Android Studio**, let Gradle sync, and press **Run ▶**.

> **Windows users:** use `gradlew.bat` instead of `./gradlew` when running from a plain `cmd`, or run `./gradlew` from Git Bash (both work).

---

## Project Structure

```
ShortCap/
├── build.gradle.kts               # root build file (plugin versions)
├── settings.gradle.kts            # repositories + module setup
├── gradle.properties              # Gradle/JVM settings
├── gradle/wrapper/                # Gradle wrapper (8.10.2)
├── README.md
├── app/
│   ├── build.gradle.kts           # app module: SDK levels, dependencies
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/                   # launcher icons, styles.xml (XML app theme)
│       └── java/com/shortscap/app/
│           ├── MainActivity.kt        # entry point (edge-to-edge, Compose)
│           ├── ShortsCapApp.kt        # root composition: theme + chrome + screens
│           ├── components/            # reusable UI building blocks
│           │   ├── AppDrawer.kt       # navigation drawer + scrim
│           │   ├── BottomNavBar.kt    # floating bottom navigation pill
│           │   ├── CircularAnalytics.kt # animated circular widget + swipe carousel
│           │   ├── CommonComponents.kt # card, button, chip, switch, skeleton, etc.
│           │   ├── ProfileMenu.kt     # profile popover
│           │   ├── ScToast.kt         # toast overlay
│           │   └── TopBar.kt          # top app bar (hamburger | logo | avatar)
│           ├── model/Models.kt        # data classes & enums
│           ├── navigation/ScNavHost.kt # tab dispatch
│           ├── screens/
│           │   ├── activity/ActivityScreen.kt
│           │   ├── home/HomeScreen.kt
│           │   ├── rank/RankScreen.kt  # leaderboard tab (mock data)
│           │   ├── settings/SettingsScreen.kt
│           │   └── web/WebScreen.kt
│           ├── theme/
│           │   ├── Color.kt           # ScColors palettes + LocalScColors
│           │   ├── Theme.kt           # ThemeMode + ShortsCapTheme
│           │   ├── ThemePreferenceStore.kt # SharedPreferences persistence
│           │   └── Type.kt            # typography
│           └── viewmodel/AppViewModel.kt # single source of truth (StateFlow)

└── backend/                       # server backend skeleton (architecture only)
    ├── app/                       # FastAPI application (placeholder modules)
    ├── migrations/                # Alembic (configured later)
    ├── tests/                     # pytest package layout (empty)
    ├── requirements.txt           # planned Python dependencies (not installed)
    ├── .env.example               # environment template (no real secrets)
    ├── alembic.ini                # Alembic configuration (placeholder)
    └── README.md                  # backend architecture overview
```

---

## Architecture

- **Single source of truth.** `AppViewModel` exposes `StateFlow<AppUiState>`. Every screen is a stateless composable that receives its slice of state plus callback lambdas — mirroring the original RN `useState`/prop model.
- **Theme.** Two `ScColors` palettes (dark + light) are provided app-wide through the `LocalScColors` `CompositionLocal`. Components never hardcode colors; they read the active palette, so every screen adapts automatically. Switching themes **animates the palette colors** (~300 ms) while keeping the composition tree stable — no screen state (scroll, pager page) is lost and there is no restart or flicker.
- **Safe areas.** Edge-to-edge rendering is enabled (`enableEdgeToEdge`); the top bar, bottom navigation, toast, drawer, and popover all respect system bar insets (`statusBarsPadding` / `navigationBarsPadding`).
- **Backend-ready analytics.** The Home circular widget renders a list of `ScCircularMetric` models (`id`, `label`, `value`, `unit`, `progress`). Today the ViewModel seeds mock values; tomorrow the same fields can be fetched from a backend API with **no UI changes required**.

---

## Theme System (Dark / Light / System Default)

Located under **Settings → Appearance → Theme**.

- **Dark** — the original ShortsCap dark palette, preserved byte-for-byte.
- **Light** — a complete light palette (light surfaces, adjusted text/dividers, readable chip/toast/drawer colors).
- **System Default** — follows the Android device theme live (`isSystemInDarkTheme()`); the app updates automatically if the device switches modes.
- The selection is **persisted** in SharedPreferences (`ThemePreferenceStore`) and restored on the first frame at launch.
- Status bar and navigation bar **icon colors** sync automatically with the active theme (`WindowCompat`).

---

## Home Circular Analytics & Future Backend

- `ScCircularMetricRing` — reusable animated circular progress ring (value in the center, label below).
- `ScCircularAnalyticsCarousel` — horizontally swipeable pages (one metric per page) with animated page indicators.
- Ring colors come from `MaterialTheme.colorScheme`, so they adapt to the active theme.
- **Backend integration path:** replace the mock list in `AppViewModel.uiState.homeMetrics` with API-fetched data. The UI is data-driven and needs no changes.

---

## Dependencies

All declared in `app/build.gradle.kts` (versions verified).

### Compose / UI
| Artifact | Version | Purpose |
| --- | --- | --- |
| `androidx.compose:compose-bom` | 2024.09.00 | Aligns all Compose library versions |
| `androidx.compose.ui:ui` | BOM | Core Compose UI |
| `androidx.compose.ui:ui-graphics` | BOM | Canvas, brushes, draw scopes |
| `androidx.compose.ui:ui-tooling-preview` | BOM | Layout preview support |
| `androidx.compose.material3:material3` | BOM | Material 3 components (incl. `RadioButton`) |
| `androidx.compose.material:material-icons-extended` | BOM | Icons used by the app (Home, Schedule, etc.) |
| `androidx.activity:activity-compose` | 1.9.2 | `setContent`, `enableEdgeToEdge` |

### Lifecycle / Architecture
| Artifact | Version | Purpose |
| --- | --- | --- |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.6 | Lifecycle + coroutines |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.6 | `viewModel()` in Compose |
| `androidx.navigation:navigation-compose` | 2.8.0 | (Reserved) drill-down navigation |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | `delay`, `launch`, `StateFlow` updates |
| `com.google.dagger:hilt-android` | 2.51.1 | DI (Hilt-ready) |
| `com.google.dagger:hilt-compiler` (kapt) | 2.51.1 | DI compiler (Hilt-ready) |
| `androidx.core:core-ktx` | 1.13.1 | `WindowCompat` (system-bar icon control) |

### Testing
| Artifact | Version | Purpose |
| --- | --- | --- |
| `junit:junit` | 4.13.2 | Unit tests |
| `androidx.test.ext:junit` | 1.2.1 | Android instrumented tests |
| `androidx.test.espresso:espresso-core` | 3.6.1 | Instrumented UI tests |
| `androidx.compose.ui:ui-test-junit4` | BOM | Compose UI tests |
| `androidx.compose.ui:ui-tooling` (debug) | BOM | Compose tooling |
| `androidx.compose.ui:ui-test-manifest` (debug) | BOM | Test activity manifest |

---

## Change Log — what was modified and when

All work below was performed on **July 31, 2026** in the `ShortCap` working copy, on top of the existing (already-ported) app.

### Phase 1 — UI layout & safe-area corrections *(do-not-redesign pass)*
**Files:** `components/TopBar.kt`, `components/BottomNavBar.kt`, `ShortsCapApp.kt`, `components/ProfileMenu.kt`, `components/AppDrawer.kt`, `components/ScToast.kt`

- Added `statusBarsPadding()` to the **Top App Bar** so it sits comfortably below the status bar / display cutout (content stays 60 dp, bar background extends behind the status bar like modern Google apps).
- Added `navigationBarsPadding()` to the **Bottom Navigation** and the **Toast** so they float above the system navigation area.
- Enlarged bottom-nav touch targets from 46 dp → **52 dp** and improved spacing (pill padding 10 → 12 dp, item gap 6 → 8 dp).
- Anchored the **profile popover** below the top bar (`statusBarsPadding` + 64 dp, matching the original `top:64`) and the **drawer header** below the status bar.

### Phase 2 — Home dashboard circular analytics *(replaced the hero section)*
**Files:** `model/Models.kt` (new `ScCircularMetric`), `viewmodel/AppViewModel.kt` (mock metrics), `components/CircularAnalytics.kt` (**new** — `ScCircularMetricRing` + `ScCircularAnalyticsCarousel`), `screens/home/HomeScreen.kt` (hero replaced, old `TodaysSummaryCard` removed), `navigation/ScNavHost.kt` (wiring)

- Replaced the old "Today's Summary / usage / progress bar" hero with a large **animated circular progress ring**.
- Two swipeable pages: **Today's Shorts Watch Time** (`1h 30m`) and **Today's Shorts Watched** (`245 Shorts`), with animated page indicators.
- Reusable, data-driven design; mock data lives in the ViewModel so future **backend values** slot in without UI changes.

### Phase 3 — Complete theme system (Dark / Light / System Default)
**Files:** `theme/Color.kt` (`ScColors` object → theme-aware `ScColors` palette + `ScDarkColors`/`ScLightColors`/`LocalScColors` + brand constants), `theme/Theme.kt` (DARK/LIGHT/SYSTEM resolution, animated palette, system-bar sync), `theme/ThemePreferenceStore.kt` (**new**), `viewmodel/AppViewModel.kt` (`themeMode` + `setThemeMode`), `MainActivity.kt`, `ShortsCapApp.kt` (theme-aware root `Surface`), `screens/settings/SettingsScreen.kt` (**Theme selector** with Material 3 radio rows), plus **every component and screen** switched from the static color object to `LocalScColors.current`

- Dark palette preserved byte-for-byte; a complete, readable light palette added.
- **System Default** follows the device theme live; selection is **persisted** across restarts.
- Theme switches **animate colors** (~300 ms) with no restart, no flicker, and no loss of UI state (an initial root `Crossfade` was tried and then replaced by palette animation because the crossfade reset scroll/pager state).
- Status/nav bar icon colors follow the active theme; theme selector rows use proper a11y semantics (`Role.RadioButton`).

### Phase 4 — Home screen layout refinement *(alignment, scrolling, navigation width)*
**Files:** `components/CircularAnalytics.kt`, `components/BottomNavBar.kt`, `ShortsCapApp.kt`, `navigation/ScNavHost.kt`, plus this `README.md`

- **Circular widget truly centered:** each pager page is wrapped in a full-width `Box(contentAlignment = Center)` so the ring, value, and subtitle sit in the exact middle of the card on every page (true center alignment — no reliance on pager alignment semantics or manual offsets). All background layers behind the ring were removed (the dark full-circle track, then the gradient panel, then the card fill) so the animated ring now renders directly on the page surface inside the card's border - clean, open and premium — only the animated progress ring remains. Swipe, animation, and page indicators are unchanged.
- **Bottom navigation spans most of the screen width:** the floating pill now uses `fillMaxWidth()` with modest 16 dp side margins, icons are distributed evenly via `weight(1f)`, and touch targets grew to 56 dp-tall full-width items (the circular active indicator stays 52 dp). Rounded floating-pill style preserved.
- **Full scrolling:** the shared scroll container in `ScNavHost` now adds `navigationBarsPadding()` + 100 dp bottom padding, so the last content item always scrolls fully above the floating bottom navigation (which stays fixed).

### Phase 5 — Application launch flow & auth navigation wiring *(mock-only, backend-ready)*
**Files:** `AppRootNavHost.kt` (**new** — root NavHost: `auth_graph` → `dashboard`), `MainActivity.kt` (now launches `AppRootNavHost`), `viewmodel/AppViewModel.kt` (`sessionActive` placeholder + `setSessionActive`), `auth/**` (imported Auth UI module, packages renamed to `com.shortscap.app.auth.*`), plus this `README.md`

- **Launch flow:** the app now opens on the Auth flow first — **Splash → Welcome → (Continue as Guest / Sign In / Create Account)**. On mock success (guest, sign-in, or account creation), the flow swaps to the **Dashboard**, clearing the auth back stack (`popUpTo(auth_graph) { inclusive = true }`) so back on the Dashboard exits the app (standard Android behavior).
- **Forgot-password chain** wired inside the auth graph: Login → Forgot Password → OTP Verification → Reset Password → Login.
- **Back navigation** handled by the auth graph's own NavHost: Login → Welcome, Create Account → Welcome, Forgot → Login, OTP → Forgot, Reset → OTP.
- **Theme consistency:** the whole tree runs under `ShortsCapTheme`, so auth screens use the same design system and follow the persisted Dark / Light / System Default setting as the Dashboard; auth content is wrapped in safe-area padding (`statusBarsPadding` + `navigationBarsPadding`) without touching any auth screen layout.
- **Backend-ready seam:** `AppUiState.sessionActive` (default `false`) drives the root start destination. When AWS Cognito / the Python backend / JWT session state is connected, set it from real session state so the app opens straight to the Dashboard — no UI changes required.

### Phase 6 — Mobile Number (OTP) authentication & auth-screen spacing *(Aug 5, 2026)*
**Files:** `auth/screens/MobileLoginScreen.kt` (**new**), `auth/components/AuthComponents.kt` (`MobileSignInButton` / `EmailSignInButton`), `auth/screens/LoginScreen.kt`, `auth/navigation/AuthScreen.kt`, `auth/navigation/AuthNavGraph.kt`, `auth/screens/OtpVerificationScreen.kt`, plus spacing polish on `auth/screens/ForgotPasswordScreen.kt` / `CreateAccountScreen.kt` / `ResetPasswordScreen.kt`, and this `README.md` + `auth/README.md`

- **"Continue with Mobile Number"** added below the Google button on Sign In — a new `MobileSignInButton` using the modern **smartphone icon** (not a telephone receiver), visually identical to the Google button (same 56 dp outline style, 16 dp corners, icon + label).
- **New `MobileLoginScreen`** — a dedicated screen (the Email Login page is untouched, not replaced). Same logo, "Welcome Back", glow accents, footer and buttons as Sign In; adds a single horizontal phone input (country selector with flag + dial code + dropdown → vertical divider → digit-only number field), a **Send OTP** button, and a **"Sign in with"** section with compact **[ Google ] [ Email ]** buttons (Email returns to the Email Login screen, Google starts the Google flow).
- **Extensible country catalog:** `PhoneCountry` + `SupportedPhoneCountries` ship with India (+91), USA (+1), UK (+44), Canada (+1), Australia (+61), UAE (+971). Adding a country is **one list entry**; the field auto-caps digits per country and the selector highlights the current pick with a checkmark.
- **OTP screen reused — zero duplication:** `OtpVerificationScreen` now carries two optional route args — `destination` (email or phone) and `mode` (`reset` | `login`). Forgot Password passes the email; Mobile Login passes e.g. `+91 9876543210`. On "Verify", `mode=login` completes into the Dashboard, `mode=reset` keeps the existing Reset Password path.
- **Back-stack safe:** the **Email** button pops back to the Email Login (no duplicate back-stack entries); the back button on Mobile Login returns to Sign In; "Create Account" behaves exactly like from Sign In.
- **Spacing rebalanced** across all auth form screens on a consistent **8dp Material grid** (small 8–12 / medium 16 / large 24): Sign In content moved ~22 dp upward, fields and their helper rows are visually grouped, and every screen keeps comfortable top/bottom margins above the system bars.

### Phase 6.1 — Compact social login row & top-section polish on Sign In *(Aug 5, 2026)*
**Files:** `auth/components/AuthComponents.kt` (new `SocialLoginRow`), `auth/screens/LoginScreen.kt`, and this `README.md`

- **Two stacked full-width buttons → one compact row:** "Continue with Google" + "Continue with Mobile Number" are now **`| G Google | 📱 Mobile Number |`** — two equal-width outline buttons (same 56 dp height, 16 dp corners, 1 dp border, official Google "G" and modern smartphone icons) with a 12 dp gap. Saves one full button row of vertical space; tap targets unchanged (56 dp). The "Mobile Number" button navigates to the Mobile Login screen.
- **Top section tightened:** status-bar margin and back-button gap reduced to 4 dp and the logo→heading gap to 12 dp, moving the logo/heading block ~12 dp higher while the logo stays well clear of the status bar.
- **Rebalanced:** with the shorter social row the Sign In content now fits typical screens without scrolling, ending with comfortable bottom breathing space. Only Sign In was touched, and all other screens and all logic are unchanged.

### Phase 6.2 — Mobile Login "Sign in with" section *(Aug 5, 2026)*
**Files:** `auth/components/AuthComponents.kt` (`OrDivider` text param, new `SignInWithRow`), `auth/screens/MobileLoginScreen.kt`, and this `README.md`

- **Subtitle** on Mobile Login is now **"Sign in with your mobile number."** (same typography/style as before).
- The two full-width fallback buttons were replaced by the Sign In-style section: a **"Sign in with"** divider (same styling as the "OR" divider — `OrDivider` gained a configurable `text` param, default unchanged) above a compact **[ Google ] [ Email ]** row (`SignInWithRow`) — equal width, same 56 dp height / 16 dp corners / 1 dp border / colors / spacing / typography as Sign In's social row.
- **Google** reuses the official "G" icon and triggers the existing Google flow; **Email** uses the mail icon and pops back to the existing Email Sign In screen (no new screen created). Phone input, Send OTP, OTP verification, and the bottom section (Create Account / Privacy / Terms) are unchanged.

### Phase 6.3 — Create Account screen aligned with the Sign In design *(Aug 5, 2026)*
**Files:** `auth/screens/CreateAccountScreen.kt`, `auth/navigation/AuthNavGraph.kt`, and this `README.md`

- Added the Sign In-style **logo** (106 dp, same spacing) and the premium glow accents; the title was reduced to the same **28 sp** as "Welcome Back" and the tagline is now "Create your account and start taking control of your digital habits."
- Added a **"Sign up with"** section exactly matching Sign In: divider + compact **[ Google ] [ Mobile Number ]** row (`SocialLoginRow` — same size, border, radius, colors, spacing and press animation). Google uses the existing Google flow; **Mobile Number** uses the smartphone icon and navigates to the existing Mobile Login flow via a new `onMobileSignIn` callback (that flow is untouched).
- **Spacing rebalanced** on the same 8dp grid as Sign In: fields 12 dp apart, the Terms & Privacy row sits closer to the password section, and the bottom margin was tightened. Field order (Full Name → Email → Password → Confirm Password), password validation, and all auth logic are unchanged.

### Phase 6.4 — Complete Profile screen (shared by Email / Google / Mobile flows) *(Aug 5, 2026)*
**Files:** `auth/screens/CompleteProfileScreen.kt` (**new**), `auth/components/AuthComponents.kt` (new `AuthPickerField`), `auth/navigation/AuthScreen.kt`, `auth/navigation/AuthNavGraph.kt`, and this `README.md`

- **New dedicated `CompleteProfileScreen`** — same auth design language (logo, glows, 28 sp heading, compact 50 dp fields, footer). Layout: back button → logo → **"Complete Your Profile"** → "One last step before you get started." → **Name** (pre-fillable via `initialName` for Google, editable) → **Gender** dropdown (Male / Female / Prefer not to say) → **Date of Birth** (Material `DatePickerDialog`, displayed **DD/MM/YYYY**, no plain-text input, future dates disabled) → full-width **Continue** → Privacy / Terms.
- **Validation on Continue** using the existing error style (`isError` + red supporting text): Name required, Gender required, Date of Birth required; errors clear as the user fixes each field.
- **Shared by all three flows** (one screen, zero duplication): Email Create Account → Complete Profile → Dashboard; Google → Complete Profile → Dashboard; Mobile Login → OTP → Complete Profile → Dashboard. "Continue" saves the profile (mock) and exits to the Dashboard via `onExitToDashboard()`.
- **Reuse:** new `AuthPickerField` (compact 50 dp / 14 dp corner picker with animated border + floating label + error support) lives in `AuthComponents.kt`; everything else reuses `AuthTextField`, `AuthPrimaryButton`, `AuthBackButton`, the theme, and the footer pattern.

### Phase 6.5 — Create Account simplified to Email + Password, email-verification step *(Aug 5, 2026)*
**Files:** `auth/screens/CreateAccountScreen.kt`, `auth/navigation/AuthScreen.kt`, `auth/navigation/AuthNavGraph.kt`, and this `README.md`

- **Create Account now collects only Email + Password** (per the master spec — **Full Name** and **Confirm Password** removed; the password strength indicator and the Terms checkbox remain). Layout: Logo → heading → tagline → Email → Password → Create Account → "Sign up with" [Google] [Mobile Number] → "Already have an account? Sign In" → Privacy • Terms.
- **Email verification step added without a new screen:** after Create Account, the shared **OTP Verification** screen is reused as the email-verification step (new `mode=email_verify`; it already shows "code sent to <email>"), then **Complete Profile** → **Dashboard**. The full Email flow now matches the spec: `Email → Password → Create Account → Verify Email → Complete Profile → Dashboard`.
- Google → Complete Profile → Dashboard and Mobile → OTP → Complete Profile → Dashboard were already wired (Phase 6.4) and are unchanged.

### Phase 7 — Rank tab: bottom navigation + dedicated leaderboard screen *(Aug 13, 2026 — UI only, mock data)*
**Files:** `model/Models.kt` (`ScScreen.RANK`), `ShortsCapApp.kt` (bottom-nav items — now **five**, Rank centered: Home | Activity | Rank | Web | Settings), `icons/IconModels.kt` (`IconKey.RANK`), `icons/IconTheme.kt` (Rank icon mapping), `i18n/AppStrings.kt` + all five language files (English / Hindi / Urdu / Chinese / Spanish — full Rank string catalog), `navigation/ScNavHost.kt` (`ScScreen.RANK → RankScreen()`), `screens/rank/RankScreen.kt` (**new**), `i18n/ChineseStrings.kt` (template fix in `rankPositionChange`)

- **Bottom navigation grows from 4 to 5 items** — Rank sits in the **center** (Home | Activity | Rank | Web | Settings); Home, Activity, Web and Settings are untouched.
- **New `RankScreen`** — a dedicated tab (not a dialog/sheet, not inside Home or Settings) with the required structure: **Your Rank / Your Score** hero status card (+ movement badge), **This Week | This Month** pill filter, **Top 3 podium** (2nd | 1st | 3rd, bottom-aligned pedestals), **full leaderboard** (rank / name / score rows; the current user's row is subtly highlighted as "You"), and a **Your Progress** 2×2 metrics section (Shorts, Distracting Apps, Study Sessions, Your Score).
- **All values are clearly marked mock placeholders** — no backend calls, no leaderboard tables, no score engine, no Rank settings, no ranking APIs. Loading / Empty / Error UI states are prepared behind a `RankUiState` sealed type (defaults to mock data) so the future backend plugs in without UI redesign.
- **Fully wired into the existing global systems**: theme (`LocalScColors` — Dark / Light / System), language (`LocalAppStrings` — all 5 catalogs, RTL-aware), font & text size (`ScTextStyles`, which resolve the active global font at use time), and icons (`IconTheme` + `IconKey.RANK` + active icon style).
- **Calm animations only**: score count-ups, staggered podium / leaderboard entrances, and one animated progress bar.

### Phase 7.1 — Rank icon refinement: trophy + animation *(Aug 13, 2026 — visual only)*
**Files:** `icons/IconTheme.kt` (Rank icon → trophy), `screens/rank/RankScreen.kt` (status-card trophy entrance animation, "#" removed)

- **Rank icon changed from the bar-chart to a trophy** (`Icons.Filled.EmojiEvents`) everywhere it is used — the **bottom navigation** and the **Rank screen header/status card** — so it no longer looks like Activity's chart-style icon. Activity's icon is unchanged; Rank stays in the center position.
- **"#" removed** from the user's rank — the status card now reads **Your Rank / 12** (no prefix). "Your Rank" and "Your Score" labels are unchanged.
- **Subtle one-shot trophy animation** on the Rank screen header: soft scale-in with a gentle bounce/settle (0.85 → 1.08 → 1.00) plus a faint glow pulse. It plays **once per Rank screen entry** (not on recomposition, never loops, no per-frame work) using lightweight native Compose animation — works offline, respects the existing theme colors.

---

## Mobile Number Authentication (OTP Login)

A premium, design-consistent **mobile-number login** was added without touching the existing Email or Google flows. Users can now switch freely between **Email Login**, **Google Login**, and **Mobile Number Login** with zero visible difference in design quality.

### Flow

```
Splash → Welcome → Sign In
  ├─ Continue with Mobile Number → Mobile Login
  │     └─ Send OTP → OTP Verification (mode=login) → Verify → Dashboard
  └─ Forgot Password → OTP Verification (mode=reset) → Reset Password → Login
```

### How it's implemented

| Concern | File | What it does |
| --- | --- | --- |
| Entry point | `auth/screens/LoginScreen.kt` | New `onMobileSignIn` callback; the compact `SocialLoginRow` (Google + Mobile Number) routes the Mobile Number button to it |
| New screen | `auth/screens/MobileLoginScreen.kt` (**new**) | Country-code + phone-number input, Send OTP, a **"Sign in with"** section ([Google] [Email]), standard footer. Owns `PhoneCountry` + `SupportedPhoneCountries` |
| Option buttons | `auth/components/AuthComponents.kt` | `SocialLoginRow` (Google + Mobile Number on Sign In) and `SignInWithRow` (Google + Email on Mobile Login) — compact half-width outline buttons sharing one private style matching the Google button |
| Routes | `auth/navigation/AuthScreen.kt` | `mobile_login` route; `OtpVerification` gains `destination` + `mode` args (`createRoute(...)` helper, URI-encoded) |
| Wiring | `auth/navigation/AuthNavGraph.kt` | Mobile Login → OTP with `mode=login`; Forgot Password → OTP with the email; verify branches by mode (`login` → Dashboard, `reset` → Reset Password) |
| Shared OTP UI | `auth/screens/OtpVerificationScreen.kt` | Renamed `email` → `destination` so one screen shows "code sent to <email | phone>" for both flows |

### Key mechanics

- **Single-row phone field** — one 50 dp box (14 dp corners, animated 1→2 dp border on focus) split by a vertical divider: country selector (flag emoji + dial code + caret, opens a `DropdownMenu`) on the left, digit-only `BasicTextField` (phone keyboard) on the right.
- **Input hygiene** — only ASCII digits `0-9` are accepted, capped at the selected country's `maxNumberDigits`; "Send OTP" enables once 7–max digits are entered.
- **No new OTP UI** — the existing 6-box OTP screen is reused for both Forgot Password and Mobile Login; only the subtitle and the post-verify destination differ (`mode` arg).

### How to extend

- **Add a country:** append one `PhoneCountry(name, dialCode, flag, maxNumberDigits)` entry to `SupportedPhoneCountries` — nothing else changes.
- **Wire a real OTP backend:** everything is already callback-based (`onSendOtp`, `onVerify`, `onResend`). Replace the mock bodies with your provider (e.g., SMS gateway / Firebase Phone Auth) — no UI changes required.

---

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `SDK location not found` / Gradle sync fails | Create/check `local.properties` with `sdk.dir=C\:\\path\\to\\Android\\Sdk` |
| Java version errors | Set JDK 17 in Android Studio → Settings → Build Tools → Gradle |
| `Icons.AutoMirrored` deprecation warnings | Purely informational; existing icons are intentional |
| Hilt kapt processor warnings | Expected while Hilt is build-ready but not wired (`@HiltAndroidApp`) |

---

# Permissions Module

## What was added

Settings → **Permissions** is a clean, minimal **permission status overview** — it is **not** a permission-request page (permissions are requested during first-time onboarding/setup). Each permission appears as a compact **settings row** (icon · name · colored status) with **no action buttons** — the entire row is the tap target, opening the correct Android settings page for that permission (works the same whether the status is Enabled or Disabled; the status text is informational only). Rows are auto-refreshed whenever the screen resumes (first open AND returning from Android Settings), so statuses always reflect the real Android system state with **no manual refresh button**. Uses the ShortsCap Premium Dark theme and mirrors modern Android settings screens.

## Supported Permissions

| # | Permission | Purpose | Status when granted |
| --- | --- | --- | --- |
| 1 | **Usage Access** | Monitor application usage time | Enabled |
| 2 | **Accessibility Service** | App blocking & restriction enforcement | Enabled |
| 3 | **Display Over Other Apps** | ShortsCap's small monitoring Brain indicator above supported short-video apps | Enabled |
| 4 | **Notification Permission** | Reminders & monitoring alerts | Enabled |
| 5 | **Ignore Battery Optimization** | Reliable background operation | Enabled |
| 6 | **Storage / Media Access** | Profile image selection & future backups | Enabled |
| 7 | **System Audio Access** | Study Mode's Sound Mode (ring-mode control) | Enabled |

## Tap behavior

- **The entire row is the action target** — there are no separate "Enable" / "Manage" buttons. Tapping anywhere on a row (Enabled **or** Disabled) opens the **corresponding Android Settings screen** via `PermissionActions`: Usage Access, Accessibility, Overlay/Display Over Other Apps, App Notification, Battery Optimization, App-details (Storage), and Notification Policy Access (System Audio Access) pages.
- Android does not let the app revoke most of these programmatically, so the system settings page is always the grant **and** revoke path — the app never shows a fake in-app "Disable" toggle.
- Only if Android exposes **no** settings screen for a permission does the row fall back to the informational **detail page**.

## Permission Status System

- **One consistent status vocabulary** — every permission shows only **Enabled** (active/working) or **Disabled** (missing, denied, or inactive); the underlying Android states are normalized in the UI.
- **Status colors** — 🟢 green = Enabled; 🟠 orange / 🔴 red = Disabled (color reflects the internal state — not granted vs denied).
- **Live OS checks** — `permissions/PermissionRepository.kt` resolves every status from the Android OS: Usage Access via `AppOpsManager`, Accessibility via `ENABLED_ACCESSIBILITY_SERVICES`, overlay via `Settings.canDrawOverlays`, notifications via `NotificationManagerCompat`, battery via `PowerManager`, storage via `ContextCompat.checkSelfPermission` (version-aware: `READ_MEDIA_*` on Android 13+, including partial "Select photos" access on 14+), System Audio Access via `isNotificationPolicyAccessGranted`.
- **Automatic refresh** — `RefreshPermissionsOnResume` (a `LifecycleEventObserver`) re-checks all permissions every time a Permissions screen reaches `ON_RESUME`; returning from Android Settings updates the UI instantly.
- **Last checked time** — every `PermissionInfo` stamps `lastCheckedAt`; the detail page shows it (or "Never").

## Permission Detail Page

A simple read-only page serving as the graceful fallback when Android exposes no settings screen for a permission (the rows themselves open the real Android settings pages directly). Shows: permission icon + title, **current status** (colored), **why this permission is required** (purpose), and **last checked** time. No action buttons, no inline expansion.

## Backend Integration Ready

- **Models** (`permissions/PermissionModels.kt`): every permission has `id`, `status`, `lastCheckedAt`, plus **future cloud-sync placeholder** (`cloudSyncEnabled`) and **future analytics placeholder** (`analyticsEvent`).
- **Repository seam** (`permissions/PermissionRepository.kt`): `checkAll`/`checkStatus` are the only data touch-points; documented `syncPermissionsToCloud(...)` and `trackPermissionAnalytics(...)` placeholders mark the future API/analytics calls.
- **Actions seam** (`permissions/PermissionActions.kt`): centralizes every system-settings intent.
- **No UI changes required later** — screens consume `List<PermissionInfo>`; swapping the OS checks for backend data only replaces the repository body.
- Manifest declares the permissions surfaced by the module (`POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `PACKAGE_USAGE_STATS`, storage/media).

## Files

- `permissions/PermissionModels.kt`, `permissions/PermissionRepository.kt`, `permissions/PermissionActions.kt` — Permissions module (models, OS checks, settings intents)
- `screens/settings/PermissionsScreen.kt` — minimal settings-row status list
- `screens/settings/PermissionDetailScreen.kt` — simple status + purpose detail page
- `screens/settings/PermissionUi.kt` — shared helpers (icons, labels, status colors, auto-refresh)
- `navigation/SettingsNavHost.kt` — `PermissionsScreen` + `settings_permission_detail/{id}` route
- `viewmodel/AppViewModel.kt` — `AppUiState.permissions` + `refreshPermissions()`
- `i18n/` — full catalog (EN / HI / UR / ZH / ES) for the status list + detail pages
- `AndroidManifest.xml` — permission declarations

---

*ShortsCap v2.4.1 · Build 2026072801 · © 2026 ShortsCap*

---

# Dynamic App & Website Icons Architecture (Future Backend Ready)

You are working on the existing ShortsCap Android project built with Kotlin + Jetpack Compose + Material Design 3.

This is NOT a redesign request.

Do NOT modify the existing UI layout, navigation, colors, animations, spacing, or screen hierarchy.

Only enhance the existing architecture to support dynamic application and website icons.

--------------------------------------------------------

OBJECTIVE

Currently, the application displays application names and website names such as:

Instagram

WhatsApp

Chrome

YouTube

Reddit

X (Twitter)

TikTok

etc.

However, no icons are displayed.

This makes the interface look incomplete.

Every application and website should display its official icon/logo beside its name.

--------------------------------------------------------

WHERE THIS SHOULD WORK

The icon system must work everywhere in the application.

Examples include:

• Home → Recent Activity

• Activity Screen

• Web Screen

• Restricted Apps

• Restricted Websites

• Search Results

• Future Analytics Screens

• Future History Screens

Every list displaying an app or website must automatically display its icon.

--------------------------------------------------------

APPLICATION ICONS

Every Android application should display its official installed application icon.

Examples:

Instagram

WhatsApp

Chrome

YouTube

Facebook

Telegram

Snapchat

Discord

Spotify

Gmail

etc.

The icon should always appear before the application name.

Maintain consistent icon size.

Maintain consistent spacing.

Icons should be vertically centered.

--------------------------------------------------------

WEBSITE ICONS

Every website should display its favicon or official logo.

Examples:

reddit.com

x.com

youtube.com

instagram.com

facebook.com

tiktok.com

github.com

stackoverflow.com

etc.

Display the favicon before the website name.

If a favicon is unavailable, show a clean default web icon instead of leaving the space empty.

--------------------------------------------------------

FUTURE BACKEND READY

For the current frontend, mock data is acceptable.

However, the architecture must be designed so that future backend integration requires only replacing the data source.

When backend APIs are connected, each item should support fields similar to:

App Name

Package Name

App Icon

Website Name

Website URL

Website Icon

Usage Time

Restriction Status

Timestamp

The UI should automatically render the icon from the provided data.

No UI redesign should be required.

--------------------------------------------------------

ACCESSIBILITY SERVICE READY

The architecture should also be prepared for future Android Accessibility Service integration.

When the application begins monitoring real user activity:

Installed application information

Package names

Application icons

Usage statistics

Restriction status

should all be capable of being retrieved and displayed.

No changes to the UI should be required later.

Only the repository/data source should change.

--------------------------------------------------------

DATA MODEL

Design the UI using reusable models.

Example fields may include:

id

title

packageName

websiteUrl

icon

type (App / Website)

usageTime

restrictionStatus

timestamp

This allows the same reusable UI components to render both applications and websites.

--------------------------------------------------------

REUSABLE COMPONENTS

Create reusable list item composables.

Each item should support:

Leading Icon

Title

Subtitle

Usage Information

Restriction Status

Trailing Action

The same component should work for:

Recent Activity

Restricted Apps

Websites

Search Results

History

Future Lists

Avoid duplicate UI implementations.

--------------------------------------------------------

ICON LOADING

Icons should load efficiently.

Prevent unnecessary recompositions.

Provide placeholder icons while loading.

Display fallback icons if an icon cannot be found.

Never leave empty space where an icon should appear.

--------------------------------------------------------

PRESERVE EXISTING DESIGN

Do NOT redesign any screen.

Do NOT modify the Home layout.

Do NOT modify Recent Activity design.

Do NOT modify the Web screen layout.

Do NOT modify Bottom Navigation.

Do NOT modify Top App Bar.

Do NOT modify animations.

Do NOT modify colors.

Do NOT modify spacing.

Only enhance the existing architecture so that every application and website displayed in the UI automatically shows its correct icon now (using mock data) and later (using backend APIs or Android Accessibility data) without requiring any UI redesign.

The ShortsCap design system must remain visually identical while becoming fully dynamic and future-ready.

--------------------------------------------------------

## Dashboard Drawer Enhancements

The Dashboard Navigation Drawer (three-line menu) was upgraded into a production-quality, modern, scalable navigation system while keeping the existing ShortsCap design language, dark theme, and branding consistent.

**Drawer Menu (final order):**

1. Help & Support
2. Privacy Policy
3. Terms & Conditions (NEW)
4. About ShortsCap
5. Feedback
6. Share App

**Drawer header (unchanged):** ShortsCap logo, "ShortsCap", "Digital Wellbeing".

**Drawer footer (updated):** Version 1.1.1, Build 2026072801, ©2026 ShortsCap, All Rights Reserved. The footer was moved slightly upward (~24dp) so it no longer touches the bottom navigation area.

### What was added

• Added Help & Support screen
• Added FAQ accordion (expandable, animated, initially collapsed)
• Added Contact Support section
• Added Bug Report page
• Added Privacy Policy reader
• Added Terms & Conditions reader
• Added About ShortsCap page
• Added Feedback page
• Added Native Share App integration
• Updated Footer
• Version changed to 1.1.1
• Build number added
• Prepared modular architecture for backend integration
• Future APIs planned

### Screens

**Help & Support** — three sections:
1. Frequently Asked Questions — expandable accordion cards (initially collapsed) covering how ShortsCap works, Accessibility/Usage Access permissions, blocking Shorts, password reset, OTP issues, profile updates, and account deletion.
2. Contact Support — displays support@shortscap.app.
3. Report a Bug — Subject and Description fields with a Submit button that shows a success message.

**Privacy Policy / Terms & Conditions** — full-screen, read-only, scrollable document readers that display the local text files with preserved formatting, headings, and chapters. Each includes a Back button and comfortable reading padding.

**About ShortsCap** — logo, name/tagline, Version (1.1.1), Build (2026072801), About (purpose/mission/vision), Features, Technologies (Android, Kotlin, Jetpack Compose, Python Backend (Future), AWS Cloud (Future)), and Copyright.

**Feedback** — 5-star rating selector, feedback text box, and a Submit button that shows "Thank you for your feedback."

**Share App** — launches the native Android share sheet with a ShortsCap message.

### Architecture notes

All drawer sub-screens are full-screen destinations managed through a single `DrawerScreen` enum in the `AppUiState` (`HelpSupportScreen`, `LegalDocumentScreen`, `AboutShortsCapScreen`, `FeedbackScreen`). Each screen is decoupled from data sources behind small seams (e.g. `LegalDocumentLoader` for the local text assets, callback parameters for Bug Report / Feedback submissions), so connecting real backend APIs later requires only swapping the data layer — no UI redesign.

### Future Integration

These screens are intentionally prepared for backend APIs without requiring UI redesign. Planned endpoints:

• Privacy Policy API
• Terms API
• Feedback API
• Support API
• Bug Report API
• About API
• Version API
• Share API

When the backend is connected, the local text-asset loader for legal documents will be replaced by backend-hosted HTML pages, and Bug Report / Feedback / Support submission callbacks will post to the corresponding APIs — all behind the same UI and navigation already in place.

---

# Redesigned Settings Module & Monitoring Settings Screen

## What changed

### 1. Settings home screen — premium redesign *(no more basic settings list)*

- Every row is now a **large premium card**: icon tile (60 dp, 36 dp icon) + title + chevron — **no subtitles** anywhere on the home page.
- Cards use the shared `ScPremiumNavCard` visual language (22 dp corners, 1 dp border, resting shadow, soft press-scale animation, accent glow/border while pressed) with generous 14 dp spacing between cards.
- The old **inline expansion** (tap a category to unfold switches inside the Settings page) was removed entirely. Each item now opens its own **dedicated screen**.

Settings list (icon + title + chevron only):

1. General
2. Monitoring
3. Permissions
4. Notifications
5. Appearance
6. Data Backup
7. About
8. Reset All Settings

### 2. New dedicated Monitoring screen

The Monitoring screen (`screens/settings/MonitoringScreen.kt`) is a full page with its own back button and a clean, configuration-only structure (Home holds the quick summary, Activity the detailed reports):

| # | Section | Control |
| --- | --- | --- |
| 1 | Monitoring | **Device Monitoring** — master switch + **Enabled / Disabled** status (same vocabulary as the Permissions screen) + a small circular **info button** opening a dialog explaining what is monitored, why the required Android permissions matter, and what happens if one is disabled |
| 2 | Strict Mode | Switch ("Prevent bypassing restrictions.") |
| 3 | Study Mode | **Study Mode** — the complete study-focus feature relocated from the General section (duration, break reminder, schedule, allowed apps/websites, summary, start session) |
| 4 | Shorts | **Shorts Control** — opens the dedicated per-platform screen (YouTube Shorts / Instagram Reels / Facebook Reels / Snapchat Spotlight, each with its own real brand icon + independent switch) |
| 5 | Monitoring Schedule | Dedicated page (UI only; start/end time + weekdays/weekends later) |

Removed from Monitoring: **Break Reminder / Reminder Interval** (it now lives exclusively in **Study Mode**, which now sits in the Monitoring section), **Enable App Blocking**, **Blocked Apps**, **Allowed Apps**, **Daily Screen Time Limit**, the per-platform Shorts toggles and the read-only Statistics tiles — the underlying concepts/models are preserved where needed for the future Settings/Restriction section; only this page's dependency was removed.

All pickers are clean Material 3 dialogs styled with the ShortsCap dark theme; the current selection is highlighted with a checkmark.

### 3. Other dedicated Settings screens

- **General / Permissions / Data Backup** — dedicated screens; Data Backup uses the generic `SettingsSectionScreen` placeholder page ("Coming soon").
- **Notifications** — premium hub: 6 category rows, each opening its own dedicated option page with toggles.
- **Appearance** — hub with **Theme / Text Size**, each on its own dedicated page.
- **About** — Version 2.4.1, Build 2026072801, © 2026 ShortsCap; also hosts **Privacy Policy** and **Terms & Conditions**, each opening the existing bundled legal document.
- **Reset All Settings** — the last row on the Settings home; tapping it opens a premium in-place confirmation dialog (no separate screen).

### 4. Design rule — no introduction cards (applies to every Settings page, now and future)

Every Settings sub-page follows one strict design language:

- **No introductory information card** at the top of any sub-page — no "Manage the permissions…", no "Choose which notifications…", and no similar repeated description cards.
- Each page contains only a **top app bar** (back button + page title) and the **settings list / options**.
- **Future rule:** any new Settings page added later must follow the same design — no introduction cards, no repeated descriptions, only clean navigation and settings options.

## Navigation flow

All settings sub-screens run inside a new `SettingsNavHost` (Navigation Compose) with a real back stack and a `BackHandler`:

```
Settings → Monitoring → Blocked Apps
    Back → Monitoring
    Back → Settings
```

- **System Back** pops the settings stack one level at a time; at the root it returns to the Settings tab. **Back never exits the app** from any settings screen.
- Each Settings row keeps its own route, so deep-linking / predictive back will work later with no restructure.
- Opening a settings screen sets `AppUiState.settingsDestination`; closing it clears it (same overlay pattern as the dashboard drawer screens).

## Backend-ready architecture

- **No business logic is hardcoded in the UI.** Every setting lives in the `MonitoringSettings` model (`model/Models.kt`) held by `AppViewModel` as the single source of truth (`StateFlow<AppUiState>`). The Monitoring screen is a stateless composable receiving the model + callback lambdas.
- **Placeholder API seams** (documented, not implemented) — ready for a `SettingsRepository`:
  - `GET / UPDATE Monitoring Settings` → `MonitoringSettings` fields (master, strict mode, per-platform Shorts, schedule; app blocking / screen-time limit fields remain reserved for the future Settings/Restriction section)
  - `GET / UPDATE Blocked Apps` → Blocked Apps page
  - `GET / UPDATE Allowed Apps` → Allowed Apps page
  - `GET / UPDATE Monitoring Schedule` → Schedule page (start/end time, weekdays/weekends)
- **Future cloud integration:** the same seams support Firebase / AWS backend sync and a future local database (e.g. Room) — swapping the data source requires **no UI changes**.
- Monitoring statistics were removed from `MonitoringSettings` — Home's Quick Status derives **Today Usage** directly from the same `ActivityRepository` Daily report that powers Activity → Daily (one source of truth), and the Web counts come from the Web rule list.

## Files

---

# General Settings — Language Module (Internationalization)

## What was added

- **General → Language**: the General settings screen now contains a single **🌐 Language** option that opens a dedicated full-screen Language picker (no dialog/bottom-sheet for the list). Flow: `Settings → General → Language`.
- **Language screen**: flag + native name (with English name, or “(Default)” for English) per language, a checkmark on the current selection, single-select only, and a confirmation dialog on selection — **Current / New / Cancel / Apply**.
- **Apply** instantly switches the language across the **entire logged-in experience** and pops back to General.

## Supported languages

| Flag | Language | Direction |
| --- | --- | --- |
| 🇬🇧 | English (Default) | LTR |
| 🇮🇳 | हिन्दी (Hindi) | LTR |
| 🇵🇰 | اردو (Urdu) | **RTL** |
| 🇨🇳 | 中文 (Simplified Chinese) | LTR |
| 🇪🇸 | Español (Spanish) | LTR |

Urdu also flips the app’s **layout direction to RTL** (`LocalLayoutDirection`). The **Auth flow** (Splash, Welcome, Sign In, Sign Up, Forgot Password, OTP, Reset Password) intentionally stays in English.

## Internationalization (i18n) architecture

- **Centralized catalog**: `i18n/AppStrings.kt` defines the `AppStrings` interface — every user-visible string in the logged-in experience (Dashboard, Profile, Settings + all sub-pages, Monitoring, Drawer + all sub-pages, Help & Support, FAQ, Contact, Report a Bug, Feedback, About pages, Privacy Policy / Terms readers, all dialogs, toasts, and content descriptions).
- **One file per language**: `EnglishStrings.kt`, `HindiStrings.kt`, `UrduStrings.kt`, `ChineseStrings.kt`, `SpanishStrings.kt`. The interface is the contract: **adding a string breaks compilation until all five languages translate it**, so nothing can silently fall back.
- **No hardcoded translated text in UI**: every screen reads `LocalAppStrings.current` (a `staticCompositionLocalOf`) — swapping the language recomposes all screens instantly.
- **Adding a new language** = one `AppLanguage` entry + one new catalog file. **No UI code changes required.**
- The `AppStrings` interface is a plain Kotlin contract, so the ViewModel resolves toast text via `AppStrings.forLanguage(...)` without Compose dependencies.

## Language persistence

- `i18n/LanguagePreferenceStore.kt` persists the selection in SharedPreferences; the language is restored automatically on the next launch (`AppUiState.appLanguage`).
- Applying a language shows a **smooth transition overlay** (spinner + “Applying language…” fade, ~650 ms) instead of an abrupt reload.

## Future backend synchronization

- `i18n/LanguageRepository.kt` documents the cloud seams: `syncLanguageToCloud(language)` (PUT the preference on the user’s cloud profile) and `loadLanguageFromCloud()` (pull on login). Placeholders only — swap the bodies when AWS / Firebase / the Python backend connects; no UI changes required.
- Legal documents support **localized assets**: the reader loads `privacy/PrivacyPolicy_{lang}.txt` / `terms/TermsConditions_{lang}.txt` when present and falls back to the English source — translated legal text can be dropped into `assets/` with no code changes.

## Files

- `i18n/AppLanguage.kt`, `i18n/AppStrings.kt`, `i18n/EnglishStrings.kt`, `i18n/HindiStrings.kt`, `i18n/UrduStrings.kt`, `i18n/ChineseStrings.kt`, `i18n/SpanishStrings.kt` — **new** i18n core
- `i18n/LanguagePreferenceStore.kt`, `i18n/LanguageRepository.kt` — **new** persistence + cloud seam
- `screens/settings/LanguageScreen.kt`, `screens/settings/GeneralScreen.kt` — **new** screens
- `navigation/SettingsNavHost.kt` — **new** `settings_language` route
- `viewmodel/AppViewModel.kt` — `appLanguage`, `languageApplying`, `applyLanguage()`
- `ShortsCapApp.kt` — string + RTL providers, localized drawer/toasts/share, applying overlay
- Every logged-in screen — literals replaced with catalog lookups

---

# Redesigned Settings Module & Monitoring Settings Screen

## What changed

### 1. Settings home screen — premium redesign *(no more basic settings list)*
- `screens/settings/SettingsSectionScreen.kt`, `NotificationsScreen.kt`, `AppearanceScreen.kt`, `AboutSettingsScreen.kt`, `ResetAllScreen.kt` — **new** dedicated screens
- `screens/settings/BlockedAppsScreen.kt`, `AllowedAppsScreen.kt`, `MonitoringScheduleScreen.kt` — **new** UI-only pages
- `navigation/SettingsNavHost.kt` — **new** settings back-stack NavHost
- `model/Models.kt` — `SettingsDestination`, `SettingsItem`, `MonitoringSettings`, `ShortVideoPlatform`
- `viewmodel/AppViewModel.kt` — settings navigation + monitoring state & setters, `resetAllSettings()`
- `components/PremiumCards.kt` — `ScPremiumNavCard` gained optional `subtitle` + `trailing` slots (drawer pages unchanged)
- `ShortsCapApp.kt`, `navigation/ScNavHost.kt` — wiring

---

# Study Mode (Monitoring section)

## What was added

**Study Mode** is a complete, connected study-focus feature living inside the **Monitoring settings section** (Settings → Monitoring → Study Mode). No new navigation item, no Journal section, and no duplicate Study Mode controls anywhere else in the app. It is fully separate from Device Monitoring, Shorts Monitoring, Activity and History data — it has its own `study/` package, its own models and its own `StudyRepository` backend seam.

## Study Mode screen (Monitoring → Study Mode)

| Section | Controls |
| --- | --- |
| Status | Active / Inactive + live remaining countdown while a session runs |
| Session | **Start Study Session** (pre-start confirmation) → while active the button is replaced by the countdown — **no Stop/Cancel during a session** (the only way to end early is the Focus Exit Passcode) |
| Focus Protection | **Focus Exit Passcode** — set/status row; the ONLY way to end an active session early (via the passcode verification screen) |
| Settings | Study Duration (15/25/30/45/60/90 min) · Break Reminder switch · Break Duration (3/5/10/15 min) · Sound Mode (Sound/Vibrate/Silent) |
| Study Schedule | Enabled switch + Start/End time pickers (configuration; future automation) |
| Allowed Apps/Websites | Dedicated page — each allowed app/website keeps its own independent switch + add-website by domain |
| Study Session Summary | Sessions today · study time today · last session (derived when sessions complete) |

## Session behavior (one connected system)

- **Start** → confirmation dialog clearly states: Study Mode stays active until the countdown reaches **00:00**, there is **no Stop/Cancel button** during a session (you can end early ONLY with the Focus Exit Passcode), and **Restricted Mode stays on until the timer finishes** (Shorts platforms stay restricted; YouTube, Google, Calculator, Gallery and the user's allowed apps/websites remain accessible).
- **Restricted Mode** is activated automatically: Strict Mode is forced ON for the session (the previous value is remembered), and while a session is active the user **cannot manually disable** Strict Mode or Monitoring — the ViewModel ignores those toggles until 00:00.
- **Timestamp-based countdown** — the session stores `startTimeMillis`, `endTimeMillis` and a ticking `currentTimeMillis` (remaining = end − current), so the timer stays exact when the app goes to the background or is reopened. A one-second ticker + an on-resume expiry check end the session at 00:00, restore the normal Strict Mode state and update the summary.
- **HOME** — while a session is active the existing circular analytics carousel leads with a dedicated **Study Mode page** ("Study Mode Active" + countdown ring + a reusable **Watch/Timer sweep-hand animation** with a small lock badge). The existing Watch Time / Shorts Count pages stay behind it, untouched, and return to the front automatically at 00:00.
- **Two exit locations, one shared state** — an active session can be ended early from **Home** (tap the active Study Mode circle) or from **Settings → Monitoring → Study Mode** (tap the active session card). Both open the same "Stop Study Mode?" confirmation, which routes into the **same** Focus Exit Passcode verification screen. There is exactly ONE `StudyModeState` (inactive / active + startTime / endTime / remaining) and ONE passcode + recovery system — Home and Study Mode always mirror each other; they can never disagree.

## Focus Exit Passcode (Study Mode protection & recovery)

A complete **Focus Exit Passcode** system lives inside Study Mode (Settings → Monitoring → Study Mode → **Focus Protection**). It controls ONLY the ability to manually end an active session before 00:00 — it never changes blocking settings, restriction configuration, Monitoring, Activity/History data or normal authentication, and natural completion at 00:00 never requires it.

- **Setup (first time)** — "Set Focus Exit Passcode": hidden passcode field with Show/Hide eye, **min 8 characters, no artificial maximum, no Forgot option**; success toast + return to Study Mode.
- **Protected exit (from both Home and Study Mode)** — an active session can only be ended early via the passcode. Tapping the active Study Mode card (Home circle or the Study Mode session card) shows "Stop Study Mode?" → **Stop Study Mode** → the shared **"Enter Focus Exit Passcode"** screen. A correct passcode ends the session immediately (countdown stops, restrictions restored, "Study Mode ended successfully."); an incorrect one keeps Study Mode fully active with a calm "Incorrect Focus Exit Passcode." error so the user can retry. If no passcode has been set yet, the flow routes to passcode setup first.
- **Recovery (Forgot Passcode?)** — only reachable from the verification screen: Recover → choose **Email** or **Mobile** (two separate cards, no assumption) → Send Verification Code → dedicated 6-digit OTP page (resend countdown; clearly says email or mobile, no sensitive data shown) → Create New Passcode (New + Confirm with eye toggles, min 8, must match) → returns to the verification screen so the new passcode works immediately.
- **Design** — a dedicated "Study Focus Protection & Recovery" visual identity (lock/focus icon, clean card, ShortsCap dark theme); it deliberately looks **nothing like** the Sign In / Sign Up / auth OTP screens.
- **Security / backend-ready** — the passcode is **never stored as plain text** (per-install random salt + SHA-256 hash, constant-time verification; the future backend uses a proper KDF). OTP is a LOCAL MOCK (random 6-digit code, 5-minute expiry, single-use) surfaced as a subtle "Demo code" line only until the backend sends it via email/SMS; the old passcode becomes invalid immediately after recovery.

## Future backend readiness

- `study/StudyModels.kt` — `StudyModeSettings`, `StudySchedule`, `StudySession` (sessionStartTime / sessionEndTime / currentTime / remainingDuration map 1:1 to a future API), `StudySummary`, allowed-items catalogs.
- `study/FocusPasscodeModels.kt`, `study/FocusPasscodeRepository.kt` (OTP request/resend/verify seams → `POST /focus-passcode/otp/*`), `study/FocusPasscodePreferenceStore.kt` (salted-hash storage).
- `study/StudyRepository.kt` — GET/PUT Study Settings, POST Study Session, GET Study Summary seams (mirrors the SettingsRepository pattern). Swapping local state for backend APIs requires **no UI changes**.
- Study Mode never touches `MonitoringSettings`, `ActivityRepository` or Web rules — the two systems stay independently extendable.

## Files

- `study/StudyModels.kt`, `study/StudyRepository.kt` — Study Mode module (models + backend seam)
- `study/FocusPasscodeModels.kt`, `study/FocusPasscodeRepository.kt`, `study/FocusPasscodePreferenceStore.kt` — Focus Exit Passcode module (recovery method, mock OTP seam, salted-hash storage)
- `screens/settings/StudyModeScreen.kt`, `screens/settings/StudyAllowedItemsScreen.kt` — **new** screens
- `screens/settings/FocusPasscodeScreens.kt` — **new** 7-screen Focus Exit Passcode flow (setup / verify / recover / email / mobile / OTP / create)
- `screens/settings/MonitoringScreen.kt` — Study Mode row as the third item inside the Monitoring section (relocated from the General section)
- `navigation/SettingsNavHost.kt` — `settings_study_mode` + `settings_study_allowed` routes
- `navigation/FocusPasscodeNavHost.kt` — **new** dedicated root-level overlay hosting ALL passcode screens (setup / verify / recover / email / mobile / OTP / create) so Home and Study Mode share the exact same flow
- `ShortsCapApp.kt` — renders the `FocusPasscodeNavHost` overlay above the settings overlay
- `components/CircularAnalytics.kt` — tappable Study Mode page injected into the Home carousel (countdown + reusable `ScStudyAnimation` Watch/Timer animation + lock badge; `StudyAnimationType` enum ready for future Book/Focus variants from Appearance)
- `screens/home/HomeScreen.kt` — active Study Mode circle opens "Stop Study Mode?" → Focus Exit Passcode
- `viewmodel/AppViewModel.kt` — `studySettings` / `activeStudySession` / `studySummary` + session lifecycle + Restricted Mode guards + passcode create/verify/update + mock OTP + `endStudySessionWithPasscode` + `focusPasscodeFlow` open/close overlay control
- `icons/` — `IconKey.STUDY_MODE`, `IconKey.FOCUS_PASSCODE`
- `i18n/` — full catalog (EN / HI / UR / ZH / ES) for the screens, dialogs, toasts and Home page

---

# Notifications Module

## What was added

Settings → **Notifications** is now a premium, scalable notification center. It is **not** a flat list of toggles: the main page shows the **6 notification categories** as premium rows (icon · title · chevron — **no subtitles**), and each category opens its **own dedicated page** where its options live as premium toggle cards (icon · title · description · switch). No expandable cards anywhere; every category is one tap deep from the hub.

## Notification Categories

| # | Category | Options |
| --- | --- | --- |
| 1 | **Reminder Notifications** | Daily Usage Reminder · Daily Screen Time Summary · Goal Achievement |
| 2 | **Limit Alerts** | Notify at 50% · Notify at 80% · Notify at 100% (e.g. "You've reached 80% of today's usage limit.") |
| 3 | **Block Notifications** | App Blocked Alert · Restriction Message (e.g. "Time to take a break.") |
| 4 | **Weekly Insights** | Weekly Progress Report · Weekly Achievement (e.g. "You reduced Shorts usage by 3 hours this week.") |
| 5 | **System Notifications** | Permission Reminder · Monitoring Stopped · Background Service Status |
| 6 | **Sound & Vibration** | Notification Sound · Vibration |

## Local Notification Architecture

- **Models** (`notifications/NotificationModels.kt`): `NotificationCategory` (6) and `NotificationSettingId` (15 options, each grouped to a category via its `category` field). Every option maps 1:1 to a future backend `GET /notifications/settings` entry.
- **State** (`AppViewModel`): `AppUiState.notificationSettings: List<NotificationSetting>` is the single source of truth; `toggleNotificationSetting(id, enabled)` updates it and persists immediately.
- **Local persistence** (`notifications/NotificationRepository.kt`): every option's on/off state is saved to SharedPreferences (`loadSettings` / `saveSettings` / `clearSettings`), matching the Theme/Language store pattern. Missing keys fall back to defaults, so new options added in a future release need **no migration**.
- **Defaults**: the three limit alerts and both weekly insights are opt-in (start disabled); everything else starts enabled.
- **Screens**: `NotificationsScreen` (hub) → `NotificationCategoryScreen` (dedicated page per category, rendered from the same enum — adding a category only touches the enum + i18n catalog).
- **i18n**: all titles/descriptions/examples live in the `AppStrings` catalog (EN / HI / UR / ZH / ES) — no hardcoded text.

## Backend Integration Ready

- Every `NotificationSetting` carries `id` (unique setting ID), `enabled` (current state), plus **future cloud-sync placeholder** (`cloudSyncEnabled`) and **future analytics placeholder** (`analyticsEvent`).
- **Repository seam**: `syncSettingsToCloud(...)` (POST /notifications/settings) and `trackNotificationAnalytics(...)` are documented placeholders — swapping SharedPreferences for backend APIs or a local Room cache requires **no UI changes**.
- **Navigation**: `settings_notification_category/{category}` route with typed `NotificationCategory` arg — deep-linking/predictive back works later with no restructure.
- `Reset All Settings` restores the notification prefs to their defaults (via the centralized `SettingsManager`).

## Cloud Sync Ready

The local SharedPreferences store is deliberately shaped like the future remote model: one key per `NotificationSettingId` with a boolean state. When the backend connects, `NotificationRepository.loadSettings`/`saveSettings` are replaced by the API calls behind the identical `NotificationSetting` shape — the UI, navigation and state management stay untouched.

## Files

- `notifications/NotificationModels.kt`, `notifications/NotificationRepository.kt` — Notifications module (models + local storage + backend seams)
- `screens/settings/NotificationsScreen.kt` — hub with the 6 category rows
- `screens/settings/NotificationCategoryScreen.kt` — dedicated option page per category
- `screens/settings/NotificationUi.kt` — shared helpers (icons, titles, descriptions)
- `navigation/SettingsNavHost.kt` — `NotificationsScreen` + `settings_notification_category/{category}` route
- `viewmodel/AppViewModel.kt` — `AppUiState.notificationSettings` + `toggleNotificationSetting()` + reset
- `i18n/` — full catalog (EN / HI / UR / ZH / ES) for the hub, categories and options

---

# Appearance Module

## What changed

Settings → **Appearance** is a premium hub with **2 rows** (icon · title · chevron, no subtitles, no intro cards), each opening its **own dedicated page**:

| # | Row | Dedicated page |
| --- | --- | --- |
| 1 | **Theme** | Dark / Light / System Default radio rows — selecting applies the theme immediately (persisted), **no Apply button** |
| 2 | **Text Size** | Small / Medium (Default) / Large radio rows — global **typography scale** for the entire application |

## Removed Icon Size feature

The **Icon Size** feature has been **completely removed** from the application: no `IconSizeMode`, no icon-size state or persistence, no `IconSizeScreen`, no route and no i18n keys. The global density override is gone — only typography is scaled now. The Appearance page contains only **Theme** and **Text Size**.

## Appearance module simplified

The module is now a single-concern page: Theme (visual identity) + Text Size (global typography). One shared generic `SizeOptionScreen` + `RadioOptionRow` power the Text Size page; no duplicated option UI.

## Theme

Options: ○ Dark, ○ Light, ○ System Default. Selecting any option immediately applies the theme app-wide (palette colors animate smoothly) and persists it across restarts via `ThemePreferenceStore`. No Apply button.

## Text Size (global typography scaling architecture) — remains globally supported

- **`appearance/AppearanceModels.kt`** — `TextSizeMode` (SMALL `0.9×`, MEDIUM `1.0×` default, LARGE `1.1×`).
- **How it works** — the root (`AppRootNavHost`) wraps the whole composition in a single `LocalDensity` override that multiplies only the **font scale** by the text-size factor, so every `sp` text size updates instantly — Dashboard, Settings, Profile, Monitoring, Notifications, Permissions, Help & Support, About ShortsCap, Privacy Policy, Terms & Conditions, the Web section and all future screens.
- **Only text changes** — the density itself is untouched, so **layouts, icons, cards and spacing remain unchanged**.
- Persisted locally via `AppearanceRepository` (SharedPreferences).

## Backend-ready

- **`appearance/AppearanceRepository.kt`** — the single data seam: `load/saveTextSizeMode` (local today), plus documented **future cloud-sync** (`syncAppearanceToCloud`) and **future analytics** (`trackAppearanceAnalytics`) placeholders. Stored as a **global user preference**; swapping to backend APIs requires **no UI changes**.
- `AppUiState` carries `textSizeMode`; it loads at startup and is restored by `resetAllSettings`.
- i18n catalog (EN / HI / UR / ZH / ES) covers the hub rows and the text-size labels.

## Files

- `appearance/AppearanceModels.kt`, `appearance/AppearanceRepository.kt` — Appearance module (`TextSizeMode`, local storage + backend seams)
- `screens/settings/AppearanceScreen.kt` — hub with the 2 rows
- `screens/settings/ThemeScreen.kt`, `TextSizeScreen.kt` — dedicated pages
- `screens/settings/AppearanceUi.kt` — shared `RadioOptionRow` + generic `SizeOptionScreen`
- `AppRootNavHost.kt` — the single global `LocalDensity` fontScale override (text only)
- `navigation/SettingsNavHost.kt` — routes: `settings_appearance_theme` / `_text_size`
- `viewmodel/AppViewModel.kt` — `textSizeMode` + setter + reset
- `i18n/` — full catalog for the module

## Removed Privacy section from Settings

The **Privacy** section has been **completely removed** from the Settings module — no menu row, no route (`settings_privacy`), no `SettingsDestination.PRIVACY`, no screen and no i18n keys (`settingsPrivacy`, `privacyTitle`). The final Settings list is now:

1. General
2. Monitoring
3. Permissions
4. Notifications
5. Appearance
6. Data Backup
7. About
8. Reset All Settings

## Privacy-related setting options removed entirely

The placeholder privacy options (**Data Collection**, **Export My Data**, **Clear Local Data**) had **no implemented functionality** in the codebase (the Privacy page was a Coming Soon placeholder), so they were **removed entirely rather than relocated** — no empty/placeholder rows were carried over into Data Backup. The Data Backup section remains its Coming Soon placeholder screen.

## Moved Privacy Policy and Terms & Conditions into About

**About** (Settings) now hosts the legal documents:

- **Privacy Policy** — opens the existing bundled `PrivacyPolicy.txt` via `LegalDocumentScreen` (no new document created)
- **Terms & Conditions** — opens the existing bundled `TermsConditions.txt` via `LegalDocumentScreen`

Both reuse the app's already-bundled local assets and the existing reader screen, with new routes (`settings_legal_document/{document}`) in `SettingsNavHost`. The Dashboard drawer's legal entries are unchanged.

## Reset All Settings redesigned

**Reset All Settings** no longer opens a screen. It stays the **last row** on the main Settings page and, when tapped, shows a premium in-place confirmation dialog.

## Removed unnecessary Reset screen

The dedicated `ResetAllScreen` and its route (`settings_reset_all`) have been **completely removed**: no `SettingsDestination.RESET_ALL`, no navigation, no file. `AppViewModel.resetAllSettings()` is now wired straight from the Settings home's dialog.

## Added confirmation dialog

`ResetAllSettingsDialog` — a premium dark dialog (24dp radius, dimmed scrim, scale + fade entrance, danger icon header) that is deliberately **not** Android's default `AlertDialog`. It shows the exact confirmation message and a single row of equal-width buttons:

- **Cancel** — outlined
- **Reset** — filled red (`colors.Danger`)

## Reset now restores only application settings

Confirmed reset routes through the centralized **`SettingsManager`** (`settings/SettingsManager.kt`) and restores **only application settings**:

- Theme → **System Default**
- Text Size → **Medium**
- Language → **English**
- Monitoring preferences → defaults
- Notification preferences (incl. permission reminders) → defaults
- Any future setting registered in `SettingsManager` → defaults

**Never touched:** user account, login session, profile information/picture, authentication tokens, monitoring history, backend data and cloud data.

After reset the Settings UI refreshes automatically and a success toast appears ("Settings have been restored to their default values.") — **no app restart required**.

## Backend-ready reset architecture prepared

- **`settings/SettingsManager.kt`** — single centralized authority for resettable settings: one default accessor per setting + `restoreDefaults(context)`. Future settings are registered once and are then included automatically in every reset.
- Local persistence (theme, language, appearance, notifications) is restored to defaults and persisted, so state stays consistent across restarts.
- Documented future seam: `SettingsManager.resetCloudSettings()` for backend / cloud preference resets.
- i18n catalog (EN / HI / UR / ZH / ES) covers the dialog message, the Reset button label and the success toast.

---

# Icon System — ShortsCap Original & Vibrant Colors *(newly implemented — Aug 7, 2026)*

A **centralized, app-wide icon system** was added to ShortsCap. Users pick an **icon style** under **Settings → Appearance → Icons**; the chosen style is applied **globally** — Dashboard, Activity, Web, Settings and every sub-page, the drawer, the profile screen, Help & Support, About ShortsCap and all future screens — with **no app restart** and **no state loss**.

## Icon Styles

| Style | Name | Description | Default? |
| --- | --- | --- | --- |
| `IconStyle.ORIGINAL` | **ShortsCap Original** | Clean blue-black icons designed for the ShortsCap interface — visually identical to the icons used before this system existed | ✅ Yes |
| `IconStyle.VIBRANT` | **Vibrant Colors** | Colorful category-based icons — every section (General, Monitoring, Permissions, Notifications, Appearance, Data Backup, About, …) has its own recognizable icon **and** color inside a tinted rounded-square container | No |

- The Vibrant palette is **ShortsCap's own original color language** (blue, cyan, teal, green, lime, amber, orange, red, pink, purple, violet, indigo, slate). It was inspired by the *concept* of colorful, category-specific, rounded-square icons only — **no third-party assets, icons, branding or colors are copied**. The icon shapes come from ShortsCap's existing Material-icon design language, with a few per-category swaps (e.g. Translate for Language, Policy for Privacy, QuestionAnswer for FAQ, Backup for Data Backup) so the two styles are visually distinct.
- Both styles honor the ShortsCap icon design rules: consistent stroke/shape language, consistent rounded-square container treatment, proper padding, no gradients, no tiny unreadable details, and icons stay crisp at small sizes.

## Icon Settings page (Settings → Appearance → Icons)

- **Premium selection cards**, not a radio list: each card shows a live mini icon strip, the style name, a one-line description and a **check indicator** on the selected card (subtle ShortsCap-blue border + tint) with the same soft press-scale animation as the rest of the app.
- A compact **PREVIEW** section shows General / Monitoring / Permissions / Notifications / Data Backup exactly as they will look under the pending style — it updates live while browsing.
- **Apply behavior is deliberate**: browsing cards does NOT change the app. **[ Cancel ]** discards, **[ Apply ]** persists the style, updates the global icon provider and returns to the previous page. Apply is **disabled** while the pending style already equals the active style. No restart is ever needed.
- Accessibility: selectable cards, labeled icons and check indicators (content descriptions), full touch targets, colors never used as the only signal (icons + labels always accompany color).

## Global icon architecture

| Concept | File | Role |
| --- | --- | --- |
| `IconKey` | `icons/IconModels.kt` | Semantic keys — the stable vocabulary screens use to request icons (e.g. `IconKey.MONITORING`, `IconKey.PERMISSIONS`, `IconKey.NOTIFICATIONS`, `IconKey.DATA_BACKUP`). One key per category/destination; ~80 keys cover navigation, Settings, Monitoring, Permissions, Notifications, Appearance, the drawer, Help & Support, About pages, Home stats and Profile |
| `IconStyle` | `icons/IconModels.kt` | The selectable style (ORIGINAL / VIBRANT); `IconStyle.DEFAULT` = ORIGINAL |
| `IconTheme` | `icons/IconTheme.kt` | The single manager/resolver: `icon(style, key)`, `tint(style, key, default)` + the per-category Vibrant palette |
| `LocalIconStyle` | `icons/IconTheme.kt` | App-wide CompositionLocal (mirrors `LocalScColors`) provided in `ShortsCapApp` from `AppUiState.iconStyle` |
| `IconRepository` | `icons/IconRepository.kt` | Local persistence (SharedPreferences) + documented future cloud-sync / analytics seams |
| `IconScreen` | `screens/settings/IconScreen.kt` | The dedicated Icon Settings page |

**How screens request icons:** shared components gained an `iconKey` parameter (`ScPremiumNavCard`, `ScPremiumInfoCard`, `ScStatCard`, `ScEmptyState`, `ScSettingsListItem`, `StatTile`); bottom-nav items, drawer items and settings rows now carry `IconKey` instead of hardcoded `ImageVector`s. Screens never resolve vectors or colors themselves — they pass a key and the centralized system decides (e.g. `ScPremiumNavCard(iconKey = IconKey.MONITORING, …)`).

**Global application integration (at minimum):**

- **Dashboard** — Home Quick Stats cards, bottom navigation (Home / Activity / Web / Settings)
- **Three-dot drawer** — Help & Support, Privacy Policy, Terms & Conditions, About ShortsCap, Feedback, Share App
- **Profile** — profile field icons (person / email / lock / calendar)
- **Settings** — General, Monitoring, Permissions, Notifications, Appearance, Data Backup, About, Reset All Settings
- **General** — Language
- **Monitoring** — Device Monitoring, Strict Mode, Shorts Control, Monitoring Schedule
- **Permissions** — all 7 permission rows + detail page hero (each row keeps its recognizable icon and gains its own color in Vibrant)
- **Notifications** — the 6 categories + every option row (options inherit their category color in Vibrant)
- **Appearance** — Theme, **Icons**, Text Size
- **Help & Support** — FAQ, Contact Support, Report a Bug (+ FAQ accordion tiles)
- **About ShortsCap** — About, Features, Technologies, Version & Build, Copyright (+ their content pages)
- **Web / Activity / empty states** — Blocked / Allowed / Schedule empty-state icons, Web empty state

**Any future screen** can use the system by passing an `IconKey`; new categories are one enum entry + one mapping, and new styles (e.g. `IconStyle.MINIMAL`, `COLORFUL`, `FUTURISTIC`, `CUSTOM`) are one enum entry + branches in `IconTheme` — **no screen changes required**.

## Persistence behavior

- The selection is stored locally in SharedPreferences (`IconRepository`, prefs `shortscap_icons`, key `icon_style`) and restored on launch (`AppUiState.iconStyle` loads at startup, so the very first frame is correct).
- The style **survives app restarts** and **logout/login** (it is a local application preference, not session data).
- Defaults to **ShortsCap Original** on first install and whenever no preference has been stored.

## Reset All Settings behavior

`Settings → Reset All Settings` restores the icon style to **ShortsCap Original** together with every other default (Theme → System Default, Text Size → Medium, Language → English, Monitoring/Notification preferences → defaults) via the centralized `SettingsManager` (`defaultIconStyle()` + one line in `restoreDefaults()`). No separate reset page exists for icons.

## Dark / Light compatibility

- The icon system is **theme-agnostic**: both styles render each icon inside the same compact **neutral charcoal container** (`CardHover` — slightly lighter than the card surface in Dark, a soft light-gray in Light); only the icon color differs (accent vs per-category), so contrast stays correct on Dark and Light surfaces.
- Both styles work under Dark, Light and System Default (existing `ThemeMode` / `ShortsCapTheme` untouched); text and icon contrast remain sufficient in both modes.
- Nothing in the Theme, Text Size, Language, Navigation, Auth, Monitoring, Permission, Notification or backend layers was modified — the icon system integrates cleanly alongside them.

## Future backend synchronization readiness

- `IconRepository.syncIconStyleToCloud(style)` and `trackIconStyleAnalytics(style)` are documented placeholders (not implemented — no backend exists yet).
- Future backend storage: `user_id`, `selected_icon_style`, `updated_at`. Swapping SharedPreferences for the API requires **no UI changes** — `loadIconStyle`/`saveIconStyle` are the only data touch-points, matching the `ThemePreferenceStore` / `AppearanceRepository` pattern.

## Files

- `icons/IconModels.kt`, `icons/IconTheme.kt`, `icons/IconRepository.kt` — **new** Icon System core (styles, keys, resolver, palette, persistence, cloud seams)
- `screens/settings/IconScreen.kt` — **new** dedicated Icon Settings page
- `screens/settings/AppearanceScreen.kt` — **Icons** row added (Theme · Icons · Text Size)
- `navigation/SettingsNavHost.kt` — **new** `settings_appearance_icons` route (`Settings → Appearance → Icons`)
- `viewmodel/AppViewModel.kt` — `AppUiState.iconStyle` + `setIconStyle()` + reset integration
- `settings/SettingsManager.kt` — `defaultIconStyle()` + restore registration
- `components/PremiumCards.kt`, `components/CommonComponents.kt`, `components/BottomNavBar.kt`, `components/AppDrawer.kt`, `model/Models.kt` — shared icon-key-aware components
- Every screen listed under *Global application integration* — icons routed through `IconKey`/`IconTheme`
- `i18n/` — new catalog keys (EN / HI / UR / ZH / ES) for the Icons page
- This `README.md` — this section (appended; all existing content preserved)

*Implemented August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

---

# Icon System — Compact Proportions Redesign *(newly implemented — Aug 7, 2026)*

Following a visual review, the icon system was **redesigned from large colored icon boxes to a compact, premium icon treatment**. The previous version rendered big tinted containers with oversized icons that dominated the screen; the corrected system uses **small neutral containers with colorful icons inside**, short cards, and refined category colors. This is a pure **visual/UI correction** — no functionality, navigation, persistence, theme, language or backend behavior changed.

## New compact proportions (applied globally)

| Element | Before | After |
| --- | --- | --- |
| Row icon container | 60dp | **44dp** (rounded-square, 13dp radius) |
| Row icon | 36dp | **24dp** (icon ≈ **55%** of the container, generous internal padding) |
| Settings card height | ~96dp | **~70dp** (13dp vertical padding, 16dp horizontal) |
| Detail heroes (permission detail, copyright) | 64–72dp / 34–36dp | **52–56dp / 28dp** |
| Empty-state hero | 64dp / 26dp | **56dp / 28dp** |
| Home stat tiles | 34dp / 17dp | **36dp / 20dp** |
| Card shadows | resting 3dp / pressed 14dp | **resting 2dp / pressed 12dp** (subtler, no glow) |

Visual hierarchy is now: **1) title → 2) icon → 3) chevron** (icon is a small accent, never the dominant element), and more settings fit on screen without scrolling.

## Color treatment — color belongs to the ICON, not the container

- **Both styles** use the same compact, **neutral dark/subtle charcoal container** (`CardHover`: slightly lighter than the card in Dark, a soft light-gray surface in Light).
- The **category color lives on the icon itself** — e.g. dark rounded container + blue eye icon (Monitoring), dark container + green shield icon (Permissions), dark container + pink bell icon (Notifications), dark container + purple palette icon (Appearance). No more large saturated colored panels.
- The centralized `IconTheme` intentionally has **no container resolver** — the neutral container is shared by every style so layout dimensions can never diverge; a future style that needs its own container can add one seam without touching screens.

## Refined category color system (Vibrant Colors)

Tasteful, non-neon shades matched to the recommended mapping:

| Category | Color |
| --- | --- |
| General | Purple |
| Monitoring | Blue |
| Permissions | Green |
| Notifications | Pink/Red |
| Appearance | Purple |
| Data Backup | Cyan |
| About | Blue/Violet |
| Help & Support | Blue |
| Feedback | Orange |
| Share | Cyan/Blue |
| Language | Violet |

## Both styles keep the compact proportions

- **ShortsCap Original** — every icon in the ShortsCap accent blue, compact neutral tiles.
- **Vibrant Colors** — the same compact tiles; only the icon treatment differs (per-category color + a few distinctive icon swaps).
- The **difference between styles is the visual icon treatment, never layout dimensions** — switching styles does not change row heights, spacing or tile sizes.

## Global application

The corrected proportions apply everywhere: Settings (all rows), Monitoring, Permissions (+ detail page), Notifications, Appearance, General, Language, Data Backup, About (+ all sub-pages), Help & Support, FAQ, Contact Support, Report a Bug, Profile, Dashboard (Home stat cards), Web empty states, the three-dot drawer and the Icon Settings page itself. All icons continue to flow through the **centralized `IconKey` / `IconTheme` architecture** — no screen hardcodes its own dimensions.

## Dark / Light / System Default compatibility

- **Dark:** very dark charcoal cards, slightly lighter charcoal icon containers, category-accent icons, very subtle 1dp borders — no glow, no neon.
- **Light:** the same hierarchy with a soft light-gray container (`CardHover` #F0F0F2) on white cards and adjusted icon contrast; the category colors remain readable.
- System Default follows the existing `ThemeMode` unchanged; the theme system was not modified except removing the now-unused `StatIconBg` token.

## Responsive sizing

All dimensions are standard `dp` values (44dp tiles, 24dp icons, 13dp padding) derived from the existing design conventions, so the system scales consistently across small and large Android screens; touch targets stay comfortable (70dp+ rows).

## Files touched (visual correction only)

- `icons/IconTheme.kt` — neutral-container model + refined palette (Share→cyan, Language→violet)
- `components/PremiumCards.kt` — compact 44dp/24dp `PremiumIconTile`, 13dp padding, subtler shadows
- `components/CommonComponents.kt` — compact `ScStatCard` (36/20), `ScEmptyState` (56/28)
- `screens/settings/MonitoringScreen.kt`, `PermissionsScreen.kt`, `PermissionDetailScreen.kt`, `IconScreen.kt` — neutral containers / compact heroes
- `screens/about/CopyrightScreen.kt`, `screens/help/FaqScreen.kt`, `screens/help/ReportBugScreen.kt` — neutral containers / compact tiles
- `theme/Color.kt`, `theme/Theme.kt` — removed the now-unused `StatIconBg` token
- This `README.md` — this section (appended; all existing content preserved)

*Redesign completed August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

---

## Android Launcher Icon — Official ShortsCap Logo (NEW)

> Implementation completed **August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801**.

### What changed

The generic **Android Studio template launcher icon** (green grid background + robot silhouette, `ic_launcher*.webp` at 5 densities, non-versioned `mipmap-anydpi` adaptive XMLs) was **removed** and replaced with the **official ShortsCap logo** as the launcher/app icon — shown on the home screen, app drawer, recent shortcuts, and the Android App Info screen.

- **Existing logo reused** — the launcher icon is generated from the project's own `res/drawable/logo_pic.png` (blue speech-bubble + red play mark). The logo itself was **not redesigned, redrawn or altered**; its identity and proportions are preserved exactly. The in-app logo placements (Splash, Dashboard, Profile, About, auth screens, TopBar, drawer) are **unchanged**.
- **Android Adaptive Icon implemented** — `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` with:
  - `background` → `@drawable/ic_launcher_background` (solid brand black `#000000`, matching the logo tile)
  - `foreground` → `@mipmap/ic_launcher_foreground` (432px PNG, logo scaled into the 66dp safe zone)
  - `monochrome` → `@mipmap/ic_launcher_monochrome` (white silhouette for Android 13+ themed icons)
- **Safe-area handling** — the logo artwork occupies ≈ 49% × 58% of the canvas, keeping every element inside the **66dp adaptive-icon safe zone**, so it is never clipped or cropped by launcher masks (circle, rounded square, squircle, Samsung/Xiaomi/OEM shapes) and is not stretched, rotated or distorted.
- **Legacy fallback maintained** — proper `ic_launcher.png` / `ic_launcher_round.png` PNGs at all five densities (`mipmap-mdpi` 48px → `xxxhdpi` 192px) cover pre-API-26 launchers and remain as a safety net (current `minSdk = 26` uses the adaptive icon on all supported devices).
- **Manifest** — `AndroidManifest.xml` sets `android:icon="@mipmap/ic_launcher"` and now also `android:roundIcon="@mipmap/ic_launcher_round"`. No other manifest/Gradle changes were made.
- **Default template icons removed** — the old `mipmap-anydpi/` folder, `drawable/ic_launcher_foreground.xml`, and all template `ic_launcher*.webp` density files were deleted; nothing else in the project referenced them.
- **Verified** — `./gradlew :app:assembleDebug` builds successfully with no resource-linking errors; the APK was inspected and contains the adaptive XMLs, all density PNGs, the foreground and the monochrome layer.

### Asset generation (for reproducibility)

The launcher assets were produced from `drawable/logo_pic.png` with a short Python/PIL routine: extract the logo content (blue-dominant OR red-dominant OR luminance > 150 mask), crop to its content bounding box, scale the longer side to ~58.5% of the canvas (inside the safe zone), center it on a transparent 432px foreground; legacy icons use the same composition on solid `#000000`; the monochrome is the same silhouette in white. If the logo ever changes, regenerate all PNGs with the same parameters for a consistent result.

### Icon caching note

Android launchers can cache app icons. If the old default icon still appears after reinstalling the APK, **uninstall the previous build and install the new one** — the launcher will then pick up the ShortsCap logo.

*Launcher icon replaced August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

---

## Removed: Settings → Data Backup & Dashboard menu → About ShortsCap (NEW)

> Implementation completed **August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801**.

### 1. Settings → "Data Backup" removed

- The **Data Backup** row was removed from the main Settings screen (`SettingsScreen.kt`); the remaining rows render identically (the list is data-driven, so no empty space or broken item is left behind).
- Its navigation was removed end-to-end: the `SettingsDestination.DATA_BACKUP` enum entry, the `settings_data_backup` route constant, the `startRoute()` mapping and the dedicated composable route in `SettingsNavHost.kt` are all gone, along with the now-unused generic section screen (`SettingsSectionScreen.kt`).
- No other Settings option was modified.

### 2. Dashboard three-dot menu → "About ShortsCap" removed

- The **About** item was removed from the drawer/three-dot menu (`ShortsCapApp.kt`): the `DrawerItem("about", …)` entry and its click handler are gone, so no unused menu item remains.
- Its navigation/window/page was removed end-to-end: the `DrawerScreen.ABOUT_SHORTSCAP` enum entry and all About routes (`about_shortscap`, `about_info`, `features`, `technologies`, `version_build`, `copyright`) plus their composable blocks were removed from `DrawerNavHost.kt`, and the six About screen files under `screens/about/` were deleted.
- The other three-dot menu options (Help & Support, Privacy Policy, Terms & Conditions, Feedback, Share App) are unchanged.

### Notes

- The **Settings → About** screen (Privacy Policy / Terms & Conditions) is a separate feature and was **not** touched.
- The **Icon Settings preview** still lists "Data Backup" as one of its style-demo categories (`IconKey.DATA_BACKUP` + its label string were intentionally kept — the preview has no navigation).
- Unused string keys (`dataBackupTitle`, `drawerAbout`) and the About-related icon keys remain declared in the language/icon catalogs to keep those systems untouched; they are harmless and can be cleaned up later if desired.
- Verified: `:app:compileDebugKotlin` and `:app:test` both pass.

*Feature removals August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

---

## Web Section Redesign — Dedicated Analytics + Website Rule Screens (NEW)

> Implementation completed **August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801**.

### What changed

The old inline Web screen (tabs + switch list inside one page) was replaced with a **dedicated three-screen Web section** driven by a clean **data / model / repository** architecture. Only the Web section was touched — navigation, auth, monitoring, settings and all other features are unchanged.

- **Web tab → Web Usage Analytics** (`WebAnalyticsScreen`) — exclusively website usage (no app/Android usage):
  - Large **donut chart** with one proportional segment per website (animated sweep), the **total usage in the center**
  - **Today / Week / Month** period selector — Today = today's usage, Week = 7-day summary, Month = monthly summary
  - A **trend bar chart** below the donut (last-7-days bars for Today/Week, four weekly buckets for Month)
  - **Website-wise breakdown** list: website icon, name, domain, duration and percentage
  - **Website rules** cards at the bottom open the dedicated Blocked / Allowed screens
- **Blocked Websites** (`WebBlockedScreen`) — full blocked list (icon + name + domain), **Unblock** and **Delete** actions, **search**, **Add Website** (with Block/Allow choice and inline domain validation), and a polished **empty state**
- **Allowed Websites** (`WebAllowedScreen`) — full allowed list with **Block** and **Remove** actions (easy Blocked ⇄ Allowed switching), search, Add Website, empty state
- **Navigation** — the Web tab is now a small NavHost (`navigation/WebNavHost.kt`): analytics root + Blocked/Allowed sub-screens with proper back-stack behavior (system Back pops sub-screens; at the analytics root it behaves like every other tab)

### Future-ready architecture

- **No hardcoded data in the UI.** New `web/WebModels.kt` + `web/WebRepository.kt` own all data:
  - **Website rules** — `WebRule` (domain, display name, status `BLOCKED`/`ALLOWED`, created time, updated time)
  - **Analytics** — `WebUsageRecord` (domain, display name, usage duration, date) + `WebAnalyticsSummary` (period, total, per-website items with percentage, trend points)
- `WebRepository.analyticsSummary()` is a **pure aggregation function** — it works identically with today's deterministic seed data and future backend/database records; documented seams (`fetchRulesFromBackend`, `syncRuleToCloud`, `fetchUsageFromBackend`, `trackWebsiteVisit`) are ready but not implemented.
- **No fake detection is claimed** — Shorts/website usage is demo data; a future browser / VPN / accessibility-based tracking mechanism will insert real records of the same shape without any UI changes.
- All state flows through `AppViewModel` (`webRules`, `webUsageRecords`, `webPeriod` + `addWebRule` / `setWebRuleStatus` / `removeWebRule` / `setWebPeriod`), fully backend-ready.
- New screens use the **centralized icon system** (`IconKey.WEB_ANALYTICS / WEB_BLOCKED / WEB_ALLOWED`), the **5-language string catalog** (35 new keys), and the shared premium components; dark/light/system themes, responsive sizing and navigation safe areas are respected (Web content inherits the tab container's navigation-bar padding).

### Files

- New: `web/WebModels.kt`, `web/WebRepository.kt`, `navigation/WebNavHost.kt`, `screens/web/{WebAnalyticsScreen,WebBlockedScreen,WebAllowedScreen,WebComponents}.kt`
- Modified: `viewmodel/AppViewModel.kt`, `navigation/ScNavHost.kt`, `model/Models.kt` (removed obsolete `SiteEntry`/`WebTab`), `icons/IconModels.kt` + `icons/IconTheme.kt` (3 new keys), `i18n/*` (35 new keys × 5 languages)
- Removed: `screens/web/WebScreen.kt` (old inline implementation)
- Verified: `:app:compileDebugKotlin` and `:app:test` both pass.

*Web section redesign completed August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

---

## UPDATE — Web Section Restructured: Website Blocking is the Primary Feature (August 7, 2026)

Following review, the Web tab was restructured to preserve its **original purpose**: **website blocking is the primary feature**, and web usage analytics is now a **secondary screen** that opens only from the Web Time card.

### New hierarchy

```
MAIN WEB PAGE  (Website Blocking & Management)
├─ URL input + Block Website        ← primary action, at the top
├─ Overview cards: Blocked / Allowed / Web Time (all tappable)
└─ Nav chips: Blocked / Allowed / Recent
        ↓
Web Time card → WEB USAGE ANALYTICS (dedicated screen)
Blocked       → Blocked Websites screen
Allowed       → Allowed Websites screen
Recent        → Recent Websites screen
Add Website   → dialog with Block/Allow choice
```

### Main Web screen — Website Blocking hub (`screens/web/WebBlockingScreen.kt`)

- **URL input + Block Website** remains the primary, prominent action at the top of the tab — now backed by a **3-layer local domain-verification pipeline** (`DomainNormalizer` → `DomainValidator` → `DomainVerifier`) so only real, reachable domains can be blocked (see *Website Domain Verification* below).
- Blocking an **allowed** website flips it to blocked (upsert — no duplicate row); already-blocked domains are reported inline.
- **Three compact overview cards** near the top: Blocked count, Allowed count, and **Web Time** (today's total usage, clickable → opens analytics). All three cards are tappable and navigate.
- **Blocked / Allowed / Recent** chips open their own dedicated screens — nothing expands inline on the main page.

### Dedicated secondary screens

- **Blocked Websites** — icon + name + domain rows with status pill, **Unblock** + **Delete** (confirm dialog), **search**, **Add Website**, polished empty state.
- **Allowed Websites** — same layout with **Block** + **Remove**, so websites switch between Allowed ⇄ Blocked easily.
- **Recent Websites** — most recently added/modified rules (sorted by `updatedAt`), **no usage time** shown.
- **Web Usage Analytics** (`WebAnalyticsScreen.kt`) — opens **only** from the Web Time card: back bar, "Today's Web Usage" headline, large proportional **donut chart** with total + period in the center, website-wise breakdown (icon, name, domain, duration, percentage), and **Today / Week / Month** periods with a trend bar chart for Week/Month. No Android app activity is shown — website usage only.

### Navigation & back behavior

- `WebNavHost` now routes: `BLOCKING` (root) → `BLOCKED` / `ALLOWED` / `RECENT` / `ANALYTICS`, with slide/fade transitions.
- System Back pops every secondary screen back to the main Web page; at the blocking root it behaves like every other bottom tab.
- The URL input survives back-stack trips (`rememberSaveable`) so a partially typed URL is not lost.

### Rules & analytics stay separated (backend-ready)

- **Website rules** — `WebRule` (domain, display name, status `BLOCKED`/`ALLOWED`, created/updated time)
- **Analytics** — `WebUsageRecord` (domain, display name, duration, date) + `WebAnalyticsSummary` (period, total, per-website items, trend) — all consumed from `WebRepository` via `AppViewModel`; no hardcoded UI data, no fake Shorts detection claimed.

### Files

- New: `screens/web/WebBlockingScreen.kt`, `screens/web/WebRecentScreen.kt`
- Reworked: `screens/web/WebAnalyticsScreen.kt` (secondary page — back bar, headline, period chips, conditional trend chart; rule cards removed), `navigation/WebNavHost.kt` (blocking root + 4 routes), `screens/web/WebComponents.kt` (`normalizeWebDomain` / `isValidWebDomain`, `WebOverviewStat`, status pill in `WebRuleRow`), `viewmodel/AppViewModel.kt` (upsert-style `blockWebsite`)
- Updated: `screens/web/WebBlockedScreen.kt` + `WebAllowedScreen.kt` (status pill + screen-specific search placeholders), `i18n/*` (13 new keys + retitled analytics title and dialog action across all 5 languages)
- Verified: `:app:compileDebugKotlin` and `:app:test` both pass.

*Web section restructure completed August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

---

## UPDATE — Automatic Website Favicon System + Real-Blocking Readiness (August 7, 2026)

Added an automatic **website identity / favicon system** to the Web section (Blocked, Allowed, Recent and the Web Usage breakdown) and made the website-rule architecture ready for a **real blocking engine** and future backend sync. No Web page structure or navigation was changed.

### Website favicon system (`favicon/` package — reusable app-wide)

- **Automatic for ANY domain** — nothing is hardcoded. When a website is added (e.g. `youtube.com`, `reddit.com`, `x.com`), the app normalizes the URL/domain, extracts the main domain (subdomains like `m.youtube.com` resolve to `youtube.com` for the logo lookup), identifies a display name, and retrieves the site's official favicon.
- **`FaviconRepository`** — resolution order: in-memory `LruCache` → disk cache (`cacheDir/favicons`, PNG) → network. Network tries the site's official `https://<domain>/favicon.ico` first, then a public favicon lookup service as fallback. Includes:
  - short connect/read timeouts + a 512 KB response cap (hostile/broken domains cannot exhaust memory)
  - failure tracking with a **10-minute TTL** so transient network blips self-heal (the globe fallback is not pinned for the session)
  - `refresh()` (cache refresh/update) and `clearCache()` (reset)
- **`WebsiteFavicon` composable** — loading state and professional fallback built in: the globe tile shows while loading and whenever the favicon is unavailable/invalid/offline, so the UI never breaks. Loaded logos render on a soft white tile (favicons are designed for light backgrounds) that reads correctly in dark, light and system themes.
- **Display name identification** — `WebRepository.displayNameFor()` uses known-domain hints (metadata only, never icons) with automatic derivation for unknown domains.

### Data model (future backend/database ready)

- `WebRule` now carries **website identity references only**: `faviconUrl` (primary favicon URL candidate) and `localIconPath` (favicon cache key = normalized domain). **Image bytes are never stored** in the model — pixels always come from the local favicon cache, so a future backend can sync rules + favicon references without transporting image data.
- Moving a website Blocked ⇄ Allowed keeps the same identity and cached icon — it is never re-downloaded.
- `AppViewModel.addWebRule()` populates the identity fields automatically for every new website.

### Real-blocking readiness (`web/BlockingEngine.kt`)

- New `WebsiteBlockingEngine` interface — `applyBlock` / `removeBlock` / `isBlocked` / `isAvailable` — the modular seam for a future Android-supported mechanism (VPN / DNS-based domain filtering, local proxy, accessibility-driven blocker).
- `PlaceholderBlockingEngine` is wired into the ViewModel (`pushRuleToEngine` on add / status change / remove). It performs **NO network filtering** (`isAvailable = false`) and fails loudly instead of silently succeeding — the app **never claims the current UI can block websites**; the BLOCKED/ALLOWED state is a local rule list only.
- Connecting a real engine later requires swapping one field in `AppViewModel` — zero Web UI or data-model changes.

### Other changes

- `AndroidManifest.xml`: added the `INTERNET` permission (favicon downloads + website domain verification) and `ACCESS_NETWORK_STATE` (instant offline detection for the verifier).
- `components/EntityIcon.kt`: website entities now render through the favicon system app-wide (any future list picks it up automatically).

### Files

- New: `favicon/FaviconRepository.kt`, `favicon/WebsiteFavicon.kt`, `web/BlockingEngine.kt`
- Modified: `web/WebModels.kt` (`WebRule` + `faviconUrl`/`localIconPath`), `web/WebRepository.kt` (seed identity refs + `displayNameFor`), `viewmodel/AppViewModel.kt` (identity on add + engine push), `components/EntityIcon.kt`, `AndroidManifest.xml`
- Verified: `:app:compileDebugKotlin` and `:app:test` both pass.

*Favicon system + blocking readiness completed August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

---

## UPDATE — Website Domain Verification before Blocking (August 11, 2026)

The main Web screen's **Block Website** action is now gated by a **local, on-device domain verification pipeline** — no backend, no API, no new services. A domain must prove it exists before it can be added to the blocklist.

### Three-layer verification (`web/` package)

1. **Layer 1 — Format validation (`DomainNormalizer` + `DomainValidator`)**
   - `DomainNormalizer.normalize()` extracts a bare hostname: strips scheme (http/https only), repeated `www.`, path/query/fragment and trailing FQDN dots; rejects e-mails (`hello@youtube.com`) and unsupported schemes (`ftp://`). `youtube.com` / `www.youtube.com` / `https://youtube.com/` all → `youtube.com`.
   - `DomainValidator.isValidDomain()` — synchronous regex check; `youtube..com` and random text fail here with **✕ Invalid website address**, no network is touched.
2. **Layer 2 — DNS lookup (`DomainVerifier`)**
   - `InetAddress.getAllByName()` on `Dispatchers.IO` with a 4 s timeout. NXDOMAIN → **✕ Domain not found**; a DNS timeout/transient failure is never treated as proof of non-existence.
3. **Layer 3 — Lightweight reachability (`DomainVerifier`)**
   - A single HEAD request (HTTPS first, HTTP fallback, short timeouts, redirects followed). *Any* HTTP status (even 4xx/5xx) proves the host answers → **✓ Domain verified**. Connection/SSL/timeout failures → **⚠ Could not verify website right now** (blocked until verification succeeds).

### UI behavior (`screens/web/WebBlockingScreen.kt`)

- The **Block Website button is disabled** until the current input is verified — it stays disabled for empty, invalid, unresolved and temporarily-unverifiable input.
- A live status line under the field shows `Checking domain...` → `✓ Domain verified` / `✕ Invalid website address` / `✕ Domain not found` / `⚠ Could not verify website right now`, using the app's existing Success/Danger/Warning colors.
- **Re-verification on every change**: editing the URL cancels the previous check (450 ms debounce avoids a DNS lookup per keystroke) and resets the state — a stale "verified" can never carry over to a different domain.
- Offline devices get **⚠ Could not verify website right now** immediately via a `ConnectivityManager` pre-check (no DNS-timeout wait).
- Blocking still uses the existing `blockWebsite` upsert + duplicate handling; the blocking engine, rule storage and all other Web screens are untouched.

### Files

- New: `web/DomainNormalizer.kt`, `web/DomainValidator.kt`, `web/DomainVerifier.kt`
- Modified: `screens/web/WebBlockingScreen.kt` (verification state machine + disabled-until-verified button), `AndroidManifest.xml` (`ACCESS_NETWORK_STATE`), i18n catalogs (5 keys × 6 languages)
- Verified: `:app:compileDebugKotlin` passes.

*Website domain verification completed August 11, 2026*

## Global Chart Style System (`Settings → Appearance → Chart`) — New

A single, app-wide Chart Style preference controls how every supported analytics chart in ShortsCap visualizes data. This is a **presentation-only** preference: the underlying usage data is never recalculated, filtered, or changed by the selected style (DATA ≠ VISUALIZATION).

### Chart Styles
- **Bar Chart** — distributions drawn as proportional bars.
- **Circular Chart** — distributions drawn as circular/donut charts (the default).

One preference drives Activity, Web Usage Analytics, weekly/monthly reports and any future analytics screen — there are **no per-screen chart settings**.

### Selection & Apply behavior
- `Settings → Appearance → Chart` opens a dedicated page with two radio options (each with a mini chart preview).
- Apply persists the choice locally, closes the page and returns to Appearance; the Chart row shows the current style as its summary. Apply is disabled when the selection is unchanged (no repeated confirmations).

### Global application
- **Activity**: the Usage Timeline card renders per the style — BAR shows Mon–Sun bars for Weekly (per-app bars for Daily/Monthly, never a Mon–Sun chart for Daily), CIRCULAR shows a donut with the total in the center. The Most Used Apps card uses the same renderer. Data identical either way.
- **Web Usage Analytics**: the distribution card renders the same aggregation as a donut (CIRCULAR) or proportional bars (BAR); the weekly/monthly trend chart is unchanged.

### Architecture
- `charts/ChartModels.kt` — `ChartStyle` (BAR / CIRCULAR, default CIRCULAR) + `ChartSlice` pure data model.
- `charts/ChartRenderer.kt` — `ScDistributionChart`: one renderer, same slices, two visualizations (donut / bars).
- Preference layer: persisted by `AppearanceRepository` (SharedPreferences, `chart_style`), default + reset via `SettingsManager.defaultChartStyle()` / `restoreDefaults()`, state in `AppUiState.chartStyle` (`AppViewModel.setChartStyle`).
- Backend-ready: usage records never store "bar" or "circular"; a future backend provides data only, and the chart preference syncs as a separate user preference (e.g. `UserPreferences.chartStyle`).
- Reset All Settings restores Chart to Circular Chart along with all other defaults. Dark / Light / System themes unaffected.

- New: `charts/ChartModels.kt`, `charts/ChartRenderer.kt`, `screens/settings/ChartScreen.kt`
- Modified: `appearance/AppearanceRepository.kt`, `settings/SettingsManager.kt`, `viewmodel/AppViewModel.kt`, `icons/IconModels.kt` + `icons/IconTheme.kt` (`IconKey.CHART`), `screens/settings/AppearanceScreen.kt`, `navigation/SettingsNavHost.kt`, `screens/activity/ActivityScreen.kt`, `screens/web/WebAnalyticsScreen.kt`, `navigation/ScNavHost.kt`, `navigation/WebNavHost.kt`, i18n catalogs (3 keys × 5 languages)
- Verified: `:app:compileDebugKotlin` and `:app:test` both pass.

*Global Chart Style system completed August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

---

## Activity & Reports Update — Period-Driven Charts + Dedicated Reports (New)

### Period-driven chart data
- The Daily | Weekly | Monthly tabs are unchanged; the chart now shows the data for the **selected period** — each period has its OWN dataset (never reused):
  - **Daily** = today's hourly usage series.
  - **Weekly** = the Mon–Sun day-by-day series (the values the page always showed).
  - **Monthly** = the day-by-day series for the month.
- All values flow from the new `activity/` data layer (`ActivityModels.kt` + `ActivityRepository.kt`) — a single structured `ActivityReport` shape per period with a documented backend seam (`fetchReportFromBackend`). No per-screen fake data; a future backend fills the same shape with zero UI changes.

### Thin, consistent bar design
- Bars are now significantly thinner (~30% of their slot) with consistent width, spacing, pill-top corner radius, alignment and 10sp labels — across Daily, Weekly, Monthly, Activity and Reports. Long series use sparse labels (`labelEvery`) so 30-day months stay readable.
- Responsive: bars scale to the available width; no clipping or overlapping on small/large screens.

### Chart style applies to Activity + Reports
- The global chart style (Settings → Appearance → Chart) drives the Activity timeline chart **and** the report charts (thin bars or circular/donut). One centralized preference — no per-screen logic.
- Changing the style never touches the underlying activity data.

### Dedicated Weekly / Monthly Report screens
- Tapping **Weekly Report** (and **Monthly Report**) now opens a dedicated full report screen instead of expanding inline.
- The report shows the period chart in the selected chart style, plus a summary derived from the same structured data: total usage, busiest day, most-used app, trend %, and Shorts usage/count (derived until real tracking) — all from `ActivityRepository.reportFor(period)`, nothing hardcoded.
- Back navigation (in-app arrow and system Back) returns to the Activity page.

### Architecture / data separation
- `activity/ActivityModels.kt` — `ActivityPeriod`, `ActivityPoint`, `ActivitySlice`, `ActivityReport` (data only, no presentation fields).
- `activity/ActivityRepository.kt` — deterministic per-period seeds + future backend seam.
- Charts consume the structured report; the renderer (`ScDistributionChart` in the charts package) only visualizes it.
- Cleanup: removed the old `DayUsage`/`WeekData`/`AppUsageSlice` models (moved behind the repository) and the inline report-expansion state (`expandedReport`/`toggleReport` → `activityReport` + `open/closeActivityReport`).

- New: `activity/ActivityModels.kt`, `activity/ActivityRepository.kt`, `screens/activity/ActivityReportScreen.kt`
- Modified: `charts/ChartRenderer.kt` (thin bars + `labelEvery`), `screens/activity/ActivityScreen.kt` (period-driven rewrite), `viewmodel/AppViewModel.kt`, `navigation/ScNavHost.kt` (report screen + BackHandler), `model/Models.kt`, i18n catalogs (10 keys × 5 languages)
- Verified: `:app:compileDebugKotlin` and `:app:test` both pass.

*Activity & Reports update completed August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

## Activity Charts — Correct Time Granularity + Graph Chart Style (August 7, 2026)

Newly implemented update to the Activity / Reports charts. The underlying usage data and monitoring logic are unchanged; this update fixes the labeling/granularity of the Daily, Weekly and Monthly charts and adds a third chart style.

### Correct time granularity per period

- **Daily** → usage grouped by hour of the selected day (X-axis shows hours such as 9 AM, 10 AM, 11 AM, 12 PM — never weekday names). Each bar/point shows the actual usage during that hour, with the usage duration printed above each point. Hours with no usage render as clean empty gaps (no misleading bars).
- **Weekly** → exactly 7 data points labelled Sunday, Monday, Tuesday, Wednesday, Thursday, Friday, Saturday (Sunday-first), each showing its actual aggregated usage with the duration above the bar.
- **Monthly** → aggregated by calendar month (January, February, March, …) over the recent 6-month range; each month bar shows its total usage duration above it. No weekday names are used.
- The headline total always equals the sum of the currently selected period (day / week / month).

### Single data source, dynamic aggregation

- One raw record set (`activity/ActivityRepository.kt` → `ActivityRecord` with date + hour-of-day + minutes) is aggregated dynamically per period — Daily groups by hour, Weekly by weekday, Monthly by calendar month. There are NO separate fake datasets per period.
- The reference day's hourly profile is scaled to its weekday base total, so the daily total always equals that day's bar in the weekly chart and the weekly total stays at the established series (27h 15m).
- Data stays entirely in the data layer; the charts only visualize. A future backend/database fills the exact same `ActivityReport` shape with no UI redesign.

### Three chart styles (Settings → Appearance → Chart)

- **Bar Chart** → thin, compact professional bars (consistent width/spacing, rounded pill tops) for all three periods.
- **Circular Chart** → circular/donut distribution with the period total in the center.
- **Graph Chart** → new line/area graph with point markers for time-series data (Daily / Weekly / Monthly).
- One global preference drives Activity, Reports and Web Analytics — changing it only re-renders the identical data; it never modifies the underlying usage values.

### Responsive & readable

- Dense series (24 hourly points) use sparse axis labels and sparse duration labels (`labelEvery` / `valueEvery`) so nothing clips on narrow screens; weekly (7) and monthly (6) label every point.
- Charts respect screen width, avoid overlapping labels, and stay clear of the system navigation area.

### Files

- New: none (built on `activity/ActivityModels.kt` + `activity/ActivityRepository.kt`)
- Modified: `charts/ChartRenderer.kt` (`ScSeriesChart` with BAR/GRAPH + sparse `valueEvery`), `charts/ChartModels.kt` (`ChartStyle.GRAPH`), `screens/activity/ActivityScreen.kt`, `screens/activity/ActivityReportScreen.kt`, `screens/settings/ChartScreen.kt`, `screens/web/WebAnalyticsScreen.kt` (GRAPH → donut fallback for distributions), i18n catalogs (`chartGraphChart` × 5 languages)
- Verified: `:app:compileDebugKotlin` and `:app:test` both pass.

*Activity charts granularity + Graph chart style completed August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

## Activity / Reports — Time-First Timeline Update (August 7, 2026)

Newly implemented update to the Activity and Reports charts. TIME, DATE and TIMELINE are now the primary information in every visualization. No monitoring logic, backend architecture or underlying usage data changed — this is a presentation + aggregation update.

### Daily — complete 24-hour timeline

- Shows the full timeline from 12 AM to 11 PM (24 points, one per hour).
- Every hour is clearly labelled (two-line "12 / AM" style so all 24 fit on narrow screens) and every hour with usage shows its exact duration (e.g. 45m, 1h 20m) above its bar/point.
- Hours with no usage render as clean empty gaps — never misleading bars.
- The user can always tell WHEN usage happened, not just how much.

### Weekly — Monday to Sunday with exact durations

- Exactly 7 points, labelled Monday–Sunday (full day names), each showing its aggregated total duration above the bar.
- The highest/lowest usage days are immediately identifiable.

### Monthly — adaptive date ranges with tap-to-detail

- The current month is divided into meaningful 7-day date ranges (Aug 1–7, Aug 8–14, …; the final range holds the remaining days).
- The number of ranges adapts automatically to the actual days in the selected month (28/29/30/31).
- Each bar shows its date range below and its total usage duration above (e.g. Aug 1–7 → 42h 15m).
- **Tapping a monthly date-range bar opens a dedicated per-day detail screen** for exactly that range — per-day bars, total, busiest day and trend. This works in Bar and Graph styles (bar tap) and in Circular style (legend row tap). System Back returns to Activity.

### Circular / Donut uses the SAME time data

- The donut is no longer the app-share distribution: it renders the exact same hour/day/range time slices as the bar chart, with the total in the center and a duration-first legend (label + exact duration + share %) beneath.
- No separate or fake data for the circle — one data source, two visualizations.

### Data layer (future-backend-ready)

- One raw record set (`activity/ActivityRepository.kt` → `ActivityRecord` date + hour + minutes) is aggregated dynamically per period — Daily groups by hour, Weekly by weekday (Monday-first), Monthly by date range. `rangeReportFor(range)` drills into one range's per-day usage. No per-period fake datasets.
- The seed now covers the full previous + current month so every date range and trend has data; the reference week keeps the established series (weekly 27h 15m, daily equals its Friday bar).
- Charts only visualize; a future backend/database fills the exact same `ActivityReport` / `ActivityRange` shapes with zero screen redesign.

### Chart styles (Settings → Appearance → Chart)

- Bar Chart → thin, compact, professional bars for all three periods.
- Circular Chart → donut of the same time data with a duration legend.
- Graph Chart → line/area with point markers for time-series data.
- One global preference drives Activity and Reports; changing it only re-renders identical data.

### Responsive & readable

- Dense 24-hour series use two-line hour labels and a smaller duration font so nothing overlaps on small phones; weekly (7) and monthly (5) label every point.
- Bars are thin with consistent spacing; charts respect screen width and stay clear of the system navigation area.

### Files

- Modified: `activity/ActivityModels.kt` (+`ActivityRange`), `activity/ActivityRepository.kt` (full-month seed, Monday-first weekly, monthly date ranges, `rangeReportFor`), `charts/ChartRenderer.kt` (`labelLines`, `valueFontSp`, `onPointClick` taps, `ScTimeLegend`), `screens/activity/ActivityScreen.kt`, `screens/activity/ActivityReportScreen.kt` (range detail), `viewmodel/AppViewModel.kt`, `navigation/ScNavHost.kt`
- Verified: `:app:compileDebugKotlin` and `:app:test` both pass.

*Activity time-first timeline update completed August 7, 2026 · ShortsCap v1.1.1 · Build 2026072801*

---

## Update — TIME, DAY & DATE visible in every Activity chart style (August 8, 2026)

New implementation entry — **TIME/DAY/DATE is now the primary information in ALL three chart styles** (Bar Chart, Circular/Donut Chart, Graph/Line Chart), driven by the single global Chart preference (Settings → Appearance → Chart). The underlying activity data, Activity architecture, Daily/Weekly/Monthly tabs and chart-selection system are unchanged — this update only improves how the existing data is visualized.

**What changed:**

- **Daily** → the complete 24-hour timeline is used internally, with clean, readable 3-hour markers (12 AM, 3 AM, 6 AM, 9 AM, 12 PM, 3 PM, 6 PM, 9 PM) so the chart never looks crowded. Bars/line rise and fall with the real hourly data; zero-usage hours stay clean empty gaps.
- **Weekly** → exactly 7 points, Monday–Sunday, each labelled with its day **and** its actual date on two lines ("Mon" / "Aug 4") plus its exact duration above.
- **Monthly** → the current month split into 7-day date ranges (Aug 1–7, Aug 8–14, …), each bar showing its date range below and total duration above; the count adapts to the month's real length.
- **Tap-to-inspect tooltip (all three styles)** → tapping a bar, graph point or donut segment surfaces a small professional detail card with the exact date ("Friday, August 7"), the clock window for daily hours ("2:00 PM – 3:00 PM") and the usage duration ("35m"), plus a close action. Monthly ranges add a "View details" action that opens the per-day detail screen.
- **Date caption near the chart** → the exact calendar span of the selected period ("Friday, August 7" / "Aug 3 – Aug 9" / "August 2026") is displayed right beside the chart and inside the donut centre, so the user always knows WHICH day/date a chart covers.
- **Selected-point highlight** → the tapped bar / graph point / donut segment is visually highlighted (others dimmed), so the selection is unmistakable in every style.
- **One data source, three renderings** → Bar, Graph and Donut all consume the exact same `ActivityPoint` series (label + minutes + full date + clock range) from `ActivityRepository`; the chart type only changes the drawing, never the numbers.

**Architecture notes:**

- `activity/ActivityModels.kt` — `ActivityPoint` now carries `detailTitle` (full date) and `timeRange` (hourly clock window) alongside `label` and `minutes`, so every renderer can show exact time/date info.
- `activity/ActivityRepository.kt` — hourly, weekday and date-range aggregations emit full date metadata; new `periodDateCaption(period)` returns the exact span text.
- `charts/ChartRenderer.kt` — `ScSeriesChart`/`ScDistributionChart` gained `selectedIndex` + tap callbacks; the donut now supports arc-level hit-testing (`donutIndexAt`, geometry matched exactly to the drawn ring) and a highlight state; new reusable `ScPointTooltipCard` renders the detail card in any style.
- `screens/activity/ActivityScreen.kt` + `ActivityReportScreen.kt` — selection is tracked by label so bar, line and donut taps always resolve to the same point, even though the donut only draws non-zero slices.
- **Future backend:** the same structured `ActivityReport`/`ActivityPoint` shapes are all the chart layer needs — a backend/database can fill them without any chart or UI redesign. The chart preference remains a pure presentation preference, never stored in usage records.

**Files modified:** `activity/ActivityModels.kt`, `activity/ActivityRepository.kt`, `charts/ChartRenderer.kt`, `screens/activity/ActivityScreen.kt`, `screens/activity/ActivityReportScreen.kt`, `i18n/AppStrings.kt` (+ `EnglishStrings`, `HindiStrings`, `UrduStrings`, `ChineseStrings`, `SpanishStrings` — 4 new tooltip strings).

**Verified:** `:app:compileDebugKotlin` and `:app:test` both pass; code review confirmed label/index mapping, donut hit-test geometry and no regressions.

*Chart time/date visibility update completed August 8, 2026 · ShortsCap v1.1.1*

---

## Update — Slide-to-inspect Graph/Line Chart interaction (August 8, 2026)

New implementation entry — a **simple touch-based visualizer** for the Graph/Line Activity chart. The user no longer needs to precisely tap small dots: placing a finger on the line chart and sliding horizontally (left ↔ right) automatically selects the **nearest data point** at every finger position, and the selection follows the finger continuously (10 AM → 11 AM → 12 PM → …).

**How it behaves:**

- **Nearest-point auto-selection** — the point under the finger is highlighted immediately (enlarged ringed marker); no precise tapping required.
- **Guide lines** — ONE thin (1dp) vertical line through the selected point and ONE thin horizontal line from the left (value-axis side) to the point, in a subtle muted colour. Minimal, professional — no crosshairs, candles, trading controls or stock-market UI.
- **Live info label** — the existing compact detail card updates automatically with the actual data for the selected point:
  - Daily → full date + clock window ("Time: 10:00 AM – 11:00 AM") + "Usage: 35m"
  - Weekly → day + date ("Monday, August 4") + "Usage: 3h 20m"
  - Monthly → date range ("Aug 1–7") + "Usage: 42h 15m"
- **Slide-only semantics** — dragging always selects (never deselects), so the label cannot vanish mid-inspection; taps still toggle. The interaction is **Graph chart only** — the Bar chart behaviour and the Circular/Donut chart are untouched.
- **Scroll-friendly** — the drag detector deliberately does not consume the touch, so vertical page scrolling still works when the finger starts on the chart.

**Architecture notes:**

- `charts/ChartRenderer.kt` — `ScSeriesChart`/`ScLineSeries` gained an `onPointDrag` callback wired only into the GRAPH branch; a new `pointDragDetector` modifier (awaitEachGesture-based) maps finger x → nearest point slot and reports only index changes (no recomposition churn); the line canvas draws the thin vertical + horizontal guides when a point is selected.
- `screens/activity/ActivityScreen.kt` + `ActivityReportScreen.kt` — pass a select-only drag handler that drives the same label-based selection state behind the existing `ScPointTooltipCard`; the underlying Activity data/architecture is unchanged (one data source, three renderings).

**Verified:** `:app:compileDebugKotlin` and `:app:test` both pass; code review confirmed graph-only scoping, gesture coexistence (tap + drag), scroll compatibility and no regressions.

*Slide-to-inspect Graph chart interaction completed August 8, 2026 · ShortsCap v1.1.1*

---

# Backend Implementation

Server backend for ShortsCap — Python **FastAPI** + **SQLAlchemy (MySQL)**.
Located in the `backend/` directory; the Android app is **not** touched by the
backend work.

> **Status:** Phase 2 (running FastAPI server) + Phase 3 (database foundation +
> environment configuration) + Phase 4 (24 approved SQLAlchemy models) +
> Phase 5 (Alembic migration applied — the 24 MySQL tables now exist) +
> Phase 6 (settings data layer) + Phase 7 (settings extended to monitoring /
> shorts / notifications / leaderboard / permissions) + Phase 8 (study data
> layer — schedules / sessions / breaks / events) + Phase 9 (monitoring data
> layer — app usage sync / monitoring events / summary) + Phase 10 (shorts
> data layer — shorts usage sync / shorts events / shorts summary) +
> **Phase 11A (shorts usage schema update — `platform` / `surface` columns,
> new idempotency key, Alembic migration `657ba9f4d4f8`)** +
> **Phase 11B (Android: cross-platform Shorts detection integrated with the
> monitoring pipeline)** + **Phase 12 (web data layer — blocked-website CRUD
> with domain normalization / website events / web summary)** + **Phase 13
> (reporting / insights layer — read-only daily / weekly / monthly reports
> with previous-period comparison)** + **Phase 14A (Your Score
> specification & validation)** + **Phase 14B (Your Score engine — read-only
> `GET /score/daily|weekly|monthly` implementing the approved spec)** +
> **Phase 15A (Rank / leaderboard specification & validation — ranking
> method, eligibility, tie-breaker, rank change designed and simulated)** +
> **Phase 15B (Rank / Leaderboard engine — read-only
> `GET /rank/weekly|monthly` implementing the approved spec, consuming the
> Score Engine as the only score source; dynamic board, `leaderboard_scores`
> not written)** + **Phase 16 (Android ↔ backend synchronization — network
> layer, offline-first sync queue with retry/dedupe, settings / study /
> monitoring / shorts / web syncers, read-only Reports / Score / Rank
> clients, temporary dev identity; Android remains the real-time authority
> for the study timer, monitoring, Shorts detection and web blocking)** +
> **Shorts HUD (Android floating counter overlay — appears automatically on
> positively-detected short-form content, global count / daily limit,
> draggable with persisted position, three appearances incl. animated Brain
> states, overlay permission handling, themed; presentation only — it
> consumes the existing detection pipeline, never detects/counts itself)**.
> Auth, OAuth, and the remaining routers are implemented in
> later phases.

## Reserved technology stack

| Area | Choice |
| --- | --- |
| Language | Python 3.14 |
| API framework | FastAPI |
| ORM | SQLAlchemy 2.x |
| Database (dev) | MySQL Community Server 8.0.43 (local) |
| Database (prod) | AWS RDS MySQL |
| MySQL driver | PyMySQL |
| Migrations | Alembic (configured later) |
| Config | pydantic-settings + python-dotenv (env-driven) |
| Env file | `backend/.env` (git-ignored) |

## Implemented so far

### Phase 2 — running server

- `backend/app/main.py` — FastAPI app with `GET /` health response.
  Verified via Uvicorn at <http://127.0.0.1:8000/> and Swagger
  <http://127.0.0.1:8000/docs>.

### Phase 3 — database foundation & environment configuration

- `backend/.env` — environment file created from `.env.example` with the local
  MySQL configuration (`DB_HOST=127.0.0.1`, `DB_PORT=3306`, `DB_USER=root`,
  `DB_NAME=shortscap_db`; `DB_PASSWORD` is intentionally left blank and must be
  filled in manually — it is **never** committed, logged, or echoed).
- `backend/app/config.py` — `pydantic-settings` `Settings`, fully env-driven
  (reads `.env` / environment variables). No credentials in source; the DB URL
  is built from `DB_HOST` / `DB_PORT` / `DB_USER` / `DB_PASSWORD` / `DB_NAME`.
- `backend/app/database.py` — SQLAlchemy engine (MySQL via PyMySQL,
  `pool_pre_ping`, `pool_recycle`), declarative `Base`, `SessionLocal`,
  FastAPI `get_db()` dependency, and a safe `check_database_connection()` that
  performs a real query and never exposes the password/connection URL.
- `backend/app/main.py` — added **`GET /health/db`** database health endpoint.
  On success returns `{"status": "connected", "database": "shortscap_db"}`;
  on failure returns HTTP 503 with `{"status": "not_connected", ...}`. It never
  leaks the password, connection string, or internal error details.
- `backend/app/models/user.py` — first model `User` (table `users`) with the
  field set mirroring the Android auth/profile requirements
  (`ProfileData` in `model/Models.kt`): `id`, `name`, `email` (unique),
  `phone` (unique, optional — mobile OTP login), `gender`, `date_of_birth`,
  `created_at`, `updated_at`. *(No application tables are created yet.)*
- `backend/scripts/check_db.py` — connectivity check reporting the **real**
  connection state:
  ```powershell
  cd backend
  .venv\Scripts\python -m scripts.check_db
  ```
- `backend/requirements.txt` — includes `fastapi`, `uvicorn`,
  `pydantic-settings`, `SQLAlchemy`, `PyMySQL` (python-dotenv ships with
  pydantic-settings).

### Phase 4 — approved SQLAlchemy models *(Aug 13, 2026)*

- All **24 approved models** implemented as SQLAlchemy 2.x models in
  `backend/app/models/` and registered on the shared `Base` metadata via
  `backend/app/models/__init__.py` (users, devices, settings, study,
  monitoring, shorts, web, notifications, feedback, leaderboard, …).
- Relationships, foreign keys, unique constraints and indexes per the approved
  schema. **No tables were created in this phase** — that happened in Phase 5.

### Phase 5 — Alembic migration & actual MySQL tables *(Aug 13, 2026)*

- Alembic configured (`migrations/env.py` uses the SAME declarative `Base` and
  the same env-driven database URL as the app — nothing hardcoded, no secrets
  printed).
- Initial migration **`70d943e5af25` — "create approved schema tables"**
  applied with `alembic upgrade head`; `shortscap_db` now contains the **24
  approved tables** (plus Alembic's own `alembic_version` table).
- Verified with `SHOW TABLES`, `DESCRIBE`, FK/index checks, `alembic current`
  (`70d943e5af25 (head)`); FastAPI restart verified (`/`, `/health/db`, `/docs`).

### Phase 6 — settings data layer *(Aug 13, 2026)*

- First vertical slice: Router → Schema → Service → Repository → SQLAlchemy →
  MySQL for `user_settings` — `GET /settings` (creates app defaults on first
  use) and `PUT /settings` (partial update only; unspecified fields preserved).
- Validation mirrors the app: `theme` ∈ dark|light|system, `language` ∈
  en|hi|ur|zh|es, `timezone` = valid IANA name.
- Temporary development identity introduced: the **`X-Dev-User-Id`** header
  (development only — replaced by Cognito later).

### Phase 7 — settings backend extended *(Aug 14, 2026)*

- The same pattern applied to the remaining settings domains —
  `GET/PUT /settings/monitoring` (device monitoring / strict mode),
  `GET/PUT /settings/shorts` (limits / warnings), `GET/PUT /settings/notifications`
  (preferences), `GET/PUT /settings/leaderboard` (participation/display only —
  no scoring), `GET/PUT /settings/permissions` (last-known sync mirror; Android
  is the real authority).
- No new tables, no schema changes; same temporary dev identity.

### Phase 8 — study data layer *(Aug 15, 2026)*

- **Study Schedule API** — `POST/GET /study/schedules`,
  `GET/PUT/DELETE /study/schedules/{id}` on the existing `study_schedules`
  table (title / subject / start_time / duration_minutes / days_of_week /
  reminder_minutes / is_enabled; per-user ownership enforced).
- **Study Session API** — `POST /study/sessions/start`,
  `POST /study/sessions/{id}/end`, `POST /study/sessions/{id}/cancel`,
  `GET /study/sessions` (+ filters), `GET /study/sessions/{id}` on
  `study_sessions`. Server-side timestamps; durations computed as
  `ended_at - started_at`; state transitions `active → completed/cancelled`.
- **Break Session API** — `POST /study/sessions/{id}/breaks/start`,
  `POST /study/breaks/{break_id}/end` on `break_sessions` (no overlapping
  active breaks; no breaks on completed sessions).
- **Study Event history** — `GET /study/events` on `study_events` with
  `STUDY_STARTED / STUDY_ENDED / STUDY_CANCELLED / BREAK_STARTED /
  BREAK_ENDED` events created for actual backend actions only.
- **Architecture** — same Router → Schema → Service → Repository →
  SQLAlchemy → MySQL pattern as the settings layer; no new tables, no schema
  changes, no new migration.
- **MySQL persistence** — verified end to end (see
  `backend/scripts/verify_study.py`).
- **Development identity** — same temporary `X-Dev-User-Id` header (now
  shared via `backend/app/routers/deps.py`); Cognito is planned later.
- **Real-time timers remain Android-side** — the backend only persists study
  state/history; it is not a real-time timer.
- See `backend/README.md` → *Phase 8 — Study Data Layer* for full detail.

### Phase 9 — monitoring data layer *(Aug 15, 2026)*

- **App usage synchronization** — `POST /monitoring/app-usage/sync` accepts
  one or a batch of aggregated daily usage summaries (device / package /
  date / duration / launches) and persists them to `app_usage` for the
  current user. **Idempotent:** re-syncing the same summary overwrites its
  values (user + device + package + date lookup) — no uncontrolled duplicates.
- **Monitoring history** — `GET /monitoring/app-usage` with filters
  (`device_id`, `package_name`, `date_from` / `date_to`) and simple
  `page` / `page_size` pagination; only the current user's rows are returned.
- **Monitoring events** — `POST /monitoring/events` (validated event types:
  `MONITORING_STARTED`, `MONITORING_STOPPED`, `LIMIT_WARNING`, `LIMIT_REACHED`,
  `APP_RESTRICTED`) and `GET /monitoring/events` with `event_type` /
  `device_id` / `app_package` / `start_date` / `end_date` filters; aware
  timestamps are normalized to naive UTC.
- **Monitoring summary** — `GET /monitoring/summary` (total usage seconds,
  total launches, monitored apps count, event count) via DB aggregation.
- **Device ownership** — usage/events must reference a device belonging to the
  current user (unknown or another user's device → 404).
- **Android remains the real-time monitoring authority** — the backend only
  stores synchronized historical data; no server-side monitoring loop, no
  timers, no app detection.
- **Architecture** — same Router → Schema → Service → Repository → SQLAlchemy
  → MySQL pattern; no new tables, no schema changes, no migration.
- **MySQL persistence** — verified end to end (see
  `backend/scripts/verify_monitoring.py`); existing Settings and Study
  endpoints still pass.
- **Temporary development identity** — same `X-Dev-User-Id` header
  (`backend/app/routers/deps.py`); Cognito planned later. Reports and
  Rank/Your Score are planned later too.
- See `backend/README.md` → *Phase 9 — Monitoring Data Layer* for full detail.

### Phase 10 — shorts data layer *(Aug 15, 2026)*

- **Shorts usage synchronization** — `POST /shorts/usage/sync` accepts one or
a batch of aggregated daily Shorts summaries (device / date / count /
 duration / warning / limit flags) and persists them to `shorts_usage` for
 the current user. **Idempotent:** re-syncing the same day overwrites its
 values (user + device + usage_date lookup) — no uncontrolled duplicates.
- **Shorts history** — `GET /shorts/usage` with filters (`device_id`,
  `date_from` / `date_to`) and `page` / `page_size` pagination; only the
  current user's rows are returned.
- **Shorts events** — `POST /shorts/events` (event types map 1:1 to real
  Android Shorts behaviors: `SHORT_STARTED`, `SHORT_COUNTED`, `SHORT_ENDED`,
  `WARNING_TRIGGERED`, `LIMIT_REACHED`) and `GET /shorts/events` with
  `event_type` / `device_id` / `start_date` / `end_date` filters; aware
  timestamps normalized to naive UTC.
- **Shorts summary** — `GET /shorts/summary` (total count / duration,
  per-day averages, warning / limit counts) via DB aggregation.
- **Warning / limit state** — persisted exactly as supplied; Android remains
  authoritative for real-time limit enforcement; a future scoring/analytics
  layer may use these fields.
- **Device ownership & user isolation** — data must reference a device owned
  by the current user (unknown / other user's device → 404); GET operations
  return only the current user's data.
- **Android remains responsible for real-time Shorts detection** — the
  backend only stores synchronized historical data; no detection loop, no
  device control, no timers.
- **Architecture** — same Router → Schema → Service → Repository → SQLAlchemy
  → MySQL pattern; `shorts_settings` reused from Phase 7; no new tables, no
  schema changes, no migration.
- **MySQL persistence** — verified end to end (see
  `backend/scripts/verify_shorts.py`); existing Settings / Study / Monitoring
  endpoints still pass.
- **Scoring / ranking / Cognito / AWS** — all planned later, none implemented.
- See `backend/README.md` → *Phase 10 — Shorts Data Layer* for full detail.

### Cross-Platform Short-Form Content Architecture *(Aug 15, 2026 — architecture lock)*

**Status: abstraction locked and documented — a full universal Shorts
detector is NOT implemented.** Short-form content is treated as
platform-independent (YouTube Shorts, Instagram Reels, TikTok, Snapchat
Spotlight, Moj, X and LinkedIn video surfaces, Facebook Reels, and future
platforms), never as a single-app feature.

- **Platform vs surface** — `ShortPlatform` (YOUTUBE, INSTAGRAM, TIKTOK,
  SNAPCHAT, FACEBOOK, MOJ, X, LINKEDIN, UNKNOWN) is the hosting app;
  `ShortSurface` (YOUTUBE_SHORTS, INSTAGRAM_REELS, FACEBOOK_REELS,
  TIKTOK_SHORT_FEED, SNAPCHAT_SPOTLIGHT, X_SHORT_VIDEO,
  LINKEDIN_SHORT_VIDEO, MOJ_SHORT_VIDEO, UNKNOWN) is the specific short-form
  place inside it. A platform may host long-form/live/chat/stories too, so
  "app is running" never means "a Short is being watched".
- **New Android abstraction** — `app/src/main/java/com/shortscap/app/shorts/`:
  `ShortPlatformAdapter` interface (supports + detect →
  `ShortDetectionResult` with platform/surface/`isShortForm`/confidence/
  `DetectionMethod`/timestamp/metadata), 8 platform adapters (YouTube is the
  only surface-positive one today via the Shorts activity class; the rest
  conservatively report UNKNOWN), `ShortPlatformRegistry` (centralized
  package→adapter mapping + `GenericShortVideoAdapter` fallback),
  `ShortUsageAggregator` (detection separated from counting; the existing
  3–5 second rule preserved: swipe < 2s → not counted, ≥ 3–5s → counted as
  one Short), and `ShortsBudgetTracker` (ONE global Shorts budget across all
  platforms + per-platform breakdown for future reports).
- **Honest detection status** — no 100% accuracy claim; only YouTube Shorts
  has a positive surface signal today; the accessibility service stays
  privacy-minimal (foreground package only, no window content).
- **Android remains the real-time authority** — the backend (Phase 10) is
  the persistence/synchronization layer only; no server-side detection,
  no WebSockets, no timers, no polling.
- **Database review** — `shorts_events.metadata_json` can already carry
  platform/surface/detectionMethod/confidence; `shorts_usage` originally
  had **no platform/surface columns**. That required change is now
  **applied in Phase 11A**: `platform` / `surface` columns (VARCHAR(50) NOT
  NULL) + unique constraint on the new idempotency key
  `(user_id, device_id, platform, surface, usage_date)` via migration
  `657ba9f4d4f8` (see the Phase 11A entry above).
- **Future platforms** — adding one needs only a new enum value + adapter +
  registry entry; aggregator / backend sync / reporting / ranking keep
  working unchanged.
- See `backend/README.md` → *Cross-Platform Short-Form Content Architecture*
  for the full detail (file-by-file table + implementation mapping).

### Phase 11A — Shorts usage database schema update *(Aug 15, 2026)*

- **Why:** `shorts_usage` had one daily summary per (user, device, date) and
  could not distinguish YouTube Shorts from Instagram Reels / TikTok /
  Snapchat Spotlight — platform-specific daily aggregation was impossible.
- **Schema change:** added `platform` and `surface` (VARCHAR(50) NOT NULL) to
  `shorts_usage`; existing/pre-architecture rows use the explicit `UNKNOWN`
  marker (historical values are never fabricated).
- **New idempotency key:** `(user_id, device_id, platform, surface,
  usage_date)`, enforced by unique constraint
  `uq_shorts_usage_user_device_platform_surface_date` — re-syncing the same
  daily summary for the same platform/surface can never duplicate rows.
- **Alembic migration:** revision **`657ba9f4d4f8`** — "add platform and
  surface to shorts_usage" (down_revision `70d943e5af25`). **Applied** —
  `alembic current` = `657ba9f4d4f8 (head)`; verified via `SHOW COLUMNS`
  (both columns NOT NULL) and the unique index.
- **Backward compatible:** `platform` / `surface` are optional in the sync
  payload (stored as `UNKNOWN` when omitted; invalid values → 422); the
  response always returns them. Same-day summaries for different
  platforms/surfaces are separate rows; the summary endpoint aggregates them
  into one global Shorts total.
- **Scope:** `shorts_usage` only — Settings / Study / Monitoring tables and
  endpoints untouched. Backend regression re-run (`verify_shorts.py` 67
  checks, `verify_study.py`, `verify_monitoring.py` all PASS).
- See `backend/README.md` → *Phase 11A — Shorts Usage Database Schema
  Update* for full detail.

### Phase 11B — Android: cross-platform Shorts detection integration *(Aug 15, 2026)*

- **Connected the monitoring pipeline:** `MonitoringEventHub` →
  `ShortPlatformRegistry` → platform adapters → `ShortDetectionResult` →
  `ShortUsageAggregator` (3–5 second rule) → `ShortsBudgetTracker` (ONE
  global budget across platforms) → `ShortsLocalStore` (local usage/event
  records pending a future sync layer).
- **New Android files:** `shorts/ShortsMonitoringPipeline.kt` (passive
  orchestrator that tracks the foreground context, detects via the registry,
  aggregates, updates the global budget and writes local records),
  `shorts/ShortsLocalStore.kt` (in-memory local store preserving
  platform/surface/detection method/confidence/timestamp/duration, with a
  sync seam), `shorts/ShortsMonitoringPipelineTest.kt` (10 unit tests).
- **Modified (minimal):** `MonitoringEventHub.kt` (listener + dispatch now
  carry the window class name — privacy-minimal metadata, never content);
  `ShortsCapAccessibilityService.kt` (passes `event.className` + subscribes
  the pipeline on connect — still no window content, no synthetic
  interaction).
- **3–5 second rule preserved:** a context left before ~2s is never counted;
  engagement reaching the 3–5s threshold counts as one Short with full
  duration; each context is evaluated exactly once (no double counting).
- **Global budget:** Shorts from every platform accumulate into one budget;
  switching platforms never resets it (unit-tested: 4s YouTube + 4s
  Instagram = 8s global).
- **Local vs backend:** detector → aggregator → local store → future sync
  layer; the detector never talks to FastAPI directly.
- **Honest limitations:** only YouTube Shorts is positively detected today
  (window class); all other platforms report UNKNOWN and are never counted;
  per-short counts are session-level (window events cannot see individual
  swipes).
- **Verification:** `./gradlew :app:compileDebugKotlin` clean;
  `./gradlew :app:testDebugUnitTest` 10/10 PASS; backend regression
  (Settings / Study / Monitoring / Shorts verify scripts, `/health/db`,
  `/docs`) all PASS — no backend changes in this phase.
- **Not done:** actual backend sync from Android, ranking/score, reports,
  AWS, Cognito, UI changes.
- See `backend/README.md` → *Phase 11B — Cross-Platform Shorts Detection
  Integration* for full detail.

### Phase 12 — Web data layer *(Aug 15, 2026)*

- **Blocked-website CRUD** on the existing `blocked_websites` table:
  `POST /websites/blocked`, `GET /websites/blocked`,
  `GET /websites/blocked/{id}`, `PUT /websites/blocked/{id}`,
  `DELETE /websites/blocked/{id}` + `GET /websites/blocked/check?domain=`.
- **Centralized domain normalization/validation** in `backend/app/utils/domain.py`
  (single reusable utility): `https://youtube.com/`, `www.YouTube.com` and
  `youtube.com` all normalize to `youtube.com` (scheme / `www.` / case /
  path/query/fragment stripped); malformed domains, bare labels and IP
  addresses are rejected with 422. Mirrors the Android app's own
  `web/DomainValidator.kt` rules.
- **Duplicate prevention:** a second attempt to block the same normalized
  domain for the same user returns **409** (schema unique constraint
  `uq_blocked_websites_user_domain` is the backstop).
- **Website events** on the existing `website_events` table: `POST /web/events`
  + `GET /web/events` (filters: `event_type`, `device_id`, `domain`,
  `start_date`/`end_date`, `page`/`page_size`) — this doubles as the website
  history endpoint. Event types limited to `BLOCK_ATTEMPT` / `BLOCKED` /
  `UNBLOCKED` (no invented taxonomy); domains normalized before storage;
  aware timestamps normalized to the backend's naive-UTC convention.
- **Web summary:** `GET /web/summary` — `total_block_attempts`,
  `total_blocked_events`, `total_unblock_events`, `unique_blocked_domains`.
  Deliberately minimal; weekly/monthly reports, Your Score, Rank and
  leaderboard are later phases.
- **Ownership & isolation:** events may reference only devices / blocked
  websites owned by the current user (404 otherwise); every GET/PUT/DELETE
  returns only the current development user's data (same temporary
  `X-Dev-User-Id` header via `app/routers/deps.py`).
- **Backend is configuration/history only:** no server-side browser
  monitoring, no accessibility service, no blocking loop, no WebSockets.
  Android remains the real-time enforcement authority and syncs events here.
- **No schema change, no migration** — the approved `blocked_websites` /
  `website_events` tables already supported everything.
- **Verification:** `scripts/verify_web.py` — 72/72 checks PASS (CRUD,
  normalization, duplicates, events, filters, summary, invalid inputs,
  ownership isolation, direct MySQL row checks, plus Settings / Study /
  Monitoring / Shorts regression).
- See `backend/README.md` → *Phase 12 — Web Data Layer* for full detail.

### Phase 13 — Reports / Insights *(Aug 15, 2026)*

- **Read-only reporting layer** over existing historical data — no new
  tables, no schema changes, no migration; the raw data remains the source
  of truth.
- **Endpoints:** `GET /reports/daily?date=`, `GET /reports/weekly?date=`,
  `GET /reports/monthly?date=` (date defaults to the server's UTC today;
  `include_comparison=false` drops the comparison block).
- **Per-domain metrics:** study (`total_study_seconds` from authoritative
  `actual_duration_seconds` = `ended_at − started_at`, completed/cancelled
  sessions, break seconds + completed breaks via the owning session),
  monitoring (`total_app_usage_seconds`, `monitored_apps_count`,
  `monitoring_event_count`, duration-ranked `top_apps`), shorts (`total
  count/duration`, `warning_count`, `limit_reached_count`, per-`platform`
  breakdown — only platforms with actual data), web (`block_attempts` /
  `blocked` / `unblocked` counts, `unique_blocked_domains`).
- **Periods & trends:** daily = one UTC day; weekly = ISO week (Mon–Sun)
  with a 7-entry daily trend; monthly = calendar month with a per-day
  trend. Days without data are honest zeros — never invented observations;
  no-data periods return a valid all-zero 200 response.
- **Previous-period comparison:** current vs previous day / ISO week /
  month for study time, Shorts time, app-usage time and block attempts,
  with `change_percent` — **None when the previous value is zero** (no fake
  percentages / no division by zero).
- **Architecture:** Router → Reporting Schema → Reporting Service →
  Reporting Repository (read-only SQL aggregations; existing domain
  repositories untouched). Aggregations run in SQL, never in Python loops
  over whole tables.
- **User isolation:** reports are computed only for the current development
  user (same temporary `X-Dev-User-Id` header via `app/routers/deps.py`).
- **Deliberately not implemented:** Your Score, Rank, leaderboard, scoring
  formulas, summary/report tables, AWS, Cognito — a later Score Engine
  consumes this data or the raw rows.
- **Verification:** `scripts/verify_reports.py` — 61/61 checks PASS (daily /
  weekly / monthly values, platform breakdown, trend, comparisons incl.
  zero-guard, no-data period, isolation, direct-SQL cross-checks, plus
  Settings / Study / Monitoring / Shorts / Web regression).
- See `backend/README.md` → *Phase 13 — Reports / Insights* for full detail.

### Phase 14A — Your Score specification *(Aug 15, 2026)*

- **SPECIFICATION ONLY — the score engine is NOT implemented or deployed.**
  This phase designed, simulated and documented the Your Score model; no
  schema change, no Android change, no leaderboard/rank logic.
- **Specification:** `backend/docs/your_score_spec.md` — formal math for the
  0–100 score: recommended weights **study 40 / shorts 25 / distraction 20 /
  web 10 / consistency 5**, per-component formulas, caps, penalties,
  aggregation (daily / ISO-week / month computed on period aggregates,
  never summed dailies), score-explanation output (e.g. study 34/40), and
  leaderboard compatibility (existing `leaderboard_scores` table is
  sufficient — no rank column; rank derived later).
- **Key rules:** missing data contributes a neutral 0.5 (never perfect); an
  inactivity gate returns **0 with `insufficient_data`** for zero-activity
  periods and partial coverage for sparse ones; tiny sessions (< 300 s) are
  excluded; study volume caps at 150 min/day-equivalent (no "12 h study =
  max" inflation); consistency counts active days, not sessions; a single
  blocked-site attempt is not punished (persistence is).
- **Documented limitation:** no app categorization exists, so distraction
  is phone-time moderation only (apps are never labelled distracting); a
  future categorization phase is required.
- **Validation:** `backend/scripts/score_spec_simulation.py` — profiles
  A–F scored A=98, D=83, F=75, C=34, B=21, E=0; sensitivity deltas bounded
  (+30 min study ≈ +1, crossing the Shorts limit ≈ +13, +1 violation day
  ≈ −8); distribution sweep (150 combos) min 30 / median 69 / p90 89 /
  max 95 — no clustering at 100 or 0. All fairness / sensitivity /
  distribution / anti-gaming checks PASS.
- **Explicit status (Phase 14A):** "Score engine implementation is NOT yet
  deployed." — Phase 14B below now implements it.
- See `backend/README.md` → *Phase 14A — Your Score Specification* for full
  detail.

### Phase 14B — Your Score engine *(Aug 15, 2026)*

- **Production score engine** implementing the approved Phase 14A
  specification exactly (no redesign, no new weights, no changed range):
  `GET /score/daily`, `GET /score/weekly`, `GET /score/monthly`.
- **Architecture:** `app/routers/score.py` → `ScoreService`
  (`app/services/scoring/score_service.py`) → pure component modules
  (`study_score.py` / `shorts_score.py` / `distraction_score.py` /
  `web_score.py` / `consistency_score.py`) → `ScoringQueries`
  (read-only SQL aggregation) → SQLAlchemy → MySQL. Approved weights
  40/25/20/10/5 live in `app/services/scoring/constants.py`.
- **Response:** period (reuses the reports `PeriodInfo`), `score` (0–100),
  `status` (`sufficient_data` | `partial_data` | `insufficient_data`),
  per-component breakdown (`points`/`max`/`value`/`status`), `activity`
  (active days, required days, coverage) and a **deterministic
  explanation** (`summary` + positives/negatives) — same inputs always
  produce the same text.
- **Approved behavior:** missing data → neutral 0.5; zero-activity period →
  **score 0 with `insufficient_data`** (inactivity never scores 100); sparse
  periods get coverage-scaled `partial_data`; sessions < 300 s excluded;
  study volume capped at 150 min/day-equivalent; consistency counts days
  not sessions.
- **Read-only + isolated:** scores are calculated dynamically (no storage,
  no cache, `leaderboard_scores` untouched); only the current development
  user's data is used (another user's request returns 0 /
  `insufficient_data`). **No Rank, no leaderboard, no other users' scores.**
- **Verification:** `scripts/verify_score.py` — 83/83 checks PASS, comparing
  every response against an **independent implementation of the approved
  formulas** (written in the script from the spec, not imported from the
  app); covers productive / imperfect / high-Shorts / high-study profiles,
  inactivity, missing data, 0–100 boundary, breakdown, determinism, user
  isolation, daily/weekly/monthly, plus full Settings / Study / Monitoring /
  Shorts / Web / Reports regression. The Phase 14A simulation still passes
  unchanged.
- **No schema change** (Alembic still `657ba9f4d4f8 (head)`), no Android
  change, no AWS/Cognito change.
- See `backend/README.md` → *Phase 14B — Your Score Engine* for full detail.

### Phase 15A — Rank / Leaderboard specification *(Aug 15, 2026)*

- **SPECIFICATION ONLY — the Rank engine is NOT implemented or deployed.**
  This phase designed, simulated and documented the leaderboard; no
  production code, no schema change, no Android change.
- **Specification:** `backend/docs/rank_leaderboard_spec.md` — source of
  truth (the Phase 14B Score Engine is the ONLY score source; the
  leaderboard never computes its own score), eligibility (opt-in from
  `leaderboard_settings.is_opted_in` + `is_enabled`, and score status
  `sufficient_data`/`partial_data`; `insufficient_data` and opted-out users
  are excluded), periods (ISO week / calendar month, same as Score &
  Reports), **competition ranking** (`100,100,99` → `1,1,3`),
  **deterministic tie-breaker** (`-score, -study, -consistency, user_id
  asc`), rank change (positive = improved; `null` when the previous period
  has no data — never invented), winner = rank #1 from the same pass, and
  a proposed `GET /rank/weekly|monthly` API contract (period, your_rank /
  your_score / rank_change, total_participants, winner, top_three,
  paginated entries; only rank / display_name / score / opaque user_id
  exposed — never email/phone/private fields).
- **Dynamic vs snapshot:** first implementation is DYNAMIC (scores computed
  on demand via the Score Engine; `leaderboard_scores` NOT written).
  Snapshotting/caching deferred until a measured performance need.
- **Validation:** `backend/scripts/rank_spec_simulation.py` — all cases
  PASS: A (10 users, unique scores → ranks 1..10), B (ties → 1,1,3 with
  deterministic tie-break), C (current user rank 15 identifiable outside
  page 1), D (opted-out excluded, no invented rank), E (insufficient-data
  excluded, partial-data eligible), F (rank 8→3 ⇒ change +5), G (4→6 ⇒
  change −2), H (empty previous period ⇒ change null) + determinism and
  fairness checks.
- **Explicit status:** "Rank engine implementation is NOT yet deployed."
  The future engine phase adds a read-only `LeaderboardService` +
  `GET /rank/weekly|monthly`. → **Implemented in Phase 15B below.**
- See `backend/README.md` → *Phase 15A — Rank / Leaderboard Specification*
  for full detail.

### Phase 15B — Rank / Leaderboard engine *(Aug 15, 2026)*

- **Production Rank / Leaderboard engine** implementing the approved Phase
  15A spec exactly: `GET /rank/weekly` and `GET /rank/monthly`
  (shared handler; `date`, `page`, `page_size` params; path-based periods
  like the Reports API).
- **Score Engine stays the only score source:** `app/services/scoring/batch.py`
  computes every eligible user's period score in a handful of grouped SQL
  queries (no N+1) and feeds the SAME module-level `assemble_score` helpers
  behind `GET /score/*` — a leaderboard score is byte-identical to the
  single-user score API; no formulas are duplicated.
- **Behavior (Phase 15A rules):** competition ranking (`100,100,99` →
  `1,1,3`); deterministic tie-break ordering (`-score, -study, -consistency,
  user_id asc`); eligibility = opted-in AND enabled
  (`leaderboard_settings`) AND score status `sufficient_data` /
  `partial_data` (opted-out, disabled and `insufficient_data` users are
  excluded, never ranked at 0); winner = rank #1 and top three from the
  SAME ranked pass; `rank_change = prev_rank − cur_rank` (positive =
  improved, `null` when the previous equivalent period has no data — never
  invented); current user's rank present even when far outside the visible
  page.
- **Privacy:** entries expose only `rank`, `display_name` (fallback
  `"User {id}"`), `score` and an opaque `user_id` — never email / phone /
  private fields; the current user's component breakdown stays in the Score
  API.
- **Dynamic board:** computed on demand; **`leaderboard_scores` is NOT
  written**, no caching, no rank storage (snapshotting deferred until a
  measured performance need).
- **No schema change** (Alembic still `657ba9f4d4f8 (head)`), no Android
  change, no AWS/Cognito change.
- **Verification:** `backend/scripts/verify_rank.py` — 47 checks covering
  cases A–O (unique scores, ties, top three, current user outside page 1,
  opted-out / disabled / insufficient-data exclusions, rank increase /
  decrease / no-previous-data, weekly + monthly, pagination with global
  ranks, deterministic repeat, winner) plus privacy checks and full
  regression of Settings / Study / Monitoring / Shorts / Web / Reports /
  Score. Every rank is compared against an INDEPENDENT implementation of
  the Phase 15A logic written in the script (RankService is not imported).
- See `backend/README.md` → *Phase 15B — Rank / Leaderboard Engine* for
  full detail.

### Phase 16 — Android ↔ Backend Synchronization *(Aug 15, 2026)*

- **Android network layer:** `BackendConfig` (emulator host
  `http://10.0.2.2:8000/` — NOT `127.0.0.1` — overridable for staging /
  production; centralized timeouts + temporary dev identity header
  `X-Dev-User-Id`), single `HttpBackendApi` HTTP client (project
  `HttpURLConnection` convention — no Retrofit/OkHttp was present), 1:1
  DTOs with the backend schemas, and `ApiResult` success/error handling.
- **Offline-first sync core:** `SyncModels` (`PENDING → SYNCING → SYNCED /
  FAILED`), `SyncQueue` (in-memory FIFO + dedupe key), `SyncManager`
  (bounded retry with backoff for transient failures only — never 4xx;
  marks `SYNCED` only on success; never drops local data when offline).
- **Domain syncers:** settings, study (schedule/session/break/event),
  monitoring (usage + events), shorts (usage + events, platform + surface
  retained) and web events — wired into the existing repository seams
  (`SettingsRepository`, `StudyRepository`, `ShortsMonitoringPipeline`,
  `WebRepository`) with graceful fallback when the backend is unreachable.
- **Read-only clients:** Reports (`GET /reports/daily|weekly|monthly`),
  Your Score (`GET /score/daily|weekly|monthly`) and Rank
  (`GET /rank/weekly|monthly`) with a small cache — server data stays
  authoritative, no Kotlin re-implementation of the Score/Rank engines.
- **Conflict policy:** local user change is authoritative immediately;
  a successful backend response confirms persistence; server values are
  used on initial/refresh sync and never silently overwrite a fresh local
  change. Duplicate uploads are prevented by the Android dedupe key AND the
  backend's existing idempotent sync endpoints.
- **Responsibility split:** Android remains the real-time authority (study
  timer, monitoring, Shorts detection, web blocking); the backend remains
  authoritative for persisted historical data, Reports, Your Score and
  Rank.
- **Temporary identity:** local dev only — the centralized `X-Dev-User-Id`
  header; no fake login / JWT / OTP; Cognito replaces this boundary later.
- **No backend schema change** (Alembic still `657ba9f4d4f8 (head)`), no
  AWS/Cognito, no security-hardening work.
- **Verification:** `backend/scripts/verify_sync_contracts.py` (86 checks
  over every contract the Android client uses + full regression); Android
  `SyncManagerTest` (10 new tests); `:app:compileDebugKotlin` clean,
  `:app:testDebugUnitTest` 20/20.
- See `backend/README.md` → *Phase 16 — Android ↔ Backend Synchronization*
  for full detail.

### Shorts HUD — Floating Counter + Animated Appearance *(Aug 15, 2026)*

- **Android-only presentation feature.** The Shorts HUD is a small floating
  overlay that appears automatically while the existing cross-platform
  Shorts detection identifies active short-form content. It is NOT a new
  detection or counting engine: it consumes the existing pipeline's
  surface-state broadcasts and the global budget tracker.
- **Flow:** MonitoringEventHub → existing detection pipeline (registry +
  adapters) → `ShortFormSurfaceState` (new listener seam on
  `ShortsMonitoringPipeline`) → `ShortsHudController` → floating overlay.
  The pipeline broadcasts a non-null state ONLY when the foreground context
  is positively detected as short-form (isShortForm + confidence ≥ 0.5) —
  normal long-form video, home screens, chat and unknown/low-confidence
  content broadcast null, so the HUD stays hidden (no guessing).
- **Global count display:** `CURRENT / DAILY_LIMIT` (e.g. `4 / 200`) — ONE
  count across ALL platforms (YouTube Shorts + Instagram Reels + TikTok +
  Snapchat Spotlight all contribute to the same total; no per-platform HUD
  counters). The daily limit is the product default (200) and is replaced
  by the backend `shorts_settings.daily_limit_count` when the settings sync
  provides one.
- **Lifecycle / animations:** hidden → fade + scale-in (α 0→1, 0.92→1.00,
  ~220 ms) → visible when short-form is active; short fade/scale-out
  (~150 ms) when it stops. After entry the HUD settles STATIC — only count
  changes and brain-state transitions trigger micro-animations (battery
  friendly, no continuous redraw loops). Under reduced-motion (animator
  scale 0) the scale/pulse effects are skipped; state colors remain.
- **Draggable + persistent position:** the chip is draggable within safe
  screen bounds (never fully off-screen); on release the position is saved
  as NORMALIZED (0..1) X/Y fractions in the existing SharedPreferences
  architecture, so it survives device size / orientation changes and is
  restored on restart. Default position: top-center.
- **Three appearance modes:** **ShortsCap** (branded logo + count, primary
  accent), **Brain** (animated brain with usage-driven states — HEALTHY
  0–40% / TIRED 40–75% / NEAR_LIMIT 75–99% / LIMIT_REACHED ≥100% — subtle
  premium breathing/pulse, warning/danger accents only on the brain), and
  **Live Counter** (cleanest minimal `04 / 200`). All three respect the
  global Dark / Light / System theme via the app's theme store.
- **Settings location:** Settings → Monitoring → Shorts → **Shorts HUD**
  (`ShortsHudScreen`): enable switch, the three appearance options, and a
  SYSTEM_ALERT_WINDOW permission status card with an "Open settings"
  redirect. Overlay permission is checked before every show
  (`Settings.canDrawOverlays`) — missing/revoked permission fails
  gracefully, never crashes. The HUD itself never calls the backend
  directly (sync stays: Shorts local state → existing Sync Layer → FastAPI).
- **Replaces the old Brain overlay:** the previous package-based
  `BrainOverlayManager` (which showed for any supported package — even
  YouTube Home) was superseded by the detection-based HUD and removed with
  its resources; the `ic_brain` drawable lives on as the Brain mode's
  icon. No new foreground service was added — the HUD rides the existing
  accessibility/monitoring pipeline.
- **Honest limitations:** with the current signal set only the YouTube
  Shorts surface is positively detected (window-class based); other
  platforms report UNKNOWN and never trigger the HUD. The HUD's count
  reflects the pipeline's counted total (session-level, per the existing
  3–5 second rule — unchanged).
- **Verification:** `:app:compileDebugKotlin` clean;
  `:app:testDebugUnitTest` 38/38 (15 new HUD logic tests: brain-state
  thresholds, appearance parsing, position clamping + 3 new pipeline
  surface-listener tests). Backend untouched — all 8 verify scripts still
  pass (Study 56/56, Monitoring 55/55, Shorts 67/67, Web 72/72, Reports
  61/61, Score 83/83, Rank 47/47, Sync contracts 86/86), Alembic still
  `657ba9f4d4f8 (head)`, no schema change.

### Shorts HUD — Appearance Settings UI *(Aug 16, 2026 — Phase 1)*

- **Settings relocated:** the Shorts HUD settings now live **exactly** at
  Settings → **Appearance → Shorts HUD** (moved out of Monitoring → Shorts
  Control; the Shorts Control page is back to per-platform toggles only).
  The dedicated `ShortsHudScreen` keeps the title **"Shorts HUD"** and uses
  the existing Settings/Appearance architecture — no new settings system.
- **Exactly three appearance options** (order per spec): **Brain**,
  **Counter**, **ShortsCap**. The old "Live Counter" mode is now displayed
  as **"Counter"** (the internal enum key `LIVE_COUNTER` and persisted
  value are unchanged). No additional modes.
- **Visual previews on every option** (mock preview data only — the live
  HUD is not started here): Brain shows a compact pulsing brain chip
  (the *concept* of an animated brain state), Counter shows the clean mock
  example **127 / 200** (current 127, limit 200), and ShortsCap shows the
  existing app logo/branding inside a small HUD-like chip (no new logo).
- **Radio-card selection:** exactly one mode selected at a time
  (`○ Brain ● Counter ○ ShortsCap`), using the app's existing radio-row
  design (`Role.RadioButton` semantics + highlighted card + radio dot).
- **Persistence:** the choice is saved through the existing
  SharedPreferences-based `ShortsHudSettingsStore` (same architecture as
  the theme/language stores) — it survives app restart, navigation away and
  returning, and device reboot. Also wired into Reset All Settings.
- **Global theme:** the screen reads `LocalScColors` + `ScTextStyles`, so
  Dark / Light / System modes, the accent color, typography and spacing all
  come from the existing system — nothing is hardcoded.
- **Localization:** all strings (Shorts HUD, Brain, Counter, ShortsCap,
  preview descriptions, Selected/Not selected accessibility states) were
  added to the catalog in all five languages (EN / HI / UR / ZH / ES).
- **Accessibility:** each option is a proper radio row announcing the
  option name + selected state (never color only); each preview carries a
  meaningful content description.
- **Out of scope for this phase (by design):** no overlay permission flow,
  no enable switch, no draggable overlay, no live count, no brain state
  playback and no backend calls from the settings screen — the screen only
  creates the three options, their previews and the saved selection.

### Shorts HUD — Runtime Integration *(Aug 16, 2026 — Phase 2)*

- **No second detection/counting system.** The HUD consumes the EXISTING
  runtime: `MonitoringEventHub → detection pipeline (registry + adapters) →
  ShortUsageAggregator (3–5s rule) → ShortsBudgetTracker →
  ShortsHudController → ShortsHudOverlayManager → selected appearance`.
  The HUD never calculates its own count.
- **Global count:** the HUD shows the GLOBAL budget total across all
  supported short-form platforms (e.g. YouTube Shorts 80 + Instagram Reels
  40 + TikTok 25 = **145 / 200**) — never per-platform counters. The daily
  limit comes from `ShortsHudSettingsStore` (default 200, replaced by the
  backend `shorts_settings.daily_limit_count` when the settings sync
  provides it).
- **Counter mode:** `current_count / daily_limit` (e.g. `127 / 200`), a
  subtle count bump on change — no large animations.
- **ShortsCap mode:** the existing ShortsCap logo/branding + `count /
  limit` in one compact chip.
- **Brain mode — the user's four FINAL videos.** The supplied videos were
  copied UNCHANGED (exact filenames preserved, including the double
  `.mp4.mp4` extension) into the project's existing asset convention at
  **`app/src/main/assets/shorts_brain/`**:
  - `brain_1_healthy.mp4.mp4` → HEALTHY
  - `brain_2_tired.mp4.mp4` → TIRED
  - `brain_3_near_limit.mp4.mp4` → NEAR_LIMIT
  - `brain_4_limit_reached.mp4.mp4` → LIMIT_REACHED
  Playback uses a lightweight `TextureView` + platform `MediaPlayer`
  (`BrainVideoView` — the same Android media stack already used for local
  assets, no new library): muted, looped, local-only (zero network),
  aspect-ratio preserved (center-crop), sized to the HUD. Only the current
  state's video is loaded (one player at a time) and it is released when
  the HUD hides or another mode is chosen.
- **Brain state mathematics (ratio-based, limit-agnostic):**
  `ratio = current_count / daily_limit` with the approved thresholds —
  0–40% HEALTHY, 40–75% TIRED, 75–99% NEAR_LIMIT, ≥100% LIMIT_REACHED
  (`BrainState.forRatio`). The thresholds adapt automatically when the
  daily limit changes (50 / 100 / 200 / 500 …). Edge cases: limit 0 →
  safe healthy state (no divide-by-zero), count 0 → Healthy, count > limit
  → Limit Reached, missing limit → existing default (200) fallback.
- **State transitions:** the video swaps only when the state actually
  changes; an unchanged state never restarts playback, and normal Compose
  recomposition is a no-op (`BrainVideoView.showState`).
- **HUD visibility:** the overlay appears only while the detection layer
  reports a positively detected short-form surface (`isShortForm` +
  confidence ≥ 0.5) and hides immediately when the user leaves it — never
  on Home, Settings, long-form video, unrelated apps or unknown content.
- **Overlay:** reuses the existing `ShortsHudOverlayManager`
  (TYPE_APPLICATION_OVERLAY). If the SYSTEM_ALERT_WINDOW permission is
  missing or revoked the HUD fails gracefully — no crash, no repeated
  prompting — and stays off until the user grants it. No second monitoring
  service.
- **Dragging + persistence:** the HUD remains draggable, default position
  top-center, constrained to safe bounds, persisted as normalized (0..1)
  X/Y in the existing store and restored on restart.
- **Theme:** Counter and ShortsCap adapt to Dark / Light / System + the
  app accent; the videos themselves are never recolored.
- **Performance / battery:** no video plays while the HUD is hidden (the
  player is released on hide), at most one video loads at a time, and
  Counter/ShortsCap are static except for count changes.
- **Backend boundary:** the HUD never calls FastAPI; it reads the local
  runtime count/limit only, and backend synchronization continues through
  the existing Shorts sync layer. No backend schema or scoring changes.
- **Verification:** `:app:compileDebugKotlin` clean;
  `:app:testDebugUnitTest` (incl. new `BrainVideoAssets` mapping tests) —
  see the Shorts HUD Runtime Integration report. Backend untouched: no
  schema change, no score/rank/report changes.

### Phase 19 — Security Hardening & Security Audit *(Aug 16, 2026)*

- **Security audit + controlled hardening pass** using OWASP MASVS as the
  mobile baseline; the FastAPI backend was assessed separately. No CRITICAL
  findings and **no secrets** in the repository. Full report:
  `backend/docs/security_audit.md`.
- **Secret handling:** `.env` is git-ignored (root + backend); no MySQL
  password, AWS key, private key or token exists in source, resources,
  assets, BuildConfig or README files. A repository-wide secret scan runs in
  `backend/scripts/verify_security.py` (reports file + secret TYPE only).
- **Backend hardening:** the temporary development identity
  (`X-Dev-User-Id`) now **fails closed in production** (`DEV_IDENTITY_ENABLED`,
  default derived from `APP_ENV`); environment-aware CORS (`"*"` rejected
  outside development); configurable trusted hosts (`ALLOWED_HOSTS`);
  minimal security headers (nosniff / frame / referrer); sanitized
  DEBUG-only access logging (never headers/bodies/query strings). Input
  validation, user isolation, error masking and idempotent sync were
  audited and verified already in place.
- **Android hardening:** release builds block all cleartext traffic via
  `res/xml/network_security_config.xml` (local-dev HTTP exception exists
  only in the debug variant); the dev identity header is sent only in debug
  builds (`BuildConfig.DEBUG`); `android:allowBackup="false"`; R8 resource
  shrinking enabled + documented `proguard-rules.pro`.
- **Play Integrity:** preparation only — token acquisition points and the
  backend verification boundary are documented; requires Google Play
  project credentials (deferred).
- **Deferred (marked, not implemented):** AWS deployment, Cognito
  authentication (a **required** production dependency), production
  TLS/domain, WAF/rate limiting, production secrets management.
- **Verification:** `backend/scripts/verify_security.py` (static + live
  security checks); full backend regression (study / monitoring / shorts /
  web / reports / score / rank / sync contracts); `:app:compileDebugKotlin`,
  `:app:testDebugUnitTest` and `:app:assembleRelease` build successfully.
  Database schema **unchanged** (no Alembic migration); AWS **not
  modified**; Cognito **not implemented**.

### Phase 20 — Final Pre-Production Audit & Gap Analysis *(Aug 16, 2026)*

- **Audit-first phase — no code changed.** Full evidence-based audit of every
  phase, documented vs actual implementation: `backend/docs/preproduction_audit.md`.
- **Verified:** backend 556/556 checks (study/monitoring/shorts/web/reports/
  score/rank/sync-contracts/security), Android 40/40 unit tests,
  debug + release builds (R8 + resource shrinking), Alembic consistent
  (`657ba9f4d4f8` current = head), 25/25 tables, Phase 19 security controls hold.
- **Honest gaps (not production-ready):** no real-device/emulator run has been
  performed; real Android app-usage collection and real-time web enforcement are
  NOT implemented (explicit seams); the sync queue and Shorts local store are
  in-memory (lost on process death); lint reports 2 pre-existing errors
  (incl. an API-34 `LocalDate.ofInstant` call in `SyncCoordinator` — P1 crash
  risk on API 26–33). P0 blockers: none identified from static/build evidence.
- **Next:** fix the two P1 items, then a controlled real-device test pass;
  AWS + Cognito remain required before any production claim.

### P1-1 — Android API compatibility crash fix *(Aug 16, 2026)*

- **Root cause:** `SyncCoordinator.kt` used `LocalDate.ofInstant(...)` to build
  the UTC date bucket for Shorts usage aggregation. That method only exists
  from Android API 34, while the app supports `minSdk = 26` — a
  `NoSuchMethodError` crash risk on API 26–33 devices.
- **Affected API range:** Android API 26–33 (API 34+ unaffected).
- **Compatibility-safe replacement:** extracted the bucket logic into a small
  `utcDateKey(epochMillis)` helper that uses `Instant.ofEpochMilli(...)
  .atZone(ZoneOffset.UTC).toLocalDate()` — java.time APIs available since API
  26 (the same pattern already used elsewhere in the project). Date/time
  behavior is unchanged: buckets are the UTC calendar date of the occurrence,
  exactly as before, so sync-period computation, midnight boundaries and
  pending/retry logic are identical. No new date/time framework, no sync
  architecture change.
- **Verification:** `:app:compileDebugKotlin`, `:app:testDebugUnitTest` (new
  focused `SyncCoordinatorTest` — UTC/local midnight, month start/end, leap
  day, DST, epoch zero — 5/5) and `:app:assembleRelease` all pass; the lint
  `NewApi` error is gone (remaining lint error is the pre-existing cosmetic
  `HighAppVersionCode` heuristic). No other API-34-only date/time calls exist
  in the sync path. Database, backend, UI and `minSdk` untouched.

## Database connection status

- **Local MySQL:** Community Server 8.0.43 installed, `MySQL80` Windows service
  running, database `shortscap_db` created. The backend `.env` is configured for
  it, but `DB_PASSWORD` is **blank** pending manual entry — so the real status
  is `not_configured` until the password is set in `backend/.env`.
- **AWS RDS production: NOT CONFIGURED** — no RDS instance provisioned yet; the
  same pydantic-settings config will point at RDS purely via env vars.

To connect: edit `backend/.env`, set `DB_PASSWORD` to the local MySQL root
password, then rerun:
```powershell
cd backend
.venv\Scripts\python -m scripts.check_db
```
or call `GET /health/db` — expected success: `{"status": "connected", "database": "shortscap_db"}`.

## Next phases (not yet implemented)

OTP / Google / JWT / Cognito auth endpoints (replaces the temporary
`X-Dev-User-Id`), device-monitoring / study / shorts / web-blocking
enforcement engines, Android → backend sync, analytics, reports, leaderboard
scoring, AWS deployment, and the notifications backend — each one at a time.

---

*Backend Phase 3 (database foundation) completed August 11, 2026*
