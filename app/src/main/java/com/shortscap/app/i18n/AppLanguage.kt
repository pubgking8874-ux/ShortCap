package com.shortscap.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Languages supported by the logged-in ShortsCap experience.
 *
 * [code] is the BCP-47 language tag used for asset lookup and future backend
 * sync; [flag] and [nativeName] are what the Language screen displays;
 * [isRtl] drives the layout direction (Urdu renders right-to-left).
 *
 * Adding a new language = append one enum entry + one [AppStrings]
 * implementation — no UI code changes anywhere else.
 */
enum class AppLanguage(
    val code: String,
    val flag: String,
    val nativeName: String,
    val isRtl: Boolean,
) {
    ENGLISH("en", "\uD83C\uDDEC\uD83C\uDDE7", "English", false),
    HINDI("hi", "\uD83C\uDDEE\uD83C\uDDF3", "\u0939\u093F\u0928\u094D\u0926\u0940", false),
    URDU("ur", "\uD83C\uDDF5\uD83C\uDDF0", "\u0627\u0631\u062F\u0648", true),
    CHINESE("zh", "\uD83C\uDDE8\uD83C\uDDF3", "\u4E2D\u6587", false),
    SPANISH("es", "\uD83C\uDDEA\uD83C\uDDF8", "Espa\u00F1ol", false),
    ;

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

/** Active app language, provided alongside [LocalAppStrings] in ShortsCapApp. */
val LocalAppLanguage = staticCompositionLocalOf<AppLanguage> { AppLanguage.ENGLISH }
