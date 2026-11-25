package com.runwear.app.ui.screens

import com.runwear.app.makeitme.MakeItMeMode

/**
 * WS-5 (D-14) — the Settings inventory of record.
 *
 * The design's one inventory (frames 35–39; notes §8H: Appearance
 * first, the three preferences, Make It Me, Support, About) is
 * declared ONCE, here, in order. [SettingsSheetContent] composes
 * exactly this list — one LazyColumn item per entry, keyed by the
 * entry — and [sectionsFor] is the invariant the gate tests:
 *
 * **Every registered section renders in EVERY shipped
 * configuration.** A section composed conditionally may not emit
 * nothing: not when the Make It Me feature flag is OFF (its section
 * still carries the plain not-started row, frame 36), not in any of
 * the feature's three coded modes, and not when a host passes no
 * Make It Me view model. The Delete-my-data row is its own
 * registered section — reachable on its own, never through the
 * Make It Me section's emission (that coupling is what made it
 * unreachable in the D-14 build).
 */
enum class SettingsSectionId {
    APPEARANCE,
    TEMPERATURE_UNIT,
    BODY_TEMPERATURE,
    FIT_PREFERENCE,
    MAKE_IT_ME,
    DELETE_MY_DATA,
    SEND_FEEDBACK,
    RATE_RUNWEAR,
    HELP_IMPROVE,
    ABOUT
}

object SettingsInventory {

    /**
     * The full designed inventory, in designed order. GEN-5
     * (RW-28 items 24/25): the preferences follow the onboarding
     * order — Temperature unit, Fit preference, Body temperature
     * (A-18) — and the destructive section (DELETE_MY_DATA, now
     * rendered as the headed Danger zone) is quarantined LAST,
     * after About (B-16).
     */
    val SECTIONS: List<SettingsSectionId> = listOf(
        SettingsSectionId.APPEARANCE,
        SettingsSectionId.TEMPERATURE_UNIT,
        SettingsSectionId.FIT_PREFERENCE,
        SettingsSectionId.BODY_TEMPERATURE,
        SettingsSectionId.MAKE_IT_ME,
        SettingsSectionId.SEND_FEEDBACK,
        SettingsSectionId.RATE_RUNWEAR,
        SettingsSectionId.HELP_IMPROVE,
        SettingsSectionId.ABOUT,
        SettingsSectionId.DELETE_MY_DATA
    )

    /**
     * The sections a Settings surface renders for one configuration.
     * The parameters exist so the invariant is stated — and tested —
     * over the whole configuration space; the answer is the full
     * registry in every case. Any future configuration-dependent
     * section must change this function AND its gate test together.
     */
    fun sectionsFor(
        makeItMeEnabled: Boolean,
        makeItMeMode: MakeItMeMode
    ): List<SettingsSectionId> = SECTIONS

    /** About row's value, in iOS's form: "1.2.15 (30)". */
    fun versionLabel(versionName: String, versionCode: Int): String =
        "$versionName ($versionCode)"
}
