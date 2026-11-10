package com.runwear.shared.domain.model

import java.util.Locale

/**
 * LOC-1 WP-R2 (DESIGN §7, implementation of record) — the
 * locale/units policy.
 *
 * Two unit frames exist independently:
 * - TEMPERATURE: [TemperatureUnit] (Fahrenheit / Celsius);
 * - WIND: [WindUnit] (mph / km/h), DECOUPLED from the temperature
 *   unit in this slice — before it, TemperatureUnit silently
 *   bundled the wind frame (°F⇒mph, °C⇒km/h), so a user could not
 *   be e.g. °C with mph, and a locale default could not be stated
 *   for wind on its own. TemperatureUnit's wind members remain as
 *   the legacy pairing for call sites not yet migrated; new code
 *   reads the wind frame from preferences ([UserPreferences]).
 *
 * The DEFAULT for both frames derives from the device locale when
 * the user has never chosen: the imperial set (US, LR, MM —
 * the countries whose customary system is Fahrenheit/mph) defaults
 * to Fahrenheit + mph; every other locale defaults to Celsius +
 * km/h. An explicit user choice ALWAYS wins: the stored key's
 * presence, not its value, is what marks the choice as made, so a
 * user who deliberately picks the locale's own default is never
 * overridden by a later locale change either.
 *
 * en-US evidence (the shipped behaviour is unchanged there):
 * defaultTemperatureUnit(Locale.US) = FAHRENHEIT and
 * defaultWindUnit(Locale.US) = MPH — the same pair the shipped
 * build hardcoded — pinned by LocaleUnitsPolicyTest and by the
 * Robolectric preferences test under an en-US locale.
 */
enum class WindUnit(val label: String) {
    MPH("mph"),
    KMH("km/h");

    /** Project a canonical mph wind speed into this frame. */
    fun convert(speedMph: Double): Double = when (this) {
        MPH -> speedMph
        KMH -> speedMph * 1.609344
    }

    /** A canonical mph wind speed rendered in this frame, e.g. "4 mph" / "7 km/h". */
    fun format(speedMph: Double): String = "${convert(speedMph).toInt()} $label"

    companion object {
        /** The wind frame the temperature unit historically implied. */
        fun impliedBy(unit: TemperatureUnit): WindUnit = when (unit) {
            TemperatureUnit.FAHRENHEIT -> MPH
            TemperatureUnit.CELSIUS -> KMH
        }
    }
}

object LocaleUnitsPolicy {

    /** Countries on the Fahrenheit/mph customary system. */
    private val IMPERIAL_COUNTRIES = setOf("US", "LR", "MM")

    fun usesImperial(locale: Locale): Boolean =
        locale.country.uppercase(Locale.US) in IMPERIAL_COUNTRIES

    /** The temperature default for a device locale (no user choice yet). */
    fun defaultTemperatureUnit(locale: Locale): TemperatureUnit =
        if (usesImperial(locale)) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS

    /** The wind default for a device locale (no user choice yet). */
    fun defaultWindUnit(locale: Locale): WindUnit =
        if (usesImperial(locale)) WindUnit.MPH else WindUnit.KMH
}
