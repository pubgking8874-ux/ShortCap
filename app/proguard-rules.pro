# ShortsCap — ProGuard / R8 keep rules (Phase 19 hardening).
#
# PURPOSE: R8 is a resilience measure (smaller APK, harder to read), NOT
# secrecy — decompilation is never fully prevented. These are the explicit
# keep rules the app needs on top of the consumer rules shipped by Compose /
# AndroidX. If a new reflection/serialization dependency is added, register
# its keep rules here.

# The app is minified + resource-shrunk in release builds (see
# app/build.gradle.kts). Manifest-referenced components (activities,
# services, the accessibility service) and AAR consumer rules are handled
# automatically by AGP — no manual keeps are required for them.

# Keep the generated BuildConfig so BackendConfig can read DEBUG at runtime.
-keep class com.shortscap.app.BuildConfig { *; }
