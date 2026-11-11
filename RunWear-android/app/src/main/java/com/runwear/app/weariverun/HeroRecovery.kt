package com.runwear.app.weariverun

import com.runwear.shared.domain.model.TempBracket
import java.time.Instant
import java.time.ZoneId

/**
 * RW-166 (Nov 10, 2026) — the Android half of the iOS build-28
 * RW-159/RW-160 fix: entry→bank re-link recovery for entries that
 * settled with NO hero.
 *
 * THE DEFECT (diagnosis of record, verified against this tree —
 * the same shape the iOS lane root-caused on device): the settle
 * in HeroMainScreen persists `heroRef = uiState.effectiveHeroUrl`
 * and `heroDurableRef = HeroRefResolver.durableRefFor(that)`.
 * When no hero was served at settle time the display ref is null,
 * the durable derivation from null is null, and the entry carries
 * NO path — nothing exists to re-sign, at settle or at render,
 * ever. [HeroRefResolver] recovers only by path extraction from
 * a signed URL; empty stays empty. The collection cover rule
 * (newest entry WITH a loadable reference, else the newest
 * entry) then falls back to the newest entry's honest
 * placeholder, and the Share Studio's poster slot fails outright
 * ("Your Postcard couldn't be made") while the Kit Card — which
 * never touches the hero — renders fully. The photos exist
 * server-side in the user's bank; the entry simply never
 * recorded which one is its own.
 *
 * THE RECOVERY (iOS HeroRecovery semantics, Android code shape):
 * an entry persists enough identity to find its bank hero —
 * [WearIveRunDayEntry.itemKeys] (the engine items' keys; on this
 * platform `ClothingItem.key == name`, so the outfit signature
 * hash is the same one OutfitCombination.fromConditions keyed
 * the cell from), [WearIveRunDayEntry.feelsLikeF] (the settle
 * frame's comfort-adjusted feels-like — the same value the cell
 * was keyed from, so its temp bracket is EXACT), the condition
 * label (the weather, lossily: WeatherCode.description collapses
 * neighbouring WMO codes, so weather is a CANDIDATE SET, never a
 * guess), and the moment label / settled instant (the time of
 * day, likewise a candidate set). Candidates are tried in a
 * fixed priority — exact reconstruction first — against the bank
 * the client already reads (personal_heroes rows carry
 * combination_id + storage_path). A hit is the entry's OWN
 * banked hero for its OWN outfit at its OWN bracket; nothing is
 * ever substituted across outfits or brackets. No hit → the
 * entry stays honestly empty: a look settled against a
 * stock/scaffold serve, or a cell whose generation failed, has
 * no personal hero to recover. That no-bank-cell class is the
 * named unrecoverable class — never fabricate a photo for it.
 */
object HeroRecovery {

    /** The temp-bracket token for a feels-like value — the
     * shared engine's table ([TempBracket.fromFahrenheit]:
     * FREEZING <20, COLD 20–34, COOL 35–49, MILD 50–64,
     * WARM 65–79, HOT ≥80, on the Int-truncated value). */
    fun tempBracketToken(feelsLikeF: Double): String =
        TempBracket.fromFahrenheit(feelsLikeF).name

    /**
     * Weather tokens for the entry's persisted condition label,
     * most likely first. The label is the engine WeatherCode's
     * description ("Clear sky", "Mainly clear", "Partly cloudy",
     * "Overcast", "Fog", "Light drizzle", "Rain", "Snowfall",
     * "Thunderstorm", …); the engine maps WMO codes 0–1 → CLEAR
     * and 2–3 + fog → CLOUDY, so a label standing at that
     * boundary carries both candidates. Unrecognised labels
     * carry all four.
     */
    fun weatherTokens(conditionLabel: String): List<String> {
        val label = conditionLabel.lowercase()
        val primary: String? = when {
            label.contains("snow") -> "SNOW"
            label.contains("rain") || label.contains("drizzle") ||
                label.contains("thunder") || label.contains("shower") -> "RAIN"
            label.contains("cloud") || label.contains("fog") ||
                label.contains("overcast") -> "CLOUDY"
            label.contains("clear") || label.contains("sun") -> "CLEAR"
            else -> null
        }
        val tokens = mutableListOf<String>()
        if (primary != null) tokens += primary
        // Boundary neighbour: code 1 (Mainly clear → CLEAR) and
        // code 2 (Partly cloudy → CLOUDY) wear adjacent labels.
        if (primary == "CLEAR") tokens += "CLOUDY"
        if (primary == "CLOUDY") tokens += "CLEAR"
        for (token in listOf("CLEAR", "CLOUDY", "RAIN", "SNOW")) {
            if (token !in tokens) tokens += token
        }
        return tokens
    }

    /** The time-of-day token for a 24-hour clock hour — the
     * shared engine's table (TimeOfDay.fromHour: DAWN 5–9,
     * MIDDAY 10–16, DUSK 17–19, NIGHT 20–4). */
    fun timeToken(hour: Int): String =
        com.runwear.shared.domain.model.TimeOfDay.fromHour(
            ((hour % 24) + 24) % 24
        ).name

    /**
     * Time-of-day tokens for the entry, most likely first: the
     * recorded moment label when it carries one (a clock reading
     * "9 p.m." is the settle's own confirmed hour), else the
     * hour the entry last settled (its instant read in the
     * device zone — the same zone the settle stamped it in).
     * Legacy entries (settledAtMillis 0, no label) carry only
     * the full candidate set.
     */
    fun timeTokens(
        entry: WearIveRunDayEntry,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<String> {
        val primary: String? = when {
            !entry.momentLabel.isNullOrBlank() ->
                parseClockHour(entry.momentLabel)?.let { timeToken(it) }
            entry.settledAtMillis > 0L -> timeToken(
                Instant.ofEpochMilli(entry.settledAtMillis)
                    .atZone(zone).hour
            )
            else -> null
        }
        val tokens = mutableListOf<String>()
        if (primary != null) tokens += primary
        for (token in listOf("DAWN", "MIDDAY", "DUSK", "NIGHT")) {
            if (token !in tokens) tokens += token
        }
        return tokens
    }

    /** The 24-hour clock hour a [WearMomentLabel] reading names
     * ("9 p.m." → 21, "12 a.m." → 0), or null when the label is
     * not a clock reading. */
    internal fun parseClockHour(label: String): Int? {
        val match = CLOCK_LABEL.find(label.trim()) ?: return null
        val hour12 = match.groupValues[1].toInt()
        val isPm = match.groupValues[2].startsWith("p")
        return (hour12 % 12) + if (isPm) 12 else 0
    }

    private val CLOCK_LABEL = Regex("""^(\d{1,2})\s*([ap])\.m\.$""")

    /**
     * The outfit signature hash — the engine's own rule
     * (OutfitCombination.fromConditions: the sorted item names
     * joined with "-", String.hashCode rendered as unsigned hex,
     * last 8 chars, zero-padded). Item keys ARE item names on
     * this platform (ClothingItem.key == name).
     */
    fun outfitHash(itemKeys: List<String>): String =
        itemKeys.sorted().joinToString("-").hashCode()
            .toUInt().toString(16).takeLast(8).padStart(8, '0')

    /**
     * Candidate combination ids for the entry, in priority
     * order — the exact reconstruction first, then the same
     * weather at other times of day, then the boundary/other
     * weathers. The bracket and the outfit hash never vary:
     * both derive exactly from persisted values, and a hero
     * banked under a different bracket or outfit is a DIFFERENT
     * look, never this entry's photo.
     */
    fun candidateCombinationIds(
        entry: WearIveRunDayEntry,
        genderSegment: String,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<String> {
        if (entry.itemKeys.isEmpty()) return emptyList()
        val hash = outfitHash(entry.itemKeys)
        val bracket = tempBracketToken(entry.feelsLikeF)
        val ids = mutableListOf<String>()
        for (weather in weatherTokens(entry.conditionLabel)) {
            for (time in timeTokens(entry, zone)) {
                ids += "${genderSegment}_${weather}_${bracket}_${time}_$hash"
            }
        }
        return ids
    }

    /**
     * The entry's recovered durable reference (a bank storage
     * path), when recovery applies: the entry carries NO
     * loadable reference (an entry with one is never re-linked —
     * its own identity wins) and one of its candidate cells is
     * banked. [bankPaths] is the bank as the client reads it:
     * combination_id → storage_path.
     */
    fun recoveredDurableReference(
        entry: WearIveRunDayEntry,
        genderSegment: String,
        bankPaths: Map<String, String>,
        zone: ZoneId = ZoneId.systemDefault()
    ): String? {
        if (HeroRefResolver.hasLoadableReference(entry)) return null
        for (id in candidateCombinationIds(entry, genderSegment, zone)) {
            val path = bankPaths[id]
            if (!path.isNullOrBlank()) return path
        }
        return null
    }
}
