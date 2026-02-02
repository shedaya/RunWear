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

### Generation Prompts (v3.14)

Prompts now use the **actual outfit recommendation items** instead of static descriptions per temperature bracket. This ensures the generated image matches what the app is suggesting to wear.

```
Professional running photography, {gender} runner in motion, {weather} weather,
{temp} temperature, {time} lighting, urban trail or park setting, dynamic action shot,
high quality, sharp focus. OUTFIT: {dynamic outfit from recommendation}
```

**v3.14 Change**: Previously used static outfit descriptions per temp bracket (e.g., COOL always = "quarter-zip, tights"). Now builds the description from actual `OutfitRecommendation` items, which consider:
- Temperature (adjusted for comfort preference)
- Wind conditions (adds windbreaker, gloves earlier)
- Rain (adds rain jacket)
- Humidity (affects bottom choice)
- UV index (sunglasses)

Example: 55°F rainy now generates "light long sleeve, rain jacket, running shorts" instead of generic "quarter-zip, tights".

Non-visible items (sunscreen, reflective gear) are filtered out of the prompt.

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

## Clothing Thumbnails System (v3.12)

AI-generated clothing item thumbnails displayed in outfit recommendations.

### Architecture
- **52 thumbnails total**: 26 clothing items × 2 genders (male/female)
- **Storage**: Supabase Storage bucket `clothing-thumbnails`
- **Format**: WebP, 1:1 aspect ratio, ~90% quality
- **Style**: Dark gray/charcoal clothing on pure black background, professional product photography

### URL Pattern
```
https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/public/clothing-thumbnails/{item-key}-{gender}.webp
```

Example: `tank-top-male.webp`, `thermal-tights-female.webp`

### PWA Integration (index.php)

```javascript
const CLOTHING_THUMBNAILS_URL = 'https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/public/clothing-thumbnails';

const CLOTHING_THUMBNAIL_KEYS = {
    TANK_TOP: 'tank-top',
    SHORT_SLEEVE: 'short-sleeve',
    // ... 26 items mapped
};

function getClothingThumbnail(itemKey) {
    const thumbnailKey = CLOTHING_THUMBNAIL_KEYS[itemKey];
    if (!thumbnailKey) return null;
    const gender = state.gender === 'female' ? 'female' : 'male';
    return `${CLOTHING_THUMBNAILS_URL}/${thumbnailKey}-${gender}.webp`;
}
```

### Generation Script

Located at `runwear-thumbnails/generate-thumbnails.js`:
- Uses Replicate Flux Dev model (~$0.025/image)
- Uploads directly to Supabase Storage
- Supports `--gender=male` or `--gender=female` filter
- Rate limited (12s between generations)

```bash
cd runwear-thumbnails
npm install
# Create .env with REPLICATE_API_TOKEN and SUPABASE_SERVICE_KEY
npm run generate
npm run generate -- --gender=female  # Generate one gender only
```

### Thumbnail Items
Tops: tank-top, short-sleeve, long-sleeve-light, thermal-long-sleeve
Outer: light-vest, light-jacket, insulated-jacket, rain-jacket, windbreaker
Bottoms: short-shorts, running-shorts, light-tights, thermal-tights
Head: running-cap, visor, headband, light-beanie, thermal-beanie, balaclava
Hands: light-gloves, thermal-gloves, mittens
Accessories: sunglasses, reflective-gear, neck-gaiter, sunscreen

## Direct Affiliate Links (v3.12)

Clicking an outfit item now opens the Amazon affiliate link directly instead of showing a detail modal. This reduces friction for the user.

- Removed: `showItemModal()` function and modal HTML
- Changed: Outfit card `onclick` now calls `shopItem()` directly
- Added: External link icon (arrow) on outfit cards to indicate clickable action

## Location Race Condition Fix (v3.12)

Fixed bug where manually selecting a location showed correct location name but weather/outfit data remained from original location.

**Problem**: Multiple overlapping `loadWeather()` calls could complete out of order, with stale responses overwriting fresh data.

**Solution**: Request ID counter pattern:
```javascript
let weatherRequestId = 0;

async function loadWeather() {
    const thisRequestId = ++weatherRequestId;
    // ... async operations ...
    if (thisRequestId !== weatherRequestId) return; // Discard stale
    // ... update state ...
}
```

## Next Steps
- UI/UX improvements (planning in progress)
