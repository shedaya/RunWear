# RunWear Development Notes

## Current Status
Build successful. App runs on Android device.

## Build Setup
- SDK location: `C:\Users\solhe\AppData\Local\Android\Sdk` (set in `local.properties`)
- Java: Android Studio bundled JBR

## Build Commands
```bash
cd RunWear-android

# Build debug APK
./gradlew.bat :app:assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch app
adb shell am start -n com.runwear.app/.MainActivity
```

## Completed Fixes
- Downloaded `gradle-wrapper.jar` (was missing)
- Created `gradle.properties` with AndroidX and memory settings
- Fixed Icons.Filled.Check → Text("✓") in BottomSheets.kt
- Removed PullToRefreshBox (not available in Material3 version) in MainScreen.kt
- Fixed Modifier.padding() chaining in MainScreen.kt

## Hero Image System (v3.12)

Locations:
- PWA: `runwearpwa22/index.php`
- Android: `RunWear-android/shared/.../HeroImageRepository.kt`
- iOS: `RunWear-iOS/RunWear/Services/HeroImageService.swift`
- watchOS: Shares iOS HeroImageService with `isWatch: true` parameter

### Gender Functions (Important Distinction)

Two separate functions handle gender - DO NOT confuse them:

1. **`getHeroImageGender()`** - For hero images ONLY
   - Returns `MALE` or `FEMALE` (never `ALL`/`UNISEX`)
   - When user preference is 'all': randomly picks MALE or FEMALE (50/50)
   - Purpose: Ensures variety in hero images when no preference set

2. **`getGenderPreference()`** - For affiliate links ONLY
   - Returns `all`, `male`, or `female` as stored in state
   - Purpose: Filters product recommendations by gender
   - DO NOT modify this function for hero image changes

### Hero Image Cascade (loadHeroImage)

Queries Supabase `generated_images` table with cascading fallback:

```
1. ${gender}_${weather}_${tempBracket}_${timeOfDay}  // Exact match
2. ${gender}_${weather}_${tempBracket}               // Any time of day
3. ${gender}_CLEAR_${tempBracket}                    // Clear weather fallback (if not already CLEAR)
4. ${oppositeGender}_${weather}_${tempBracket}       // Opposite gender same weather
5. ${oppositeGender}_CLEAR_${tempBracket}            // Opposite gender clear weather
```

If all queries fail, falls back to Unsplash API.

### Generation Prompts (v3.12)

Enhanced prompts now include detailed outfit descriptions matching the actual outfit recommendations:

```
Professional running photography, {gender} runner in motion, {weather} weather,
{temp} temperature, {time} lighting, urban trail or park setting, dynamic action shot,
high quality, sharp focus. OUTFIT: {detailed outfit description}
```

Outfit descriptions by temperature:
- **HOT**: tank top, split shorts, sunglasses, headband
- **WARM**: short sleeve tech shirt, running shorts, mesh cap
- **MILD**: long sleeve moisture-wicking shirt, shorts or capris
- **COOL**: quarter-zip pullover, tights, thin gloves, ear headband
- **COLD**: thermal base layer, insulated jacket, thermal tights, beanie, gloves, gaiter
- **FREEZING**: multiple thermal layers, heavy jacket with hood, thick tights, balaclava, mittens, visible breath

### Demand-Driven Replenishment (v3.11)

The system automatically grows the image library based on actual user demand:

**Replenishment triggers when:**
1. No images found at all → queue generation
2. Found via fallback (different gender) → queue for original gender
3. Exact matches < 5 variants → queue more variety

**Rate limiting:** 1 queue per user per 5 minutes (prevents abuse)

**Variant numbering:** `_v1`, `_v2`, `_v3`... auto-increments

**Result:** Popular combinations grow to 5+ variants automatically.

### Image Naming Convention

Format: `{GENDER}_{WEATHER}_{TEMP}_{TIME}_v{N}`

- **Gender**: `MALE`, `FEMALE`
- **Weather**: `CLEAR`, `CLOUDY`, `RAINY`, `SNOWY`
- **Temp**: `FREEZING`, `COLD`, `COOL`, `MILD`, `WARM`, `HOT`
- **Time**: `DAWN`, `MIDDAY`, `DUSK`, `NIGHT`
- **Variant**: `v1`, `v2`, `v3`...

Example: `FEMALE_CLEAR_COLD_MIDDAY_v1`

## iOS/watchOS Implementation (v3.11)

### File Structure
```
RunWear-iOS/
├── RunWear/
│   ├── Models/
│   │   ├── HeroImageModels.swift    # Hero enums & structs
│   │   ├── TemperatureUnit.swift    # °F/°C preference
│   │   ├── ComfortLevel.swift       # Body temp comfort
│   │   ├── GenderPreference.swift   # Updated with forHeroImage()
│   │   ├── Weather.swift            # Extended with hourly data
│   │   └── Outfit.swift             # Updated with subtag
│   ├── Services/
│   │   ├── SupabaseClient.swift     # Supabase integration
│   │   ├── FallbackImageProvider.swift  # 24 Unsplash URLs
│   │   ├── HeroImageService.swift   # 5-level cascade
│   │   └── WatchConnectivityManager.swift
│   ├── Theme/
│   │   ├── AppTheme.swift           # Dark theme, temp colors
│   │   ├── GlassMorphismModifiers.swift
│   │   └── StaggeredAnimation.swift
│   └── Views/
│       ├── HeroImageView.swift      # 75% hero section
│       ├── Components/              # WeatherPill, GenderSelector
│       ├── Modals/                  # Settings, DatePicker, etc.
│       └── Onboarding/
└── RunWearWatch/
    ├── WatchViewModel.swift         # Hero image support
    ├── WatchOutfitView.swift        # 3-page TabView layout
    ├── WatchSettingsView.swift      # Watch settings page
    └── WatchConnectivityManager.swift
```

### Key iOS Features
- Zero-lag hero: Shows Unsplash instantly, crossfades to AI (500ms)
- Dark theme with glass morphism (white @ 10%, border @ 15%)
- Temperature-colored accents (Purple→Indigo→Blue→Green→Orange→Red)
- Staggered animations (400ms duration, 50ms delay)
- Swipe-to-close modals (100px threshold)

### watchOS Features
- 3-page horizontal layout: Hero Weather → Outfit → Settings
- Circular hero image with 30% temp tint overlay
- 44pt minimum touch targets
- Watch Connectivity syncs preferences with iOS

## Next Steps
- UI/UX improvements (planning in progress)
