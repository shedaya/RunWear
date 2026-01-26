# RunWear PWA

Weather-based running outfit recommendations as a Progressive Web App.

**Live:** [runwear.ai](https://runwear.ai)

## Features

- Real-time weather-based outfit recommendations
- GPS location detection with manual fallback
- Works offline after first visit
- Installable on Android, iOS, and desktop
- Gender-specific recommendations
- Comfort preference slider (run hot/cold)
- Amazon affiliate integration with platform tracking

## Tech Stack

- **Frontend:** Vanilla JavaScript (no framework)
- **Weather API:** [Open-Meteo](https://open-meteo.com/) (free, no API key)
- **Geocoding:** [Nominatim/OpenStreetMap](https://nominatim.org/) (free)
- **Hosting:** Namecheap shared hosting
- **PWA:** Service worker with network-first caching

## Project Structure

```
runwearpwa22/
├── index.php          # Main app (single-page)
├── manifest.json      # PWA manifest
├── sw.js              # Service worker
├── icon-*.png         # App icons (various sizes)
└── README.md          # This file
```

## Deployment

Upload all files to your web root (`public_html/`):

1. `index.php`
2. `manifest.json`
3. `sw.js`
4. All icon files (see Icons section)

### Required Icons

| File | Size | Purpose |
|------|------|---------|
| `favicon.ico` | Multi | Browser tab |
| `favicon-16.png` | 16x16 | Small favicon |
| `favicon-32.png` | 32x32 | Standard favicon |
| `apple-touch-icon.png` | 180x180 | iOS home screen |
| `icon-72.png` | 72x72 | PWA |
| `icon-76.png` | 76x76 | iPad |
| `icon-96.png` | 96x96 | PWA |
| `icon-120.png` | 120x120 | iPhone |
| `icon-128.png` | 128x128 | PWA |
| `icon-144.png` | 144x144 | PWA |
| `icon-152.png` | 152x152 | iPad |
| `icon-192.png` | 192x192 | Android/Chrome |
| `icon-384.png` | 384x384 | PWA large |
| `icon-512.png` | 512x512 | PWA splash |
| `icon-maskable-192.png` | 192x192 | Android adaptive |
| `icon-maskable-512.png` | 512x512 | Android adaptive |

Icons available in `runwear-pwa-icons.zip`.

## Configuration

### Affiliate Tracking

Edit these constants in `index.php`:

```javascript
const AFFILIATE_TAG = 'runwear-20';      // Amazon Associates tag
const PLATFORM_SUBTAG = 'pwa';            // Platform identifier
```

Platform subtags for analytics:
| Platform | Subtag |
|----------|--------|
| PWA | `pwa` |
| iOS | `ios` |
| watchOS | `watchos` |
| Android | `android` |
| Wear OS | `wearos` |

### GPS Timeout

Default: 15 seconds (configurable in `getLocation()` function)

```javascript
{ enableHighAccuracy: false, timeout: 15000, maximumAge: 300000 }
```

## Testing PWA Installation

### Chrome (Desktop)
1. Open DevTools (F12) → Application → Manifest
2. Check for errors
3. Look for install icon in address bar

### Chrome (Android)
1. Visit runwear.ai
2. Tap menu → "Add to Home Screen" or "Install"

### Samsung Internet
1. Visit runwear.ai
2. Tap menu → "Add page to" → "Home screen"
3. **Note:** All icons must load (404 = no install prompt)

### Safari (iOS)
1. Visit runwear.ai
2. Tap Share → "Add to Home Screen"

## Known Issues & Solutions

### GPS fails on first try
**Cause:** 5-second timeout too short for cold GPS fix
**Solution:** Increased to 15 seconds (v2.2.1)

### Manual location input not working
**Cause:** Modal re-render destroyed input value
**Solution:** Preserve input state, update only results div (v2.2.1)

### Zip code returns wrong country
**Cause:** Nominatim returns global results
**Solution:** Sort results with USA priority (v2.2.1)

### Samsung Internet won't install PWA
**Cause:** Missing icons or relative service worker path
**Solution:** Upload all icons, use `/sw.js` absolute path

### Google Play Protect blocks installation
**Cause:** WebAPK not recognized, requests location permission
**Solution:** Submit appeal at [Play Protect Appeals](https://support.google.com/googleplay/android-developer/contact/protectappeals)

## Version History

| Version | Date | Changes |
|---------|------|---------|
| v2.2.1 | Jan 2025 | GPS timeout 15s, manual input fix, USA zip priority, SW path fix |
| v2.2 | Jan 2025 | PWA installability fix, affiliate attribution, new icons |
| v2.1 | Jan 2025 | Location fallback flow, GPS timeout improvements |
| v2.0 | Jan 2025 | Gender toggle, comfort slider, offline mode |

---

## Cross-Platform Development Notes

These patterns apply to iOS, watchOS, Android, and Wear OS apps:

### Geocoding with USA Priority

```swift
// iOS/watchOS
let geocoder = CLGeocoder()
geocoder.geocodeAddressString(query, in: nil, preferredLocale: Locale(identifier: "en_US")) { placemarks, error in
    // Sort by country, USA first
}
```

```kotlin
// Android/Wear OS
val url = "https://maps.googleapis.com/maps/api/geocode/json?address=$query&region=us&key=$API_KEY"
// Sort results by country
```

### Input Validation

- Minimum 2 characters before searching
- Debounce 300ms to prevent API spam

### Affiliate Links

Always include platform subtag:
```
https://www.amazon.com/s?k=running+shoes&tag=runwear-20&ascsubtag=PLATFORM
```

---

## Related

- [RunWear Android App](../RunWear-android/)
- [Icon Assets](../runwearicons.zip)
