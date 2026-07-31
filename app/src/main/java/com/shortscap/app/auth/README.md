# ShortsCap Auth UI (Frontend Only)

A complete, self-contained Jetpack Compose module for the 7 authentication
screens, wired with Navigation Compose using **mock navigation only** — no
backend, no API calls, no AWS/Cognito, no ViewModels/Repositories.

## What's included

```
theme/
  Color.kt        — light/dark color tokens (placeholder palette — see note below)
  Type.kt         — type scale
  Theme.kt        — ShortsCapAuthTheme(themeMode) composable, supports Light/Dark/System
components/
  AuthComponents.kt — shared building blocks: buttons, text fields, password
                       field w/ show-hide, strength indicator, 6-box OTP row,
                       Google button placeholder, OR divider, back button,
                       gradient logo mark, terms checkbox row
screens/
  SplashScreen.kt
  WelcomeScreen.kt
  LoginScreen.kt
  CreateAccountScreen.kt
  ForgotPasswordScreen.kt
  OtpVerificationScreen.kt
  ResetPasswordScreen.kt
navigation/
  AuthScreen.kt      — route constants
  AuthNavGraph.kt     — NavHost wiring all 7 screens + transitions
MainActivityExample.kt — example host Activity (for standalone preview/testing)
```

## Flow implemented

```
Splash → Welcome → { Sign In | Create Account | Continue as Guest }

Login → Forgot Password → OTP Verification → Reset Password → Login
Login (success)          → onExitToDashboard()
Create Account (success) → onExitToDashboard()
Continue as Guest        → onExitToDashboard()
```

`onExitToDashboard` is the single seam back into your real app — every
"successful" mock action (guest, sign-in, create-account, password reset)
calls it or returns to Login. Wire it to your existing Dashboard nav graph.

## Integrating with your existing Dashboard design system

This module ships a **placeholder** color palette (indigo/violet primary +
teal accent) and default Material typography so it looks finished and
premium on its own. To make it pixel-match your real Dashboard:

1. Delete `theme/Color.kt`'s values and either import your Dashboard's
   existing `ColorScheme`, or copy its hex values in here.
2. If Dashboard uses a custom font (e.g. a Google Font), set
   `ShortsCapFontFamily` in `Type.kt` to that same `FontFamily`.
3. If Dashboard already has a `ShortsCapTheme` composable, drop this
   module's `Theme.kt` and have `MainActivityExample`-style hosting call
   the existing one instead — the whole point is one shared theme.
4. Replace the `BrandLogoMark` composable placeholder and the Welcome
   screen's icon-based hero with your real logo/illustration assets.

## Dependencies (add if not already present)

```kotlin
implementation("androidx.navigation:navigation-compose:2.8.0")
implementation("androidx.compose.material:material-icons-extended:1.7.0")
implementation("androidx.activity:activity-compose:1.9.0")
```

Standard Compose BOM / Material3 dependencies are assumed to already be in
your project since the Dashboard is built with them.

## What was intentionally left out (per spec)

- No API calls, no network layer
- No AWS / Cognito integration
- No ViewModels or Repository classes — all state is local `remember {}`
  inside each screen, exactly enough to make the UI feel real
- No real OTP/email verification — `OtpVerificationScreen`'s countdown and
  "Verify" button are cosmetic
- No real password reset — `ResetPasswordScreen` just navigates back to Login

When you're ready to wire real logic, the natural seams are: `onSignIn`,
`onCreateAccount`, `onSendOtp`, `onVerify`, `onPasswordUpdated`, and
`onExitToDashboard` — every one of these is already a callback parameter,
so backend logic can be layered in later without touching any UI code.
