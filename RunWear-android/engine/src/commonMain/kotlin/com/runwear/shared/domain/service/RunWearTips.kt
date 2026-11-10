package com.runwear.shared.domain.service

import com.runwear.shared.domain.model.TemperatureUnit

/**
 * LOC-1 WP-R2 — the recommendation tips, extracted.
 *
 * The tips are DISPLAY copy (WP-3 DESIGN §2.3 classification). Their
 * canonical home is now `:engine` moko resources (`tip_*` keys in
 * `moko-resources/base/strings.xml`); this object is the engine's
 * runtime facade over them — the same two-step the WP-3 pilot recorded
 * for the non-Compose policy layer (resources canonical now; the
 * facade's runtime resolution retires when the engine's copy layer
 * moves to StringDesc end-to-end, which needs a platform context the
 * engine deliberately does not hold). The app-side parity test
 * (TipsResourceParityTest) pins every value here to its resource
 * byte-for-byte, so the two can never drift.
 *
 * The frostbite tip is the one tip with a UNIT SLOT (DESIGN §2.3):
 * its threshold is the canonical 10°F decision value, rendered through
 * the DISPLAY unit policy ([TemperatureUnit]) — "10°F" under
 * Fahrenheit, "-12°C" under Celsius — never a degree value baked into
 * the string (audit §5.2: translated tips need unit-slot rendering,
 * not literal translation). The resource value is the template with
 * the slot; the facade value below is that same template.
 */
object RunWearTips {

    /** The canonical frostbite-threshold decision value (°F). */
    const val FROSTBITE_THRESHOLD_F = 10.0

    const val FEEL_COOL: String =
        "🏃 You should feel slightly cool stepping outside. If you're comfortable standing still, you're overdressed for running."
    const val LAYERED: String =
        "🧥 Your layered setup lets you regulate heat — unzip your mid-layer or shell if you warm up after the first mile."
    const val WIND_SHELL: String =
        "💨 A wind shell beats a heavy jacket for running. It blocks wind chill while letting you vent excess heat."
    const val FROSTBITE_TEMPLATE: String =
        "🥶 Cover all exposed skin — frostbite risk increases below %1\$s with wind. Vaseline on cheeks and nose helps."
    const val HEAT_RISK: String =
        "🌡️ High heat risk — consider running early morning or evening."
    const val HYDRATE_FULL: String =
        "💧 Hydrate well before, during, and after your run."
    const val HYDRATE_CARRY: String =
        "💧 Stay hydrated — consider carrying water."
    const val RAIN_CAP: String =
        "🌧️ Wear a cap under your hood to keep rain out of your eyes. Avoid cotton — it retains moisture."
    const val HUMIDITY: String =
        "💦 High humidity — sweat won't evaporate easily. Wear looser, lighter fabrics and hydrate more."
    const val UV: String =
        "☀️ UV is high — reapply sunscreen every 60-90 minutes if running longer than an hour."
    const val SHORTS_COLD: String =
        "🩳 Many runners still wear shorts at this temperature. Your legs generate plenty of heat — try it before adding tights."

    fun feelCool(): String = FEEL_COOL
    fun layered(): String = LAYERED
    fun windShell(): String = WIND_SHELL
    fun heatRisk(): String = HEAT_RISK
    fun hydrateFull(): String = HYDRATE_FULL
    fun hydrateCarry(): String = HYDRATE_CARRY
    fun rainCap(): String = RAIN_CAP
    fun humidity(): String = HUMIDITY
    fun uv(): String = UV
    fun shortsCold(): String = SHORTS_COLD

    /** The frostbite tip with its threshold rendered in [unit]. */
    fun frostbite(unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT): String =
        FROSTBITE_TEMPLATE.replace(
            "%1\$s", unit.formatTemperature(FROSTBITE_THRESHOLD_F)
        )
}
