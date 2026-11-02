package com.runwear.shared.domain.model

/**
 * RW-25: the user's appearance (theme) preference.
 *
 * SYSTEM follows the device appearance; LIGHT and DARK pin the app to
 * that appearance regardless of the device. Persisted by ordinal in
 * PreferencesRepository, mirroring [ComfortPreference] exactly — the
 * declaration order here is therefore part of the stored format:
 * SYSTEM must stay first (it is also the default for a missing key).
 */
enum class AppearancePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}
