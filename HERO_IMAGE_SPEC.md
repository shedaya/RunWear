# RunWear Hero Image System Specification

## Overview

This document defines the hero image fetching, caching, and fallback logic for all RunWear platforms (PWA, Android, iOS, WatchOS, WearOS). The goal is **instant display** with **weather-accurate imagery** and **zero lag**.

---

## Architecture

### Image Sources (Priority Order)

1. **Supabase AI Images** - Best match, generated for specific conditions
2. **Weather-Aware Fallbacks** - Unsplash images matching temp + weather (24 options)
3. **Generic Fallback** - Single placeholder image (last resort)

### Combination ID Format

All platforms must use the same combination ID format for Supabase queries:

```
{GENDER}_{WEATHER}_{TEMP_BRACKET}_{TIME_OF_DAY}_{OUTFIT_HASH}
```

**Example:** `FEMALE_RAIN_COLD_MIDDAY_a1b2c3d4`

| Field | Values |
|-------|--------|
| GENDER | `MALE`, `FEMALE`, `UNISEX` |
| WEATHER | `CLEAR`, `CLOUDY`, `RAIN`, `SNOW`, `FOGGY`, `STORMY` |
| TEMP_BRACKET | `HOT`, `WARM`, `MILD`, `COOL`, `COLD`, `FREEZING` |
| TIME_OF_DAY | `DAWN`, `MIDDAY`, `DUSK`, `NIGHT` |
| OUTFIT_HASH | 8-char hex from sorted outfit item names |

---

## Weather Code Mapping

All platforms must map Open-Meteo weather codes consistently:

```
Code 0        → CLEAR
Code 1        → CLEAR
Code 2-3      → CLOUDY
Code 45-48    → CLOUDY (fog = cloudy for images)
Code 51-67    → RAIN (drizzle, rain)
Code 71-77    → SNOW
Code 80-82    → RAIN (showers)
Code 85-86    → SNOW (snow showers)
Code 95-99    → RAIN (thunderstorm)
```

**Implementation:**

```javascript
// JavaScript/TypeScript
function getWeatherCondition(code) {
    if (code === 0 || code === 1) return 'CLEAR';
    if ([2, 3, 45, 46, 47, 48].includes(code)) return 'CLOUDY';
    if (code >= 51 && code <= 67) return 'RAIN';
    if (code >= 71 && code <= 77) return 'SNOW';
    if (code >= 80 && code <= 82) return 'RAIN';
    if (code >= 85 && code <= 86) return 'SNOW';
    if (code >= 95) return 'RAIN';
    return 'CLEAR';
}
```

```kotlin
// Kotlin (Android/WearOS)
fun getWeatherCondition(code: Int): String = when {
    code == 0 || code == 1 -> "CLEAR"
    code in 2..3 || code in 45..48 -> "CLOUDY"
    code in 51..67 || code in 80..82 || code >= 95 -> "RAIN"
    code in 71..77 || code in 85..86 -> "SNOW"
    else -> "CLEAR"
}
```

```swift
// Swift (iOS/WatchOS)
func getWeatherCondition(_ code: Int) -> String {
    switch code {
    case 0, 1: return "CLEAR"
    case 2, 3, 45...48: return "CLOUDY"
    case 51...67, 80...82, 95...99: return "RAIN"
    case 71...77, 85, 86: return "SNOW"
    default: return "CLEAR"
    }
}
```

---

## Temperature Brackets

| Bracket | Fahrenheit | Celsius |
|---------|------------|---------|
| FREEZING | < 20°F | < -7°C |
| COLD | 20-35°F | -7 to 2°C |
| COOL | 35-50°F | 2 to 10°C |
| MILD | 50-65°F | 10 to 18°C |
| WARM | 65-80°F | 18 to 27°C |
| HOT | > 80°F | > 27°C |

**Always use "feels like" temperature, not actual temperature.**

---

## Weather-Aware Fallback Images (2D Matrix)

When no Supabase image exists, use these Unsplash fallbacks:

```javascript
const FALLBACK_IMAGES = {
    hot: {
        clear: 'photo-1571008887538-b36bb32f4571',   // Tank top, bright sunny
        cloudy: 'photo-1571019613454-1cb2f99b2d8b',  // Summer overcast
        rain: 'photo-1534258936925-c58bed479fcb',    // Summer rain
        snow: null  // Not possible
    },
    warm: {
        clear: 'photo-1486218119243-13883505764c',   // T-shirt sunny
        cloudy: 'photo-1558017487-06bf9f82613a',     // Warm overcast
        rain: 'photo-1534258936925-c58bed479fcb',    // Warm rain
        snow: null  // Not possible
    },
    mild: {
        clear: 'photo-1552674605-db6ffd4facb5',      // Light layers sunny
        cloudy: 'photo-1558017487-06bf9f82613a',     // Mild overcast
        rain: 'photo-1515191107209-c28698631303',    // Spring/fall rain
        snow: 'photo-1491002052546-bf38f186af56'     // Light snow
    },
    cool: {
        clear: 'photo-1476480862126-209bfaa8edc8',   // Long sleeves sunny
        cloudy: 'photo-1571019613454-1cb2f99b2d8b',  // Cool overcast
        rain: 'photo-1515191107209-c28698631303',    // Cool rainy
        snow: 'photo-1491002052546-bf38f186af56'     // Cool snow
    },
    cold: {
        clear: 'photo-1517649763962-0c623066013b',   // Jacket sunny
        cloudy: 'photo-1517649763962-0c623066013b',  // Cold overcast
        rain: 'photo-1519692933481-e162a57d6721',    // Cold rain/sleet
        snow: 'photo-1483921020237-2ff51e8e4b22'     // Snowy run
    },
    freezing: {
        clear: 'photo-1544899489-a083461b088c',      // Winter gear sunny
        cloudy: 'photo-1544899489-a083461b088c',     // Freezing overcast
        rain: 'photo-1519692933481-e162a57d6721',    // Freezing rain/sleet
        snow: 'photo-1418985991508-e47386d96a71'     // Heavy snow
    }
};

// Full URL format
const url = `https://images.unsplash.com/${photoId}?w=800&h=1200&fit=crop`;
```

### Fallback Selection Logic

```javascript
function getFallbackImage(tempBracket, weatherCode) {
    const weather = getWeatherConditionForFallback(weatherCode);
    const bracket = FALLBACK_IMAGES[tempBracket.toLowerCase()];

    if (!bracket) return FALLBACK_IMAGES.mild.clear;

    // Return matching weather, or clear as default
    return bracket[weather.toLowerCase()] || bracket.clear;
}

// Simplified weather for fallbacks (4 categories)
function getWeatherConditionForFallback(code) {
    if (code === 0 || code === 1) return 'clear';
    if ([2, 3, 45, 46, 47, 48].includes(code)) return 'cloudy';
    if (code >= 51 && code <= 67) return 'rain';
    if (code >= 71 && code <= 77) return 'snow';
    if (code >= 80 && code <= 82) return 'rain';
    if (code >= 85 && code <= 86) return 'snow';
    if (code >= 95) return 'rain';
    return 'clear';
}
```

---

## Fetching Strategy (Zero-Lag Architecture)

### The Problem with Sequential Loading

```
❌ BAD: Wait for Supabase → then show image
   Result: 2-5 second delay, user sees blank/spinner
```

### The Solution: Instant Fallback + Background Upgrade

```
✅ GOOD: Show fallback instantly → upgrade when Supabase returns
   Result: Image visible in <100ms, upgrades seamlessly
```

### Implementation Flow

```
1. Weather data received
   │
   ├─► IMMEDIATELY: Set hero image to weather-aware fallback
   │   - Use getFallbackImage(tempBracket, weatherCode)
   │   - Render UI with this image NOW
   │
   └─► BACKGROUND (async): Fetch from Supabase
       │
       ├─► If found: Crossfade to AI image (500ms transition)
       │
       └─► If not found: Keep fallback (already displayed)
```

### Code Pattern (All Platforms)

```javascript
// PWA Example
async function loadWeather() {
    const weather = await fetchWeather(lat, lon);
    const tempBracket = getTempBracket(weather.feelsLike);

    // INSTANT: Set weather-aware fallback
    currentHeroImageUrl = getFallbackImage(tempBracket, weather.weatherCode);
    render(); // Show immediately!

    // BACKGROUND: Try to get better image from Supabase
    loadHeroImageFromSupabase(); // Don't await!
}

async function loadHeroImageFromSupabase() {
    const combinationId = buildCombinationId(...);
    const aiImage = await fetchFromSupabase(combinationId);

    if (aiImage?.image_url) {
        // Crossfade to AI image
        currentHeroImageUrl = aiImage.image_url;
        updateHeroImageWithTransition();
    }
    // If no AI image, fallback is already showing - do nothing
}
```

---

## Update Triggers

The hero image MUST reload when ANY of these change:

| Trigger | Why | Action |
|---------|-----|--------|
| Location | Different weather | Full `loadWeather()` |
| Date | Different forecast | Full `loadWeather()` |
| Time | Different time_of_day | Full `loadWeather()` |
| Gender | Different combination ID | Call `loadHeroImage()` |
| Comfort | Different outfit → different hash | Call `loadHeroImage()` |

### PWA Implementation

```javascript
function setGender(g) {
    state.gender = (state.gender === g) ? 'all' : g;
    localStorage.setItem('gender', state.gender);
    updateSettingsGenderSelector();
    render();
    loadHeroImage(); // ← CRITICAL: Reload for new gender
}

function setComfort(val) {
    state.comfort = val;
    localStorage.setItem('comfort', val);
    if (state.weather) {
        state.outfit = getOutfitRecommendation(state.weather);
        loadHeroImage(); // ← CRITICAL: Outfit changed, reload image
    }
    renderComfortSelector();
    render();
}
```

### Android/iOS Implementation

```kotlin
// Android ViewModel
fun setGender(gender: Gender) {
    _uiState.update { it.copy(gender = gender) }
    // Reload hero image with new gender
    viewModelScope.launch { fetchHeroImage() }
}

fun setComfort(comfort: Int) {
    _uiState.update {
        val newOutfit = getOutfitRecommendation(it.weather, comfort)
        it.copy(comfort = comfort, outfit = newOutfit)
    }
    // Reload hero image with new outfit hash
    viewModelScope.launch { fetchHeroImage() }
}
```

---

## Caching Rules

### DO Cache (Long-lived)
- Unsplash fallback images (they never change)
- Supabase image URLs once loaded (the image itself)

### DO NOT Cache (Always Fresh)
- Supabase API responses (new images may be generated)
- Weather API responses
- Combination ID lookups

### Service Worker / Network Layer

```javascript
// PWA Service Worker - EXCLUDE from caching:
const BYPASS_CACHE = [
    'api.open-meteo.com',      // Weather API
    'nominatim.openstreetmap', // Geocoding
    'supabase.co'              // Hero image API ← CRITICAL!
];

if (BYPASS_CACHE.some(domain => url.includes(domain))) {
    return fetch(request); // Always network, never cache
}
```

```kotlin
// Android OkHttp - No caching for Supabase
val client = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val request = chain.request()
        if (request.url.host.contains("supabase")) {
            chain.proceed(request.newBuilder()
                .header("Cache-Control", "no-cache")
                .build())
        } else {
            chain.proceed(request)
        }
    }
    .build()
```

---

## Image Transitions

When upgrading from fallback to AI image, use smooth crossfade:

### PWA
```javascript
function updateHeroImageWithTransition() {
    const heroImg = document.querySelector('.hero-image');
    if (heroImg && heroImg.src !== currentHeroImageUrl) {
        heroImg.style.opacity = '0';
        heroImg.onload = () => {
            heroImg.style.transition = 'opacity 0.5s ease';
            heroImg.style.opacity = '1';
        };
        heroImg.src = currentHeroImageUrl;
    }
}
```

### Android (Coil)
```kotlin
AsyncImage(
    model = ImageRequest.Builder(context)
        .data(heroImageUrl)
        .crossfade(500)
        .build(),
    contentDescription = "Hero image"
)
```

### iOS (SwiftUI)
```swift
AsyncImage(url: URL(string: heroImageUrl)) { image in
    image.resizable()
} placeholder: {
    // Fallback already shown
}
.transition(.opacity.animation(.easeInOut(duration: 0.5)))
```

---

## Preloading Strategy

### PWA - Preload Common Conditions
```javascript
const PRELOAD = [
    FALLBACK_IMAGES.mild.clear,
    FALLBACK_IMAGES.mild.rain,
    FALLBACK_IMAGES.cool.clear,
    FALLBACK_IMAGES.cool.rain,
    FALLBACK_IMAGES.cold.clear,
    FALLBACK_IMAGES.cold.snow,
    FALLBACK_IMAGES.warm.clear,
    FALLBACK_IMAGES.hot.clear,
    FALLBACK_IMAGES.freezing.clear,
    FALLBACK_IMAGES.freezing.snow
];

PRELOAD.forEach(id => {
    if (id) new Image().src = `https://images.unsplash.com/${id}?w=800&h=1200&fit=crop`;
});
```

### Mobile - Prefetch on App Launch
```kotlin
// Android - Prefetch with Coil
val preloadUrls = listOf(
    getFallbackImage("MILD", "CLEAR"),
    getFallbackImage("COOL", "RAIN"),
    // ... etc
)
preloadUrls.forEach { url ->
    imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
}
```

---

## Watch Considerations (WearOS / WatchOS)

Watches have limited bandwidth and screen size. Optimizations:

1. **Smaller images**: Use `w=400&h=600` instead of `w=800&h=1200`
2. **Fewer preloads**: Only preload current conditions + 2 adjacent
3. **Longer cache**: Cache fallbacks aggressively (they're static)
4. **Skip AI images**: Consider using only fallbacks on watches for speed

```javascript
// Watch image URL
const watchUrl = `https://images.unsplash.com/${photoId}?w=400&h=600&fit=crop`;
```

---

## Error Handling

```javascript
async function loadHeroImage() {
    try {
        const aiImage = await fetchFromSupabase(combinationId);
        if (aiImage?.image_url) {
            currentHeroImageUrl = aiImage.image_url;
            updateHeroImageWithTransition();
        }
        // If no AI image, fallback already showing - silent success
    } catch (error) {
        console.warn('Hero image fetch failed, using fallback:', error);
        // Fallback already showing - no action needed
    }
}
```

**Never show error states for hero images.** The fallback system ensures something always displays.

---

## Testing Checklist

| Scenario | Expected Result |
|----------|-----------------|
| Cold + Rainy | Shows rain/sleet runner image |
| Hot + Clear | Shows tank top sunny runner |
| Freezing + Snow | Shows heavy winter snow runner |
| Change gender Male → Female | Image reloads within 1s |
| Change comfort level | Image reloads (outfit hash changed) |
| Change location | Full weather reload + new image |
| Change date/time | Full weather reload + new image |
| Offline mode | Cached fallback displays instantly |
| Supabase down | Fallback displays, no error shown |

---

## Summary

| Principle | Implementation |
|-----------|----------------|
| **Instant display** | Always show fallback immediately, upgrade in background |
| **Weather-accurate** | 2D matrix: 6 temps × 4 weather = 24 fallback options |
| **Zero lag** | Never wait for network before rendering |
| **Always fresh** | Don't cache Supabase API responses |
| **Smooth transitions** | 500ms crossfade when upgrading to AI image |
| **Trigger on all changes** | Location, date, time, gender, comfort all reload image |

---

## Files Changed (PWA Reference)

| File | Changes |
|------|---------|
| `sw.js` | Added `supabase.co` to cache bypass, bumped to v3.0 |
| `index.php` | 2D `DEFAULT_HERO_IMAGES`, `getWeatherConditionForFallback()`, `getDefaultHeroImage()`, `setGender()` calls `loadHeroImage()`, `setComfort()` calls `loadHeroImage()` |

---

## Backend: Varied Image Prompts (TODO)

**Current Issue:** All AI-generated images use similar prompts with "sidewalk" backgrounds, leading to repetitive imagery.

**Requested Enhancement:** Add variety to backgrounds and settings in the image generation prompts.

### Suggested Background Variations

The prompt generator should randomly select from diverse backgrounds:

```javascript
const BACKGROUNDS = [
    'city street with buildings in background',
    'urban park with trees',
    'waterfront boardwalk',
    'quiet suburban neighborhood',
    'scenic trail with nature',
    'downtown area with shops',
    'bridge with city skyline',
    'tree-lined avenue',
    'modern city plaza',
    'coastal path by the ocean'
];

const SURFACES = [
    'asphalt road',
    'concrete sidewalk',
    'paved trail',
    'brick pathway',
    'gravel path'
];
```

### Implementation Location

This change needs to be made in the **Supabase Edge Function** or wherever the image generation prompts are built (likely `buildImagePrompt()` function on the backend).

### Example Prompt Structure

```
A {gender} runner in their 30s running mid-stride along a {random_background}.
They are wearing {outfit_items} appropriate for {weather} {temp_bracket} weather.
Time of day: {time_of_day}. Surface: {random_surface}.

MOOD: {mood_based_on_conditions}
```

### Priority

Medium - Improves visual variety but not critical for functionality.
