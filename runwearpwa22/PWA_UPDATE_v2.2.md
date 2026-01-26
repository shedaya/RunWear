# RunWear PWA v2.2 Update Instructions

## Changes Made

### 1. PWA Installability Fixed

**Problem:** App wasn't offering "Add to Home Screen" prompt.

**Fixed:**
- Added `id` field to manifest.json (required for Chrome)
- Added proper `scope` field
- Removed non-existent screenshot references
- Split icon purposes (`any` vs `maskable` - can't combine)
- Added all required meta tags for iOS Safari

**New meta tags added:**
```html
<meta name="apple-mobile-web-app-title" content="RunWear">
<meta name="application-name" content="RunWear">
<meta name="mobile-web-app-capable" content="yes">
```

### 2. Affiliate Platform Attribution

**Problem:** No way to distinguish PWA traffic from iOS/Android in Amazon dashboard.

**Solution:** Added `ascsubtag` parameter to affiliate links.

**Example URL now:**
```
https://www.amazon.com/s?k=premium+men's+running+tank+top&tag=runwear-20&ascsubtag=pwa
```

**Platform subtags:**
| Platform | ascsubtag |
|----------|-----------|
| PWA | `pwa` |
| iOS App | `ios` |
| Android App | `android` |
| Wear OS | `wearos` |

**Viewing in Amazon Dashboard:**
1. Go to Amazon Associates → Reports → Earnings Report
2. Look for "Tracking ID" or use the detail report
3. The `ascsubtag` appears in the "SubTag" column when you export detailed reports

### 3. Icons Updated

**New icon files needed** (from runwearicons.zip):

Upload these files to your web root alongside index.html:

| File | Size | Purpose |
|------|------|---------|
| `favicon.ico` | Multi-size | Browser tab icon |
| `favicon-16.png` | 16x16 | Small favicon |
| `favicon-32.png` | 32x32 | Standard favicon |
| `apple-touch-icon.png` | 180x180 | iOS home screen |
| `icon-72.png` | 72x72 | PWA small |
| `icon-76.png` | 76x76 | iPad |
| `icon-96.png` | 96x96 | PWA |
| `icon-120.png` | 120x120 | iPhone |
| `icon-128.png` | 128x128 | PWA |
| `icon-144.png` | 144x144 | PWA |
| `icon-152.png` | 152x152 | iPad |
| `icon-192.png` | 192x192 | Android Chrome |
| `icon-384.png` | 384x384 | PWA large |
| `icon-512.png` | 512x512 | PWA splash |
| `icon-maskable-192.png` | 192x192 | Android adaptive |
| `icon-maskable-512.png` | 512x512 | Android adaptive |

---

## Files to Upload

1. **index.html** - Updated with new meta tags and affiliate tracking
2. **manifest.json** - Fixed PWA manifest
3. **sw.js** - Updated service worker (v2.2)
4. **Icon files** - All icons from the pwa/ folder in runwearicons.zip

---

## Icon Files Location

Download icons from: https://github.com/shedaya/RunWear/raw/main/runwearicons.zip

Extract and upload the contents of the `pwa/` folder to your web root.

---

## Testing PWA Installability

### Chrome (Desktop)
1. Open DevTools (F12)
2. Go to Application → Manifest
3. Check for errors
4. Look for "Install" button in address bar

### Chrome (Android)
1. Visit runwear.ai in Chrome
2. Tap 3-dot menu
3. Look for "Add to Home Screen" or "Install app"

### Safari (iOS)
1. Visit runwear.ai in Safari
2. Tap Share button
3. Tap "Add to Home Screen"

---

## A/B Testing Strategy

### Current Setup (v2.2)
- **PWA:** 100% Amazon with `ascsubtag=pwa`

### Future Setup (when Nike approved)
For A/B testing, modify the affiliate config:

```javascript
// In index.html
const AFFILIATE_PARTNERS = {
    amazon: {
        tag: 'runwear-20',
        baseUrl: 'https://www.amazon.com/s?k='
    },
    nike: {
        tag: 'YOUR_NIKE_CJ_ID',
        baseUrl: 'https://www.nike.com/w?q='
    }
};

// Assign user to cohort on first visit
function getAffiliateCohort() {
    let cohort = localStorage.getItem('affiliate_cohort');
    if (!cohort) {
        // 50/50 split
        cohort = Math.random() < 0.5 ? 'amazon' : 'nike';
        localStorage.setItem('affiliate_cohort', cohort);
    }
    return cohort;
}
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| v2.2.1 | Jan 2025 | Zip code international fix, service worker path fix |
| v2.2 | Jan 2025 | PWA fix, affiliate attribution, new icons |
| v2.1 | Jan 2025 | Location fallback flow, GPS timeout fix |
| v2.0 | Jan 2025 | Gender toggle, comfort slider, offline mode |

---

## Learnings & Fixes (v2.2.1) - Apply to All Platforms

### 1. Geocoding: International Zip Codes with USA Priority

**Problem:** Nominatim API returns global results. US zip code "33180" matched postal codes in Spain, Kazakhstan, and France before USA.

**Solution:** Fetch more results, sort by country, prioritize USA.

**Implementation Pattern:**
```javascript
// 1. Fetch more results than needed (8 instead of 5)
const response = await fetch(
    `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&limit=8&addressdetails=1`
);

// 2. Map results and flag USA entries
const results = data.map(item => {
    const addr = item.address || {};
    const countryCode = addr.country_code || '';

    return {
        name: /* city name */,
        fullName: /* state, country */,
        lat: parseFloat(item.lat),
        lon: parseFloat(item.lon),
        isUSA: countryCode === 'us'  // Flag for sorting
    };
});

// 3. Sort USA to top, then trim to display count
const sortedResults = results
    .sort((a, b) => {
        if (a.isUSA && !b.isUSA) return -1;
        if (!a.isUSA && b.isUSA) return 1;
        return 0;
    })
    .slice(0, 5);
```

**Platform-Specific Notes:**

| Platform | API to Use | Notes |
|----------|------------|-------|
| PWA | Nominatim (free) | Already implemented |
| iOS / watchOS | Apple MapKit / CLGeocoder | Use `preferredLocale` for region bias |
| Android / Wear OS | Google Geocoding API | Use `region=us` param for bias (doesn't exclude) |

**Alternative Approach (Native Apps):**
```swift
// iOS/watchOS - CLGeocoder with region bias
let geocoder = CLGeocoder()
geocoder.geocodeAddressString(query, in: nil, preferredLocale: Locale(identifier: "en_US")) { placemarks, error in
    // Sort placemarks by country, USA first
}
```

```kotlin
// Android/Wear OS - Google Geocoding with region bias
val url = "https://maps.googleapis.com/maps/api/geocode/json?address=$query&region=us&key=$API_KEY"
// Still sort results by country to ensure USA first
```

---

### 2. Service Worker Registration Path

**Problem:** Samsung Internet (and some other browsers) failed to recognize PWA with relative service worker path.

**Solution:** Use absolute path for service worker registration.

```javascript
// Wrong - relative path
navigator.serviceWorker.register('sw.js')

// Correct - absolute path
navigator.serviceWorker.register('/sw.js')
```

**Applies to:** PWA only (native apps don't use service workers)

---

### 3. PWA Install Prompt Requirements by Browser

| Browser | Requirements |
|---------|-------------|
| Chrome | HTTPS, valid manifest, service worker, 192px icon |
| Samsung Internet | All above + ALL icons must load (strict), absolute SW path |
| Safari (iOS) | No install prompt - users must use Share → Add to Home Screen |
| Firefox | Limited PWA support on mobile |

**Checklist before deploying:**
- [ ] All icon URLs in manifest.json return 200 (not 404)
- [ ] Service worker registered with absolute path
- [ ] manifest.json has `id`, `scope`, `start_url` fields
- [ ] Site served over HTTPS

---

### 4. Platform Subtag Tracking (Affiliate Attribution)

Remember to use distinct `ascsubtag` values per platform:

| Platform | ascsubtag | Example |
|----------|-----------|---------|
| PWA | `pwa` | `&ascsubtag=pwa` |
| iOS App | `ios` | `&ascsubtag=ios` |
| watchOS App | `watchos` | `&ascsubtag=watchos` |
| Android App | `android` | `&ascsubtag=android` |
| Wear OS App | `wearos` | `&ascsubtag=wearos` |

---

### 5. Location Input Validation

**Minimum query length:** 2 characters (prevents API spam on single keystrokes)

```javascript
if (!query || query.length < 2) {
    return; // Don't search yet
}
```

**Debounce searches:** 300ms delay prevents excessive API calls while typing

```javascript
let searchTimeout = null;
function handleSearch(query) {
    if (searchTimeout) clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
        // Perform actual search
    }, 300);
}
```
