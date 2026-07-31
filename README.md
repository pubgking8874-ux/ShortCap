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

---

## Features

- **4 main screens** (bottom navigation): Home, Activity, Web, Settings.
- **Home dashboard**: greeting, a large animated **circular analytics widget** (swipeable metric pages — "Today's Shorts Watch Time" and "Today's Shorts Watched" — with page indicators), Quick Stats cards, and Recent Activity.
- **Activity**: usage timeline bar chart, most-used-apps donut chart, unlock/session stat cards, and expandable reports.
- **Web**: searchable Blocked / Allowed / Recent site lists with per-site toggles and a website count summary.
- **Settings**: expandable category list with switches, and a **Theme selector** (Dark / Light / System Default) under *Appearance*.
- **App chrome**: navigation drawer (slide-in with scrim), profile popover menu, and toast feedback.
- **Complete theme system**: dark + light palettes, system-follow mode, persisted selection, smooth animated switching, and automatic system-bar icon adaptation.

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
└── app/
    ├── build.gradle.kts           # app module: SDK levels, dependencies
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                   # launcher icons, styles.xml (XML app theme)
        └── java/com/shortscap/app/
            ├── MainActivity.kt        # entry point (edge-to-edge, Compose)
            ├── ShortsCapApp.kt        # root composition: theme + chrome + screens
            ├── components/            # reusable UI building blocks
            │   ├── AppDrawer.kt       # navigation drawer + scrim
            │   ├── BottomNavBar.kt    # floating bottom navigation pill
            │   ├── CircularAnalytics.kt # animated circular widget + swipe carousel
            │   ├── CommonComponents.kt # card, button, chip, switch, skeleton, etc.
            │   ├── ProfileMenu.kt     # profile popover
            │   ├── ScToast.kt         # toast overlay
            │   └── TopBar.kt          # top app bar (hamburger | logo | avatar)
            ├── model/Models.kt        # data classes & enums
            ├── navigation/ScNavHost.kt # tab dispatch
            ├── screens/
            │   ├── activity/ActivityScreen.kt
            │   ├── home/HomeScreen.kt
            │   ├── settings/SettingsScreen.kt
            │   └── web/WebScreen.kt
            ├── theme/
            │   ├── Color.kt           # ScColors palettes + LocalScColors
            │   ├── Theme.kt           # ThemeMode + ShortsCapTheme
            │   ├── ThemePreferenceStore.kt # SharedPreferences persistence
            │   └── Type.kt            # typography
            └── viewmodel/AppViewModel.kt # single source of truth (StateFlow)
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

---

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `SDK location not found` / Gradle sync fails | Create/check `local.properties` with `sdk.dir=C\:\\path\\to\\Android\\Sdk` |
| Java version errors | Set JDK 17 in Android Studio → Settings → Build Tools → Gradle |
| `Icons.AutoMirrored` deprecation warnings | Purely informational; existing icons are intentional |
| Hilt kapt processor warnings | Expected while Hilt is build-ready but not wired (`@HiltAndroidApp`) |

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
