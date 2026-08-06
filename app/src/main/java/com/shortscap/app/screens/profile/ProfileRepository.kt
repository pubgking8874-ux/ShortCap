package com.shortscap.app.screens.profile

import com.shortscap.app.model.ProfileData

/**
 * Future backend integration point for the Profile screen.
 *
 * Placeholders only — nothing is wired to a network today:
 *  - [loadProfile]          -> GET  /profile          (replaces the local AppUiState.profile)
 *  - [updateProfile]        -> PUT  /profile          (replaces the local save in AppViewModel)
 *  - [uploadProfilePicture] -> POST /profile/picture  (replaces the placeholder toast)
 *
 * Swap the bodies for real API calls when the Python/AWS backend is connected;
 * the Profile UI and its navigation stay unchanged.
 */
object ProfileRepository {

    /** TODO(backend): fetch the signed-in user's profile. Local default for now. */
    fun loadProfile(): ProfileData = ProfileData()

    /** TODO(backend): persist the edited profile. */
    fun updateProfile(profile: ProfileData) = Unit

    /** TODO(backend): upload the picture chosen via Camera / Gallery. */
    fun uploadProfilePicture(uri: String) = Unit
}
