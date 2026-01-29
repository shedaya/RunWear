# RunWear Implementation Update v3.6

## Summary of Changes (PWA v2.9 → v3.6)

This document covers all changes made to the PWA that need to be replicated across Android, iOS, WearOS, and WatchOS platforms.

---

## 1. CRITICAL: Partial Matching for Hero Images

### The Problem

The database stores images with combination IDs like:
```
FEMALE_CLOUDY_COOL_NIGHT_v1
FEMALE_CLOUDY_COOL_NIGHT_v2
MALE_RAINY_COLD_MIDDAY_v1
```

But the app was computing outfit hashes and querying for:
```
FEMALE_CLOUDY_COOL_NIGHT_37fb2714  ← Never matched!
```

**Result:** AI images never loaded because exact match always failed.

### The Fix

Strip the last part (hash/variant) and match on first 4 parts only:

```javascript
// BEFORE (exact match - BROKEN)
const response = await fetch(
    `${SUPABASE_URL}/rest/v1/generated_images?combination_id=eq.${combinationId}`
);

// AFTER (partial match - WORKS)
const baseCombo = combinationId.split('_').slice(0, 4).join('_');
// FEMALE_CLOUDY_COOL_NIGHT_37fb2714 → FEMALE_CLOUDY_COOL_NIGHT

const response = await fetch(
    `${SUPABASE_URL}/rest/v1/generated_images?combination_id=like.${baseCombo}_*&limit=10`
);

// Pick random from results for variety
if (images.length > 0) {
    return images[Math.floor(Math.random() * images.length)];
}
```

### Implementation by Platform

**Android (Kotlin):**
```kotlin
suspend fun fetchHeroImage(combinationId: String): HeroImage? {
    // Extract base: FEMALE_CLOUDY_COOL_NIGHT from FEMALE_CLOUDY_COOL_NIGHT_37fb2714
    val baseCombo = combinationId.split("_").take(4).joinToString("_")

    val response = supabase.from("generated_images")
        .select()
        .like("combination_id", "${baseCombo}_%")
        .limit(10)
        .decodeList<HeroImage>()

    return response.randomOrNull()
}
```

**iOS (Swift):**
```swift
func fetchHeroImage(combinationId: String) async -> HeroImage? {
    // Extract base: FEMALE_CLOUDY_COOL_NIGHT from FEMALE_CLOUDY_COOL_NIGHT_37fb2714
    let parts = combinationId.split(separator: "_")
    let baseCombo = parts.prefix(4).joined(separator: "_")

    let response = try await supabase
        .from("generated_images")
        .select()
        .like("combination_id", pattern: "\(baseCombo)_%")
        .limit(10)
        .execute()

    return response.randomElement()
}
```

---

## 2. Weather-Aware Fallback Images (2D Matrix)

### The Problem

Old fallback images only considered temperature:
```javascript
// OLD - Only 6 images, ignores weather!
const DEFAULT_HERO_IMAGES = {
    hot: 'sunny-runner.jpg',
    warm: 'sunny-runner.jpg',
    mild: 'sunny-runner.jpg',
    cool: 'sunny-runner.jpg',
    cold: 'sunny-runner.jpg',
    freezing: 'sunny-runner.jpg'
};
```

**Result:** Cold rainy day showed sunny runner image.

### The Fix

2D matrix: temperature × weather = 24 fallback options:

```javascript
const DEFAULT_HERO_IMAGES = {
    hot: {
        clear: 'photo-1571008887538-b36bb32f4571',   // Tank top, sunny
        cloudy: 'photo-1571019613454-1cb2f99b2d8b',  // Summer overcast
        rain: 'photo-1534258936925-c58bed479fcb',    // Summer rain
        snow: null  // Not possible when hot
    },
    warm: {
        clear: 'photo-1486218119243-13883505764c',   // T-shirt sunny
        cloudy: 'photo-1558017487-06bf9f82613a',     // Warm overcast
        rain: 'photo-1534258936925-c58bed479fcb',    // Warm rain
        snow: null
    },
    mild: {
        clear: 'photo-1552674605-db6ffd4facb5',      // Light layers sunny
        cloudy: 'photo-1558017487-06bf9f82613a',     // Mild overcast
        rain: 'photo-1515191107209-c28698631303',    // Spring/fall rain
        snow: 'photo-1517483000871-1dbf64a6e1c6'     // Light snow
    },
    cool: {
        clear: 'photo-1476480862126-209bfaa8edc8',   // Long sleeves sunny
        cloudy: 'photo-1571019613454-1cb2f99b2d8b',  // Cool overcast
        rain: 'photo-1515191107209-c28698631303',    // Cool rainy
        snow: 'photo-1517483000871-1dbf64a6e1c6'     // Cool snow
    },
    cold: {
        clear: 'photo-1485727749690-d091e8284ef3',   // Jacket sunny winter
        cloudy: 'photo-1485727749690-d091e8284ef3',  // Cold overcast
        rain: 'photo-1519692933481-e162a57d6721',    // Cold rain/sleet
        snow: 'photo-1483921020237-2ff51e8e4b22'     // Snowy run
    },
    freezing: {
        clear: 'photo-1544899489-a083461b088c',      // Winter gear sunny
        cloudy: 'photo-1544899489-a083461b088c',     // Freezing overcast
        rain: 'photo-1519692933481-e162a57d6721',    // Freezing rain/sleet
        snow: 'photo-1516410529446-2c777cb7366d'     // Heavy snow
    }
};

// Full URL format
const url = `https://images.unsplash.com/${photoId}?w=800&h=1200&fit=crop`;
```

### Weather Condition Mapping for Fallbacks

```javascript
function getWeatherConditionForFallback(weatherCode) {
    if (weatherCode === 0 || weatherCode === 1) return 'clear';
    if ([2, 3, 45, 46, 47, 48].includes(weatherCode)) return 'cloudy';
    if (weatherCode >= 51 && weatherCode <= 67) return 'rain';
    if (weatherCode >= 71 && weatherCode <= 77) return 'snow';
    if (weatherCode >= 80 && weatherCode <= 82) return 'rain';
    if (weatherCode >= 85 && weatherCode <= 86) return 'snow';
    if (weatherCode >= 95) return 'rain';  // Thunderstorm
    return 'clear';
}

function getDefaultHeroImage(tempBracket, weatherCode) {
    const bracket = DEFAULT_HERO_IMAGES[tempBracket.toLowerCase()];
    if (!bracket) return DEFAULT_HERO_IMAGES.mild.clear;

    const weather = getWeatherConditionForFallback(weatherCode);
    return bracket[weather] || bracket.clear;
}
```

---

## 3. Hero Image Reload Triggers

The hero image must reload when ANY of these change:

| Trigger | Action Required |
|---------|-----------------|
| Location | Full `loadWeather()` → `loadHeroImage()` |
| Date | Full `loadWeather()` → `loadHeroImage()` |
| Time | Full `loadWeather()` → `loadHeroImage()` |
| **Gender** | Call `loadHeroImage()` directly |
| **Comfort** | Call `loadHeroImage()` directly |

### PWA Implementation

```javascript
function setGender(g) {
    state.gender = (state.gender === g) ? 'all' : g;
    localStorage.setItem('gender', state.gender);
    updateSettingsGenderSelector();
    render();
    loadHeroImage();  // ← ADDED: Reload for new gender
}

function setComfort(val) {
    state.comfort = val;
    localStorage.setItem('comfort', val);
    if (state.weather) {
        state.outfit = getOutfitRecommendation(state.weather);
        loadHeroImage();  // ← ADDED: Outfit changed, reload image
    }
    renderComfortSelector();
    render();
}
```

### Android/iOS Implementation

```kotlin
// Android
fun setGender(gender: Gender) {
    _uiState.update { it.copy(gender = gender) }
    viewModelScope.launch { fetchHeroImage() }  // ← Reload
}

fun setComfort(comfort: Int) {
    _uiState.update {
        val newOutfit = getOutfitRecommendation(it.weather, comfort)
        it.copy(comfort = comfort, outfit = newOutfit)
    }
    viewModelScope.launch { fetchHeroImage() }  // ← Reload
}
```

---

## 4. Service Worker / Network Caching

### DO NOT Cache These URLs

```javascript
const BYPASS_CACHE = [
    'api.open-meteo.com',       // Weather API
    'nominatim.openstreetmap',  // Geocoding
    'amazon.com',               // Affiliate links
    'supabase.co'               // Hero image API ← CRITICAL!
];
```

### PWA Service Worker (Simplified)

```javascript
const CACHE_NAME = 'runwear-v3.6';

// Install - skip precaching to avoid failures
self.addEventListener('install', event => {
    self.skipWaiting();
});

// Activate - clean old caches
self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys => {
            return Promise.all(
                keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))
            );
        }).then(() => self.clients.claim())
    );
});

// Fetch - Network first, cache fallback
self.addEventListener('fetch', event => {
    if (event.request.method !== 'GET') return;

    // Skip API requests (always fetch fresh)
    if (BYPASS_CACHE.some(domain => event.request.url.includes(domain))) {
        return;
    }

    event.respondWith(
        fetch(event.request)
            .then(response => {
                if (response.ok) {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
                }
                return response;
            })
            .catch(() => caches.match(event.request))
    );
});
```

---

## 5. Supabase Field Name

The `generated_images` table uses `image_url`, NOT `public_url`:

```javascript
// CORRECT
if (cachedImage && cachedImage.image_url) {
    currentHeroImageUrl = cachedImage.image_url;
}

// WRONG (was causing issues)
if (cachedImage && cachedImage.public_url) { ... }
```

---

## 6. UI Changes

### 6.1 Removed: Tap-to-Toggle Temperature Unit

The temperature display no longer toggles C/F when tapped:

```html
<!-- BEFORE -->
<div class="temp-display" onclick="toggleUnit()">

<!-- AFTER -->
<div class="temp-display">
```

Users change temperature unit in Settings only.

### 6.2 Added: Shop All Button

Added "Shop All" button to shop modal header:

```html
<div class="shop-modal-header">
    <div class="modal-title">🛒 Shop Your Outfit</div>
    <button class="shop-all-btn" onclick="shopAll()">Shop All →</button>
</div>
```

```javascript
function shopAll() {
    const weatherDesc = getWeatherDescription(state.weather.weatherCode);
    const searchTerm = `${weatherDesc} weather running gear`;
    window.open(buildAmazonLink(searchTerm), '_blank');
}

function getWeatherDescription(code) {
    if (code === 0 || code === 1) return 'sunny';
    if (code >= 2 && code <= 3) return 'cloudy';
    if (code >= 45 && code <= 48) return 'foggy';
    if (code >= 51 && code <= 67) return 'rainy';
    if (code >= 71 && code <= 77) return 'cold weather';
    if (code >= 80 && code <= 82) return 'rainy';
    if (code >= 85 && code <= 86) return 'snowy';
    if (code >= 95) return 'stormy';
    return 'all weather';
}
```

### 6.3 Changed: Humidity Icon

Changed from raindrop (looked like rain) to water-drop-with-lines:

```html
<!-- NEW humidity icon (distinct from rain) -->
<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
    <path d="M4 14c0-4 3-7 5-9 2 2 5 5 5 9a5 5 0 01-10 0z"/>
    <path d="M8 14h4"/>
    <path d="M7 17h6"/>
</svg>
```

Update in BOTH places:
1. Weather pill on main screen
2. Weather detail modal popup

---

## 7. Complete Hero Image Loading Flow

```
1. User loads app / changes location / date / time
   │
   ├─► loadWeather()
   │   ├─► Fetch weather from Open-Meteo
   │   ├─► Calculate tempBracket from feelsLike
   │   ├─► Get outfit recommendation
   │   │
   │   ├─► INSTANT: Set fallback image
   │   │   currentHeroImageUrl = getDefaultHeroImage(tempBracket, weatherCode)
   │   │   render()  // Show fallback NOW
   │   │
   │   └─► BACKGROUND: loadHeroImage()
   │       ├─► Build combinationId: FEMALE_CLOUDY_COOL_NIGHT_37fb2714
   │       ├─► Extract baseCombo: FEMALE_CLOUDY_COOL_NIGHT
   │       ├─► Query: combination_id LIKE 'FEMALE_CLOUDY_COOL_NIGHT_%'
   │       ├─► Pick random from results
   │       └─► If found: updateHeroImage() with 500ms crossfade
   │
2. User changes gender / comfort
   │
   └─► loadHeroImage() only (weather unchanged)
       └─► Same query flow as above
```

---

## 8. Testing Checklist

| Test Case | Expected Result |
|-----------|-----------------|
| Cold + Rainy location | Shows rainy runner fallback, then AI image if available |
| Hot + Clear location | Shows sunny tank-top fallback, then AI image |
| Change gender Male → Female | Hero image reloads within 2s |
| Change comfort level | Hero image reloads (outfit changes) |
| Change location | Full reload with new weather + image |
| Offline mode | Fallback image displays instantly |
| Supabase down | Fallback displays, no error shown |
| No AI image for combo | Fallback remains (no visible error) |

---

## 9. Version History

| Version | Changes |
|---------|---------|
| v2.9 | Starting point |
| v3.0 | Weather-aware 2D fallbacks, Supabase cache bypass |
| v3.1 | Field name fix (image_url) |
| v3.2 | Remove broken RPC calls, clean up |
| v3.3 | Shop All button, humidity icon, remove temp toggle |
| v3.4 | Debug logging, fix snow images |
| v3.5 | **CRITICAL: Partial matching fix** |
| v3.6 | Clean up debug logs, production ready |

---

## 10. Files Changed Summary

### PWA
| File | Key Changes |
|------|-------------|
| `sw.js` | Supabase cache bypass, simplified install |
| `index.php` | 2D fallbacks, partial matching, gender/comfort triggers, Shop All, humidity icon |

### To Update on Other Platforms
| Platform | Files to Update |
|----------|-----------------|
| Android | `HeroImageRepository.kt`, `MainViewModel.kt`, `HeroSection.kt` |
| iOS | `HeroImageService.swift`, `MainViewModel.swift`, `HeroView.swift` |
| WearOS | `HeroImageRepository.kt`, `MainViewModel.kt` |
| WatchOS | `HeroImageService.swift`, `MainViewModel.swift` |

---

## 11. Backend TODO: Varied Image Prompts

**Current Issue:** All AI-generated images use similar "sidewalk" backgrounds.

**Requested Enhancement:** Add variety to backgrounds in prompt generation.

```javascript
const BACKGROUNDS = [
    'city street with buildings in background',
    'urban park with trees',
    'waterfront boardwalk',
    'scenic trail with nature',
    'downtown area with shops',
    'bridge with city skyline'
];

// Random selection in prompt builder
const background = BACKGROUNDS[Math.floor(Math.random() * BACKGROUNDS.length)];
```

This is a Supabase Edge Function change, not client-side.
