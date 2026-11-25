import Foundation

/// WP-3 DESIGN §7 (iOS half): display date/time rendering goes
/// through locale formatters so a non-US locale sees its own
/// conventions. Patterns that feed persistence or the narration
/// grammar (day names inside MomentReading, `yyyy-MM-dd` stamps)
/// stay pinned — see the WP-R3 report's left-in-place register.
/// Under en-US every rendering here is byte-identical to the
/// en_US_POSIX patterns it replaces.
enum LocaleFormats {
    /// "3:42 PM" — the widget snapshot's clock (was en_US_POSIX
    /// "h:mm a"; now the user's locale, §7.3).
    static func timeHourMinute(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.dateFormat = "h:mm a"
        return formatter.string(from: date)
    }

    /// "6 PM" — the later-today by-line hour (was en_US_POSIX "h a",
    /// pinned to the viewed location's timezone).
    static func timeHour(_ date: Date, timeZone: TimeZone = .current) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.timeZone = timeZone
        formatter.dateFormat = "h a"
        return formatter.string(from: date)
    }
}
