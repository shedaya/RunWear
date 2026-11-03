package com.runwear.shared.domain.model

/**
 * WS-4 (RC-4): the simple-place model — the representation the app
 * lacked (RW-27 D-08/D-09).
 *
 * Every surface that names a place renders THIS form — city plus an
 * abbreviated region, exactly as the design's lines use it
 * ("New York, NY · Now" on the home header, "Added New York, NY, Now"
 * in the Wear I've Run narration, the same form on collection chapter
 * covers). The raw reverse-geocoded string ("New York, New York") is
 * an input to geocoding, never a rendered line.
 *
 * A [SimplePlace] is derived ONCE, at geocode/search time (see
 * `WeatherRepository.reverseGeocodePlace` and the search result's
 * display name), and its [display] string is what state, persistence,
 * and every place-bearing surface carry from then on.
 * [fromRawDisplayString] is the normalization seam for strings that
 * predate the model (saved locations, stored collection entries,
 * recents): it is idempotent, so already-simple strings pass through
 * unchanged and raw strings can never survive it.
 */
data class SimplePlace(
    val city: String,
    /** Abbreviated region ("NY"), or null when the place has none to show. */
    val region: String? = null
) {
    /** The one rendered form: "City, ST", or "City" with no region. */
    val display: String
        get() = if (region.isNullOrBlank()) city else "$city, $region"

    override fun toString(): String = display

    companion object {

        /**
         * Derive the simple form from geocode parts. [region] is the
         * geocoder's full region name ("New York", "North Carolina",
         * "New South Wales"); [countryCode] is its ISO code ("us",
         * "ca", "au", …), used to pick the abbreviation table.
         */
        fun fromGeocode(city: String, region: String?, countryCode: String? = null): SimplePlace {
            val cleanCity = city.trim()
            val cleanRegion = region?.trim()?.takeIf { it.isNotEmpty() }
                ?: return SimplePlace(cleanCity, null)
            val abbreviated = abbreviateRegion(cleanRegion, countryCode)
            return when {
                abbreviated != null -> SimplePlace(cleanCity, abbreviated)
                // "Singapore, Singapore" / "Hong Kong, Hong Kong": the
                // region adds nothing once it cannot be abbreviated.
                cleanRegion.equals(cleanCity, ignoreCase = true) -> SimplePlace(cleanCity, null)
                // A short or single-word region is already simple
                // ("London, England", "Munich, Bavaria").
                !cleanRegion.contains(' ') || cleanRegion.length <= 3 ->
                    SimplePlace(cleanCity, cleanRegion)
                // A long multi-word region with no known abbreviation
                // is exactly the raw-geocode verbosity the simple form
                // exists to remove ("Paris, Île-de-France" -> "Paris").
                else -> SimplePlace(cleanCity, null)
            }
        }

        /**
         * Normalize a place string of unknown provenance ("City, Full
         * Region", a raw Nominatim display fragment, or an already
         * simple "City, ST") into the simple form. Idempotent.
         * Strings with no comma pass through trimmed (sentinels such
         * as "Your Location" are unaffected).
         */
        fun fromRawDisplayString(raw: String): SimplePlace {
            val parts = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> SimplePlace(raw.trim(), null)
                parts.size == 1 -> SimplePlace(parts[0], null)
                else -> fromGeocode(parts[0], parts[1], countryCode = null)
            }
        }

        /**
         * The region's abbreviation, or null when none is known.
         * Already-abbreviated regions (2–3 letters) pass through
         * upper-cased; full names resolve through the US / Canadian /
         * Australian tables (the tables are name-disjoint, so a null
         * [countryCode] may search all of them).
         */
        fun abbreviateRegion(region: String, countryCode: String? = null): String? {
            val clean = region.trim()
            if (clean.isEmpty()) return null
            // Already an abbreviation: 2 letters in any case, or a
            // short all-caps token ("NY", "NSW"). A short mixed-case
            // word ("Goa") is a full name, not an abbreviation.
            if (clean.length <= 3 && clean.none { it == ' ' } &&
                (clean.length == 2 || clean.all { !it.isLetter() || it.isUpperCase() })
            ) return clean.uppercase()
            val key = clean.lowercase()
            val cc = countryCode?.lowercase()
            if (cc == null || cc == "us") US_REGIONS[key]?.let { return it }
            if (cc == null || cc == "ca") CA_REGIONS[key]?.let { return it }
            if (cc == null || cc == "au") AU_REGIONS[key]?.let { return it }
            return null
        }

        private val US_REGIONS = mapOf(
            "alabama" to "AL", "alaska" to "AK", "arizona" to "AZ", "arkansas" to "AR",
            "california" to "CA", "colorado" to "CO", "connecticut" to "CT", "delaware" to "DE",
            "district of columbia" to "DC", "florida" to "FL", "georgia" to "GA", "hawaii" to "HI",
            "idaho" to "ID", "illinois" to "IL", "indiana" to "IN", "iowa" to "IA",
            "kansas" to "KS", "kentucky" to "KY", "louisiana" to "LA", "maine" to "ME",
            "maryland" to "MD", "massachusetts" to "MA", "michigan" to "MI", "minnesota" to "MN",
            "mississippi" to "MS", "missouri" to "MO", "montana" to "MT", "nebraska" to "NE",
            "nevada" to "NV", "new hampshire" to "NH", "new jersey" to "NJ", "new mexico" to "NM",
            "new york" to "NY", "north carolina" to "NC", "north dakota" to "ND", "ohio" to "OH",
            "oklahoma" to "OK", "oregon" to "OR", "pennsylvania" to "PA", "puerto rico" to "PR",
            "rhode island" to "RI", "south carolina" to "SC", "south dakota" to "SD",
            "tennessee" to "TN", "texas" to "TX", "utah" to "UT", "vermont" to "VT",
            "virginia" to "VA", "washington" to "WA", "west virginia" to "WV",
            "wisconsin" to "WI", "wyoming" to "WY"
        )

        private val CA_REGIONS = mapOf(
            "alberta" to "AB", "british columbia" to "BC", "manitoba" to "MB",
            "new brunswick" to "NB", "newfoundland and labrador" to "NL",
            "northwest territories" to "NT", "nova scotia" to "NS", "nunavut" to "NU",
            "ontario" to "ON", "prince edward island" to "PE", "quebec" to "QC",
            "saskatchewan" to "SK", "yukon" to "YT"
        )

        private val AU_REGIONS = mapOf(
            "australian capital territory" to "ACT", "new south wales" to "NSW",
            "northern territory" to "NT", "queensland" to "QLD", "south australia" to "SA",
            "tasmania" to "TAS", "victoria" to "VIC", "western australia" to "WA"
        )
    }
}
