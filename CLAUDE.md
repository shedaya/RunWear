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

## PWA Hero Image System (v3.10)

Location: `runwearpwa22/index.php`

### Gender Functions (Important Distinction)

Two separate functions handle gender - DO NOT confuse them:

1. **`getHeroImageGender()`** - For hero images ONLY
   - Returns `MALE` or `FEMALE` (never `ALL`)
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

### Image Naming Convention

Format: `{GENDER}_{WEATHER}_{TEMP}_{TIME}_{INDEX}`

- **Gender**: `MALE`, `FEMALE`, `UNISEX`
- **Weather**: `CLEAR`, `CLOUDY`, `RAINY`, `SNOWY`
- **Temp**: `FREEZING`, `COLD`, `COOL`, `MILD`, `WARM`, `HOT`
- **Time**: `DAWN`, `MIDDAY`, `DUSK`, `NIGHT`
- **Index**: Sequential number (1, 2, 3...)

Example: `FEMALE_CLEAR_COLD_MIDDAY_1`

## Next Steps
- UI/UX improvements (planning in progress)
