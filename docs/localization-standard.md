# Localization standard (Uptime Ventures — all apps)

This app follows the studio localization standard. The canonical document is
`uptime-localization-standard.md` in the Uptime release-prep i18n folder; this
file carries the operative rules so the repo is self-describing. The string
inventory for this app lives alongside it (`runwear-string-inventory.md`).

1. **Runtime-resolved copy.** Every user-facing string resolves at runtime from
   the platform's standard resource mechanism (Compose Multiplatform resources
   in KMP modules; the web framework's i18n layer on the PWA surface; Android
   `strings.xml` only where the platform forces it). No hard-coded user-facing
   literals or compile-time copy constants in new code.
2. **English is the source of truth.** Copy is authored in English first and
   must pass this repo's copy/contract tests in English. Translations never
   edit source copy.
3. **Glossary before translation.** Brand terms are never translated:
   RunWear, Premium, Uptime Ventures. Domain terms (garment and weather terms)
   get one fixed rendering per language, decided in the glossary before
   strings are translated.
4. **Grammar is machinery.** Plurals and composed templates — outfit
   recommendations above all — use the platform's plural/ICU facilities.
   Hand-coded plural branches are defects.
5. **Locale-aware formatting only.** Dates, times, numbers, and units render
   via platform locale formatters and unit settings (°F/°C by setting, never
   baked into strings). Hand-rolled formatters are defects.
6. **Backend contracts are a separate layer.** Strings exchanged with backend
   services (including the telemetry/feedback contract) are internal data and
   stay English regardless of UI language.
7. **Shared validation.** Every language drop passes placeholder/format
   parity, brand-term integrity, missing-string, length, RTL, and
   pseudo-localization checks.
8. **Listings are their own layer.** Store listing translation is independent
   of in-app string mechanics.
