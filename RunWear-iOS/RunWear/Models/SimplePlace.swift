import Foundation

// MARK: - SimplePlace (WS-4 / RC-4 — the simple-place model)
//
// The app has exactly ONE place value, and this is it: a city plus
// its abbreviated region, derived ONCE at geocode time (or at
// manual selection) and consumed by every surface that names a
// place — the home header location line, the Wear I've Run
// narration in both states, the expanded entry subtitle, the
// collection chapter covers, the day/kit share artifacts, the
// weather-detail context, and the share text.
//
// The defect this retires (audit D-08/D-09, root cause RC-4): the
// raw reverse-geocoded string ("New York, New York" — city plus
// FULL region name, sometimes with a country third component) was
// the only place value in the system, so it reached rendered lines
// verbatim, duplicated the city in the region slot, and made the
// mini bar's one-line narration long enough to amputate at a comma.
//
// Foundation-only, like RW26CollectionModels, so the test target
// compiles this file directly and pins the derivation itself —
// what the tests pin is what the UI renders.

struct SimplePlace: Codable, Equatable, Hashable {
    /// The city (locality) name, whitespace-normalized.
    let city: String
    /// The ABBREVIATED region (e.g. "NY"), or nil when the place
    /// has no region component worth showing.
    let region: String?

    /// "New York, NY" — the only form a rendered line may carry.
    var displayName: String {
        guard let region, !region.isEmpty else { return city }
        return "\(city), \(region)"
    }

    /// Place identity: the simple form, trimmed and case-folded.
    /// A legacy raw string and its simple form canonicalize to the
    /// same key ("New York, New York" and "New York, NY" are the
    /// same place), so pre-model entries and post-model entries
    /// group, match, and trash as one.
    var key: String {
        displayName.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    // MARK: Derivation at geocode time

    /// Derive the simple place from geocode components — the city
    /// (CLPlacemark.locality) and the region
    /// (CLPlacemark.administrativeArea). The region is abbreviated
    /// HERE, once; no consumer re-derives or re-formats it.
    ///
    /// Returns nil when neither component carries a name (the
    /// caller falls back to its non-place label, e.g. "Current
    /// Location"). A region with no city stands alone as the city
    /// (a state-level geocode result still names a place).
    init?(city rawCity: String?, region rawRegion: String?) {
        let city = SimplePlace.normalized(rawCity)
        let region = SimplePlace.normalized(rawRegion)
        if city.isEmpty {
            guard !region.isEmpty else { return nil }
            self.city = region
            self.region = nil
            return
        }
        self.city = city
        if region.isEmpty {
            self.region = nil
            return
        }
        // Abbreviate once, here. A region whose abbreviation still
        // just repeats the city ("Singapore, Singapore") is
        // dropped; one that abbreviates away from it ("New York,
        // New York" → "NY") is kept.
        let abbreviated = SimplePlace.abbreviatedRegion(region, city: city)
        self.region = abbreviated.lowercased() == city.lowercased() ? nil : abbreviated
    }

    // MARK: Derivation from a stored / legacy display string

    /// Derive the simple place from a display string produced
    /// before this model existed (a persisted manual location, a
    /// stored Wear I've Run entry, a recents row): split on
    /// commas, keep the city (first component) and the region
    /// (second component, abbreviated), and DROP every further
    /// component — a country or county in a rendered line is the
    /// raw-geocode leak this model exists to stop.
    ///
    /// Single-component strings pass through as a city-only place
    /// ("Null Island" stays "Null Island"). Returns nil only for
    /// an empty / whitespace string.
    init?(rawDisplayName raw: String) {
        let parts = raw
            .split(separator: ",", omittingEmptySubsequences: true)
            .map { SimplePlace.normalized(String($0)) }
            .filter { !$0.isEmpty }
        guard let city = parts.first else { return nil }
        self.city = city
        if parts.count > 1 {
            let abbreviated = SimplePlace.abbreviatedRegion(parts[1], city: city)
            self.region = abbreviated.lowercased() == city.lowercased() ? nil : abbreviated
        } else {
            self.region = nil
        }
    }

    /// The simple form of any place string, for call sites that
    /// hold a string and need the rendered form. Unparseable
    /// (empty) input renders as the empty string, never the raw
    /// input.
    static func displayName(forRawDisplayName raw: String) -> String {
        SimplePlace(rawDisplayName: raw)?.displayName ?? ""
    }

    // MARK: - Normalization

    static func normalized(_ value: String?) -> String {
        guard let value else { return "" }
        return value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
    }

    // MARK: - Region abbreviation

    /// Abbreviate a region name:
    /// - an already-short region (≤ 4 characters, e.g. "NY",
    ///   "NSW") is kept, uppercased when it is all letters;
    /// - a region with a standard abbreviation (US states and
    ///   territories, Canadian provinces, Australian states) takes
    ///   it — this is what turns the geocoder's "New York" region
    ///   for the city of New York into "NY";
    /// - a multi-word region with no standard abbreviation takes
    ///   its initials ("Île de France" → "IDF");
    /// - a single-word region with no standard abbreviation
    ///   ("England", "Bavaria") is already simple and is kept.
    /// A region identical to the city is dropped by the callers
    /// when it cannot be abbreviated away from the city.
    static func abbreviatedRegion(_ region: String, city: String) -> String {
        if region.count <= 4 {
            let isLetters = region.allSatisfy { $0.isLetter }
            return isLetters ? region.uppercased() : region
        }
        if let standard = standardAbbreviations[region.lowercased()] {
            return standard
        }
        let words = region.split(whereSeparator: { $0.isWhitespace || $0 == "-" })
        if words.count > 1 {
            return words.compactMap { $0.first }.map { String($0).uppercased() }.joined()
        }
        return region
    }

    /// Standard region abbreviations, keyed by lowercased full
    /// name. US states + DC + territories, Canadian provinces and
    /// territories, Australian states and territories.
    static let standardAbbreviations: [String: String] = [
        // US states
        "alabama": "AL", "alaska": "AK", "arizona": "AZ", "arkansas": "AR",
        "california": "CA", "colorado": "CO", "connecticut": "CT", "delaware": "DE",
        "florida": "FL", "georgia": "GA", "hawaii": "HI", "idaho": "ID",
        "illinois": "IL", "indiana": "IN", "iowa": "IA", "kansas": "KS",
        "kentucky": "KY", "louisiana": "LA", "maine": "ME", "maryland": "MD",
        "massachusetts": "MA", "michigan": "MI", "minnesota": "MN", "mississippi": "MS",
        "missouri": "MO", "montana": "MT", "nebraska": "NE", "nevada": "NV",
        "new hampshire": "NH", "new jersey": "NJ", "new mexico": "NM", "new york": "NY",
        "north carolina": "NC", "north dakota": "ND", "ohio": "OH", "oklahoma": "OK",
        "oregon": "OR", "pennsylvania": "PA", "rhode island": "RI", "south carolina": "SC",
        "south dakota": "SD", "tennessee": "TN", "texas": "TX", "utah": "UT",
        "vermont": "VT", "virginia": "VA", "washington": "WA", "west virginia": "WV",
        "wisconsin": "WI", "wyoming": "WY",
        // US district + territories
        "district of columbia": "DC", "american samoa": "AS", "guam": "GU",
        "northern mariana islands": "MP", "puerto rico": "PR", "u.s. virgin islands": "VI",
        "us virgin islands": "VI", "virgin islands": "VI",
        // Canadian provinces + territories
        "alberta": "AB", "british columbia": "BC", "manitoba": "MB",
        "new brunswick": "NB", "newfoundland and labrador": "NL", "nova scotia": "NS",
        "ontario": "ON", "prince edward island": "PE", "quebec": "QC",
        "saskatchewan": "SK", "northwest territories": "NT", "nunavut": "NU",
        "yukon": "YT",
        // Australian states + territories
        "new south wales": "NSW", "queensland": "QLD", "south australia": "SA",
        "western australia": "WA", "victoria": "VIC", "tasmania": "TAS",
        "northern territory": "NT", "australian capital territory": "ACT",
    ]
}
