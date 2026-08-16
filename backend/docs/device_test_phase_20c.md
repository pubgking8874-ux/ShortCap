# Phase 20C — First Controlled Android Device Test

**Date:** August 16, 2026
**Phase type:** TESTING + GAP IDENTIFICATION (no fixes applied, no code changed)

---

## Environment

| Item | Value |
|---|---|
| Device | **Physical device** — vivo I2208 (MediaTek / arm64-v8a) |
| Android version | 14 |
| API level | 34 |
| Architecture | arm64-v8a |
| App build type | Debug (`assembleDebug`) |
| App version | 1.1.1 (versionCode 2026072801) |
| APK | `app/build/outputs/apk/debug/app-debug.apk` (51.9 MB) |
| Backend environment | Local dev not running during device pass (offline — app is UI/demo-seeded; backend read clients return empty) |
| Backend URL | Local dev endpoint (debug build, dev identity header present) |
| Development identity | `X-Dev-User-Id` (debug build only, `BuildConfig.DEBUG` gate) |
| Network condition | Device online (mobile data); backend not reachable in this pass |
| Permissions granted | Usage Access: **allow** · Overlay (SYSTEM_ALERT_WINDOW): **allow** · Notifications: enabled (importance=DEFAULT) · Accessibility: **enabled during tests** · Ignore Battery Optimization: enabled · Storage/Media: enabled |
| Accessibility service | `com.shortscap.app/.accessibility.ShortsCapAccessibilityService`, `TYPE_WINDOW_STATE_CHANGED` only, bound & confirmed during Shorts tests |

**Notes**
- All results below are from the PHYSICAL device unless labeled otherwise.
- The test APK is a debug build; the release build was compiled and unit-tested separately (see build summary).
- No code was modified during this phase. The working tree contains no test hacks (only transient `adb` actions).

---

## Test Matrix

| Test ID | Feature | Action | Expected | Actual | Status | Evidence | Severity | 20D Action |
|---|---|---|---|---|---|---|---|---|
| C01 | Install | `adb install -r app-debug.apk` | Installs | Installed (Success) | **PASS** | adb install log | — | — |
| C02 | Launch | `am start .MainActivity` | App opens | Dashboard rendered (Good evening, Arjun; Quick Stats; Recent Activity) | **PASS** | uiautomator dump + screenshot | — | — |
| C03 | First-launch flow | Continue as Guest | Enters dashboard | Auth → Continue as Guest → Dashboard worked | **PASS** | UI dump (Welcome → Dashboard) | — | — |
| C04 | Nav: Home/Activity/Rank/Web/Settings | tap each bottom-nav item | Each screen renders | All 5 render (Activity Daily/Weekly/Monthly; Rank 12/86 + podium; Web blocking UI; Settings list) | **PASS** | UI dumps per tab | — | — |
| C05 | No startup crash | launch + logcat watch | No FATAL/ANR | 0 crashes/ANRs in session | **PASS** | logcat grep = 0 | — | — |
| C06 | Force-stop + relaunch | `am force-stop` then relaunch | App restarts cleanly | Relaunch → Welcome (auth) rendered, no crash | **PASS** | UI dump + logcat | — | — |
| C07 | Settings persistence | change HUD appearance, force-stop, relaunch | Choice survives | BRAIN → LIVE_COUNTER persisted across force-stop; BRAIN restored | **PASS** | prefs XML + UI selected card after restart | — | — |
| P01 | Permission: denied/revoked | Accessibility toggled off (force-stop cleared it) | App doesn't crash; service reports disabled | Permissions screen shows Disabled; no crash; service re-enable via Settings worked | **PASS** | settings secure + UI state | — | — |
| P02 | Permission: granted | Enable Accessibility in system Settings | Service binds | Service bound, `eventTypes=TYPE_WINDOW_STATE_CHANGED` | **PASS** | `dumpsys accessibility` bound services | — | — |
| P03 | MonitoringService foreground | a11y enabled | FGS starts | `MonitoringService` running, `isForeground=true`, notification id=1 | **PASS** | `dumpsys activity services` | — | — |
| S01 | General/Monitoring/Shorts/Notifications settings | navigate Settings screens | Render + persist | Settings list renders (General/Monitoring/Permissions/Notifications/Sound/Appearance/About); persistence via SharedPreferences verified | **PASS** | UI dumps + prefs XML | — | — |
| S02 | Appearance → Shorts HUD | open screen | Exactly 3 options | **Brain / Counter / ShortsCap** with previews (127 / 200 mock) | **PASS** | UI dump (all 3 + mock previews) | — | — |
| S03 | HUD appearance persistence | select Counter → restart | Selection persists | LIVE_COUNTER persisted across force-stop; restored as selected | **PASS** | prefs + UI selected card | — | — |
| ST01 | Study schedule CRUD | (device UI exists) | Schedule screens functional | UI reachable via Activity → Reports/Schedule; **device-level timer lifecycle NOT fully exercised** (needs schedule + timed run; not completed in this pass) | **PARTIAL** | UI reachable; no timed run evidence | P2 | Real-device timed study run |
| M01 | Real app-usage collection | open app, dwell | Usage recorded | **NO UsageStatsManager path exists** — no usage rows produced; MonitoringService runs but the audit-confirmed engine is not implemented | **NOT IMPLEMENTED** | code search (no UsageStatsManager) + 0 usage rows | P1 | Implement real usage collection engine |
| SH01 | Shorts detection (YouTube) | open youtube.com/shorts on real device, dwell >5s, swipe | Shorts detected + counted | **0 events, 0 usage rows.** Root cause: this device's YouTube build reports Shorts inside `com.google.android.apps.youtube.app.watchwhile.InternalMainActivity`; the adapter only recognizes `Shell$ShortsActivity` | **FAIL** (on this build) | window class evidence + empty DB tables | P1 | Add current YouTube Shorts window classes to `YouTubeShortsAdapter` |
| SH02 | Cross-platform Shorts (IG/TikTok/Snap) | — | Platform adapters | Not testable on this device without those apps installed/signed-in; architecture exists but real detection unverified | **NOT TESTABLE** | — | P2 | Device tests per platform |
| SH03 | Shorts 3–5s rule | dwell < threshold vs > threshold | Threshold honored | Counting engine unit-tested; on-device counting never fires (SH01 blocks it) | **NOT VERIFIED ON DEVICE** | 0 rows | P1 | After SH01 fix, device re-test |
| H01 | HUD appears on Shorts | Shorts surface open | HUD overlay visible | HUD never appeared — detection never classified surface (SH01 root cause); overlay permission is granted (allow) | **NOT VERIFIED** (blocked by SH01) | no HUD window in `dumpsys window` | P1 | After SH01 fix, device re-test |
| H02 | HUD hidden elsewhere | Home/launcher | No HUD | No HUD window present on launcher/app screens | **PASS** | window dump | — | — |
| H03 | HUD drag + position persistence | — | Draggable, persisted | Not exercisable without HUD visible (H01) | **NOT TESTABLE** | — | P2 | With HUD visible |
| W01 | Blocked-domain configuration | add testblock20c.com | Rule added + shown Blocked | Added; listed under Blocked | **PASS** | UI dump | — | — |
| W02 | Domain detection | open blocked site in Chrome | Block attempted | **Chrome opened testblock20c.com unimpeded** — no detection/enforcement layer | **NOT IMPLEMENTED** | ChromeTabbedActivity in focus | P1 | Real-time web enforcement engine |
| W03 | Real-time enforcement (A/B/C/D) | — | Block works | `PlaceholderBlockingEngine.isAvailable=false`; no DNS/VPN/proxy/accessibility blocker exists | **NOT IMPLEMENTED** | code (BlockingEngine.kt) + device Chrome test | P1 | Implement enforcement |
| L01 | Background / lifecycle | force-stop while monitoring | State recovers | App relaunched cleanly; **a11y service binding cleared on force-stop** (re-enabled via settings — expected Android behavior; OEM may kill FGS) | **PASS** (with note) | logcat + settings | P2 | Document OEM behavior |
| L02 | Process recreation | force-stop → relaunch | Queue survives | Room DB (`shortscap.db`) + tables (`sync_queue`, `shorts_usage`, `shorts_events`) present after restart — P1-2 durable queue confirmed on-device | **PASS** | sqlite schema pulled from device | — | — |
| OF01 | Offline sync (queue durability) | kill app mid-queue | Pending data survives | Queue schema durable + P1-2 unit tests pass; **no queued rows on device because detection never fired** (SH01) — durability seam verified, data flow blocked by SH01 | **PARTIAL** | DB schema + unit tests | P1 | After SH01, offline test |
| R01 | Reports screen | Activity → Reports | Renders | Most Used Apps / Unlock Count rendered (demo data) | **PASS** | UI dump | — | — |
| R02 | Score / Rank | Rank tab | Renders | Rank 12, Score 86, podium (mock) | **PASS** | UI dump | — | — |
| R03 | Backend round-trip (reports/score/rank) | — | Real device data reaches backend | **No real data generated** (no monitoring/shorts/web enforcement) so nothing to sync; backend not running in this pass | **NOT TESTABLE** | — | P1 | Post-engines |
| E01 | Crash/ANR/exceptions | logcat throughout | None | 0 FATAL EXCEPTION / ANR for com.shortscap.app | **PASS** | logcat | — | — |
| B01 | Battery/performance | qualitative | No obvious drain | No runaway CPU/loops observed; MonitoringService FGS idle; media (Brain videos) never loaded (HUD hidden) | **PASS** (initial) | logcat + services | P3 | Full benchmark later |

---

## PASS Results

- C01–C07: install, launch, first-launch flow, 5-tab navigation, no startup crash, force-stop/relaunch, settings persistence.
- P01–P03: permission denied/granted handling, Accessibility service binding, MonitoringService foreground start.
- S01–S03: Settings screens render; Appearance → Shorts HUD shows exactly **Brain / Counter / ShortsCap** with previews; selection persists across restart (BRAIN ↔ LIVE_COUNTER round-trip verified in prefs + UI).
- H02: HUD correctly absent outside short-form surfaces.
- W01: blocked-domain configuration works (UI adds + lists rule).
- L01/L02: force-stop recovery; **Room durable queue schema present on-device (P1-2)**.
- R01/R02: Reports and Rank screens render.
- E01: no crashes/ANRs during the entire pass.
- B01: no obvious battery/perf issue in this pass.

## FAIL Results

- **SH01 — Shorts detection on this device's YouTube build.** The YouTube Shorts surface runs inside `com.google.android.apps.youtube.app.watchwhile.InternalMainActivity` on this device; `YouTubeShortsAdapter` only recognizes `Shell$ShortsActivity`. Result: Shorts content was open and actively swiped, yet 0 events / 0 usage rows were recorded (verified via the on-device Room DB). This is a genuine adapter-currency gap, not a counting bug.

## PARTIAL Results

- **ST01 — Study device run** not fully exercised (UI reachable; timed session run deferred).
- **OF01 — Offline sync** durability seam verified (Room queue + P1-2 unit tests), but no on-device queued data could be generated because detection never fired (SH01).

## NOT IMPLEMENTED Results

- **M01 — Real app-usage collection** (no `UsageStatsManager` path; audit-confirmed engine missing).
- **W02/W03 — Real-time web enforcement** (`PlaceholderBlockingEngine.isAvailable=false`; device Chrome opened a blocked domain unimpeded; no DNS/VPN/proxy/accessibility blocker).

## NOT TESTABLE Results

- **SH02** — cross-platform Shorts on this device (no IG/TikTok/Snapchat signed in).
- **H03** — HUD drag/position (HUD never visible; blocked by SH01).
- **R03** — backend round-trip for reports/score/rank (no real data generated + backend not running in this pass).

## BLOCKED Results

- **H01 — HUD visibility on Shorts** — blocked by SH01 (detection never classifies the surface).

---

## P1 Fixes Required (before real-device value / release candidate)

1. **Shorts detection adapter currency** — extend `YouTubeShortsAdapter` to recognize current YouTube Shorts window classes (`...watchwhile.InternalMainActivity` with Shorts context, plus keep `Shell$ShortsActivity`). Then re-verify SH01, SH03, H01/H03, OF01 on-device.
2. **Real app-usage collection engine** (M01) — implement the documented `UsageStatsManager`-based collector; wire into `MonitoringEventHub`/sync.
3. **Real-time web enforcement** (W02/W03) — implement a real blocking mechanism (VPN/DNS/proxy/accessibility) behind the existing `WebsiteBlockingEngine` seam; device-test blocked-domain enforcement.

## P2 Fixes Required (important, not first-RC-blocking)

- ST01 — full timed Study device run (schedule → start → break → end → history → sync).
- SH02 — per-platform Shorts device tests (IG Reels, TikTok, Snapchat Spotlight).
- H03 — HUD drag + position persistence with HUD visible.
- L01 — document OEM background/FGS-kill behavior (vivo aggressively kills; FGS type is specialUse).
- OF01 — offline queue test with real queued data once detection works.

## P3 Future Items

- Battery/performance benchmark (B01) once engines are live.
- Media (Brain video) playback verification on device once HUD is reachable.

---

## Real-Device Readiness Assessment

**Ready for real device (validated here):** install/launch/navigation/settings persistence, permissions flow (incl. Accessibility + Overlay + FGS), Shorts HUD settings UI, web rule-list UI, Reports/Rank/Score UI, durable sync queue storage.

**Needs real device (blocked in this pass):** Shorts detection/counting/HUD on current YouTube builds, real app-usage collection, real web enforcement, offline queue data flow, timed Study run.

**Bottom line:** the app is a stable, well-behaved UI shell with correct persistence and permission handling, but the three core Android engines (Shorts detection on current builds, app-usage collection, web enforcement) must be implemented/adjusted before real-device data value and any release-candidate claim.

---

## Recommended Phase 20D Work Order

1. **SH01 first** — update `YouTubeShortsAdapter` window-class set to match current YouTube builds (evidence: device `InternalMainActivity`); add unit tests for the new classes; rebuild; re-run device Shorts test (SH01 → SH03 → H01/H03 → OF01).
2. **M01** — implement real app-usage collection engine (`UsageStatsManager`) behind the existing seams; unit tests; device verification (C-M01…M09).
3. **W02/W03** — implement real web enforcement behind `WebsiteBlockingEngine`; device verification (W01–W03).
4. **ST01** — full timed Study device run.
5. **SH02** — cross-platform Shorts device matrix (IG/TikTok/Snap) as those apps become available.
6. Then re-run this Phase 20C matrix end-to-end for the remaining NOT VERIFIED rows.
