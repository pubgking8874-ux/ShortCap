# ShortsCap Auth UI (Frontend Only)

A complete, self-contained Jetpack Compose module for the 8 authentication
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
                       Google sign-in button, Email/Mobile sign-in option
                       buttons, OR divider, back button, gradient logo mark,
                       terms checkbox row
screens/
  SplashScreen.kt
  WelcomeScreen.kt
  LoginScreen.kt
  MobileLoginScreen.kt  — mobile-number login (country code + phone number)
  CreateAccountScreen.kt
  ForgotPasswordScreen.kt
  OtpVerificationScreen.kt  — shared by Forgot Password + Mobile Login
  ResetPasswordScreen.kt
navigation/
  AuthScreen.kt      — route constants (+ OtpVerification destination/mode args)
  AuthNavGraph.kt     — NavHost wiring all 8 screens + transitions
MainActivityExample.kt — example host Activity (for standalone preview/testing)
```

## Flow implemented

```
Splash → Welcome → { Sign In | Create Account | Continue as Guest }

Login → Forgot Password → OTP Verification (mode=reset) → Reset Password → Login
Login → Mobile Login → OTP Verification (mode=login) → onExitToDashboard()
Login (success)          → onExitToDashboard()
Create Account (success) → onExitToDashboard()
Continue as Guest        → onExitToDashboard()
```

`onExitToDashboard` is the single seam back into your real app — every
"successful" mock action (guest, sign-in, create-account, password reset)
calls it or returns to Login. Wire it to your existing Dashboard nav graph.

## Mobile Number Login

Sign In exposes a **"Continue with Mobile Number"** option (modern smartphone
icon, same style as the Google button). It opens `MobileLoginScreen` — a
dedicated screen that reuses the module's design system end-to-end: same logo,
"Welcome Back" heading, compact 50 dp fields, buttons, glow accents and footer.

**Single-row phone input:** one bordered box (50 dp, 14 dp corners, animated
1→2 dp border on focus) split by a vertical divider:

```
| flag +91 ▼ | 9876543210 |
```

- Left: country selector (flag emoji + dial code + caret) — opens a
  `DropdownMenu` of `SupportedPhoneCountries` (India +91, USA +1, UK +44,
  Canada +1, Australia +61, UAE +971).
- Right: digit-only number field (`KeyboardType.Phone`); only ASCII `0-9` is
  accepted and input is capped at the selected country's `maxNumberDigits`.
- **Extending countries** = appending one `PhoneCountry(...)` entry to
  `SupportedPhoneCountries` (both live in `MobileLoginScreen.kt`).

**OTP reuse, zero duplication:** `OtpVerificationScreen` takes a `destination`
(what the code was sent to — email or phone) instead of a hardcoded email.
`AuthScreen.OtpVerification` carries `destination` + `mode` route args:

- `mode=reset` (Forgot Password) → "Verify" navigates to Reset Password.
- `mode=login` (Mobile Login) → "Verify" calls `onExitToDashboard()`.

Forgot Password now also passes the email through, so the OTP subtitle shows
"code sent to <email>". Mobile Login passes e.g. `+91 9876543210`.

**Back navigation:** Mobile Login is pushed from Login, so the back button and
"Continue with Email" both `popBackStack()` back to the Email Login; "Create
Account" behaves exactly as it does from Sign In.

---

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
- No real OTP/email/SMS verification — `OtpVerificationScreen`'s countdown and
  "Verify" button are cosmetic (and the Mobile Login "Send OTP" is a mock
  callback — wire `onSendOtp` to an SMS/OTP provider when ready)
- No real password reset — `ResetPasswordScreen` just navigates back to Login
- No country auto-detection — `MobileLoginScreen` defaults to India (+91);
  device/SIM-based detection can be added later without UI changes

When you're ready to wire real logic, the natural seams are: `onSignIn`,
`onCreateAccount`, `onSendOtp`, `onVerify`, `onPasswordUpdated`, and
`onExitToDashboard` — every one of these is already a callback parameter,
so backend logic can be layered in later without touching any UI code.
