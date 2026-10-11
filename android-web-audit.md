# RunWear: Android vs Web PWA Audit

**Date:** 2026-10-10
**Reference (correct):** `runwearpwa22/index.php` (5,374 lines)
**Target (needs alignment):** `RunWear-android/app/src/main/java/com/runwear/app/`

---

## 1. Item-Tap Behavior — CONFIRMED MISMATCH

**Web (correct):** Tapping an outfit card goes **directly** to Amazon. No intermediate UI.
- `index.php:5207` — outfit cards render with `onclick="shopItem({...})"`
- `index.php:4192-4194`:
  ```js
  function shopItem(item) {
      window.open(buildAmazonLink(item.search), '_blank');
  }
  ```
- Note: the PWA *does* contain a `showOutfitDetail()` bottom-sheet (`index.php:4821`), but it is **dead code** — never called anywhere. The live path is direct-to-Amazon.

**Android (wrong):** Tapping an outfit card opens an intermediate bottom sheet.
- `HeroMainScreen.kt:278` — `onClick = { selectedShopItem = items[index] }`
- `HeroMainScreen.kt:386-392` — `selectedShopItem?.let` renders `ShopSheet`
- `BottomSheets.kt` (`ShopSheet` composable) — shows emoji icon at 40.sp, item name, category, description, and a "Shop on Amazon" button. User must tap *again* to reach Amazon.

**Fix:** Change `HeroMainScreen.kt:278` to open the Amazon URL directly via `Intent(ACTION_VIEW)` (same as `ShopItemRow`'s onClick in `BottomSheets.kt`), bypassing `ShopSheet`. `ShopSheet` can be deleted or kept only if a detail view is wanted — but per Sol, it should go.

---

## 2. Shop All / Product Icons — CONFIRMED MISMATCH (two parts)

### 2a. Main outfit cards: SVG category icons (web) vs emoji (Android)

**Web:** Outfit cards show **SVG vector category icons**, not emojis.
- `index.php:5210` — `${getCategoryIcon(item.category)}`
- `index.php:3267-3274` — `categoryIcons` map: `head`, `top`, `bottom`, `hands`, `accessories` (inline SVG paths)
- The AI-generated thumbnail (`getClothingThumbnail`, `index.php:2933`) overlays on top when loaded; the SVG icon is the base layer.

**Android:** `OutfitCard.kt` shows `item.icon` (**emoji** like 👕) as the base/fallback layer, with the Coil-loaded AI thumbnail on top.
- `OutfitCard.kt` (~line 130): `Text(text = item.icon, style = TextStyle(fontSize = 24.sp))` shown when `!thumbnailLoaded`

**Fix:** Replace the emoji `Text` in `OutfitCard`'s thumbnail box with category SVG icons (port the 5 SVGs from `categoryIcons` to Compose `ImageVector`s or vector drawables), matching the web's base layer.

### 2b. Shop All sheet: thumbnails missing on Android

**Web:** Shop modal items show emoji + AI thumbnail overlay.
- `index.php:4173-4182` (`openShop`): each `.shop-item` renders `<span class="shop-item-icon">${item.icon}</span>` **plus** `<img class="shop-item-thumb" src="${thumbUrl}">` on top.

**Android:** `ShopItemRow` in `BottomSheets.kt` shows **emoji only** — no thumbnail `AsyncImage` at all.
- `BottomSheets.kt` (`ShopItemRow`): the 40.dp icon box contains only `Text(text = item.icon, fontSize = 20.sp)`. No Coil image.

**Fix:** Add the same Coil `AsyncImage` thumbnail loading used in `OutfitCard` to `ShopItemRow`'s icon box.

### 2c. Shop modal header & FTC disclosure text differ

| | Web (`index.php:2564-2570`) | Android (`BottomSheets.kt`, `ShopAllSheet`) |
|---|---|---|
| Title | "🛒 Shop Your Outfit" (emoji + sentence case) | "SHOP YOUR OUTFIT" (Bebas Neue, all caps, no emoji) |
| FTC text | "As an Amazon Associate, RunWear earns from qualifying purchases. When you click these links, we may earn a small commission at no extra cost to you." | "Affiliate Disclosure: As an Amazon Associate, RunWear earns from qualifying purchases. This helps support the app at no extra cost to you." |
| Close control | Explicit "Close" button (`btn btn-secondary`) | Drag handle + swipe only, no button |

---

## 3. Layout Differences

### 3a. Outfit section header
- **Web** (`index.php:5195-5199`): title "N Items for Your Run" (sentence case) left, "Shop" button (bag SVG icon + "Shop" text) right.
- **Android** (`HeroMainScreen.kt:~230`): title "N Items for Your Run" in **Bebas Neue 28.sp all-caps-ish styling**, "Shop" button with gradient + glow shadow. Same structure, different button treatment.

### 3b. Date picker layout
- **Web** (`index.php:4098-4108`): 7 days, each cell shows label ("Today"/"Tomorrow"/"Mon") **plus a sublabel** ("Oct 12") on a second line.
- **Android** (`BottomSheets.kt`, `DatePickerSheet`): 7 days in a **2-column grid**, single-line labels ("Today", "Tomorrow", "Mon, Oct 12"). Web appears to be a horizontal scroll row; Android is a 2-col grid.

### 3c. Tip card
- **Web** (`index.php:5232-5244`): "Pro Tip" (title case), shows **first tip only**; card is **tappable to expand** (`toggleTipCard`) revealing additional tips.
- **Android** (`OutfitCard.kt`, `TipsSection`): "PRO TIP" (all caps), shows **all tips stacked** at once, not expandable.

### 3d. Onboarding screen copy
| Element | Web (`index.php:2600-2630`) | Android (`OnboardingScreen.kt` + screenshots) |
|---|---|---|
| Temp label | "Temperature" / sublabel "How do you measure the weather?" | "Temperature Unit" (no sublabel) |
| Fit label | "Fit Preference" / sublabel "We'll tailor product recommendations" | "Fit Preference" (no sublabel) |
| Comfort label | "Do you get cold easily or overheat quickly?" / sublabel "We'll adjust outfit recommendations" | "Body Temperature" / sublabel "How do you typically feel when running?" |
| Comfort options | 🥶 / "Get cold" / "Neither" / "Overheat" / 🥵 | "Run Cold" / "Slightly Cold" / "Neutral" / "Slightly Hot" / "Run Hot" |
| CTA | "Let's Go! 🏃" button | (none — goes straight to location buttons) |

### 3e. Location search ("Use Current Location" card position)
- **Web** (`openShop`-era location modal): GPS button below the search field.
- **Android** (`LocationPickerSheet`): search field at top, results immediately below, divider, then "Use Current Location" card. Functionally equivalent; visual order matches.

---

## 4. Styling Differences

### 4a. Temperature display
- **Web** (`index.php:5158`): static text `${Math.round(w.feelsLike)}°` — no animation.
- **Android** (`HeroSection.kt`): **animated count-up** (800ms tween from temp−10 to actual) + 500ms color transition on bracket change. Android-only enhancement; visually different on load.

### 4b. "Actual: X°F" line
- Both show it, but **Android makes the temperature tappable** (`onTempClick = onToggleUnit` in `HeroSection`) to toggle °F/°C. Web's temp display is not clickable (unit toggle lives in Settings).

### 4c. Card corner radii / elevation
- **Web** outfit cards: 16px radius, `#1A1A1A` solid background (per Android's own code comments in `OutfitCard.kt`, which claim PWA alignment — the values match).
- **Android** "Shop" button: gradient + 8.dp glow shadow — web's `.shop-btn` is a flat teal pill (check `.shop-btn` CSS at `index.php` — visually simpler, no glow).

### 4d. Section title font
- **Web**: "N Items for Your Run" in default sans, sentence case.
- **Android**: Bebas Neue, 28.sp, letterSpacing 0.56.sp. Different typeface treatment for the same string.

---

## 5. Functional Differences

### 5a. Amazon search URL construction — DIFFERENT
**Web** (`index.php:2840-2847`, `buildAmazonLink`):
```js
const fullTerm = `premium ${genderPrefix}${searchTerm}`.trim();
// gender: "men's " / "women's " (with apostrophe)
encodeURIComponent(fullTerm)
```
- Prepends **"premium "** to every search
- Gender prefix uses apostrophe: "men's ", "women's "
- Full `encodeURIComponent` encoding
- `ascsubtag=pwa`

**Android** (`BottomSheets.kt`, `buildAmazonSearchUrl`):
```kotlin
val searchTerm = "${genderPrefix}running ${item.displayName}".replace(" ", "+")
// gender: "mens " / "womens " (no apostrophe)
```
- **No "premium" prefix**
- Gender prefix: "mens ", "womens "
- Naive space→`+` replacement (no encoding of special chars like `"` in `Short Shorts (3")`)
- `ascsubtag=android` (correct per-platform)

**Fix:** Align Android's query construction with web's (add "premium " prefix, apostrophe gender forms, proper URL encoding).

### 5b. Shop All search term — DIFFERENT
- **Web** (`index.php:4196-4201`, `shopAll`): builds a **weather-aware** term — `${weatherDesc} weather running gear` (e.g. "rainy weather running gear").
- **Android** (`BottomSheets.kt`, `buildShopAllUrl`): hardcoded `"running gear outfit"` regardless of weather.

**Fix:** Port the weather-description logic (`getWeatherDescription` at `index.php:4203`) into `buildShopAllUrl`.

### 5c. Weather detail popups — Android shows FEWER rows
| Popup | Web rows (`index.php:4715-4785`) | Android rows (`BottomSheets.kt`, `WeatherDetailSheet`) |
|---|---|---|
| Condition | Condition, Cloud Cover, **Visibility ("Good")**, Precipitation % | Cloud Cover, Temperature, Feels Like |
| Wind | Speed, Gusts, **Direction**, **Feels Like Impact** | Speed, Gusts |
| Humidity | Relative Humidity, **Dew Point**, Comfort Level, **Sweat Impact** | Relative Humidity, Comfort Level |
| Precipitation | Chance of Rain, Expected Amount (**"None"** when 0, **in/mm by unit**), **Recommendation**, **Running Impact** | Chance of Rain, Expected Amount (only if > 0, **always "mm"**) |
| UV | UV Index, **Level**, **Protection**, **Peak Hours** | UV Index, Risk Level |

Notable: Android hardcodes `"${weather.precipitation} mm"` even in imperial mode; web switches to inches.

### 5d. Share text — DIFFERENT
**Web** (`index.php:4877-4895`):
```
🏃 RunWear Recommendation

📍 {location}
🌡️ Feels like {temp}°F ({bracket})
{weather icon} {weather description}

What to wear:
• {item}
• {item}

💡 Tip: {tip}

Get your personalized running outfit at runwear.ai
```
**Android** (`HeroMainScreen.kt`, `shareOutfit`):
```
🏃 RunWear Outfit Recommendation

📍 {location}
🌡️ Feels like {temp}°F

{count} Items for Your Run:
• {item}

💡 Tip: {tip}

Get your personalized running outfit at runwear.app
```
Differences: title wording, web includes temp bracket + weather icon/description line, section header wording ("What to wear:" vs "N Items for Your Run:"), and **different URLs** (`runwear.ai` vs `runwear.app`).

### 5e. "Now" shortcut button
- **Web** (`index.php:5145-5153`): when viewing a non-current date/time, a "Now" pill with reset icon appears inside the datetime pill.
- **Android**: `HeroSection` accepts `onNowClick` — need to verify it's rendered conditionally the same way. (Prop exists; visual parity unconfirmed.)

---

## 6. Android-Only Features (flag, don't auto-remove)

1. **Home screen widget** (`ui/widget/OutfitWidget.kt`, Glance) — no PWA equivalent. Keep.
2. **Debug overlay** (`debug/DebugOverlay.kt`, `DebugManager.kt`) — bug-report tool with log send. Dev-only; keep.
3. **Animated temperature count-up** (`HeroSection.kt`) — web shows static temp. This is a polish win; consider porting to web rather than removing.
4. **Tappable temperature to toggle units** (`onTempClick`) — web requires going into Settings. Genuinely useful; consider porting to web.
5. **Haptic feedback** (`LocalHapticFeedback` in `HeroSection.kt`) — no web equivalent. Keep.
6. **Pull-to-refresh** — both have it (PWA: `.pull-refresh-indicator`; Android: `PullToRefreshBox`). Parity OK.

## 7. Web-Only Features (candidates to port to Android)

1. **Expandable tip card** (`toggleTipCard`, `index.php:5233`) — Android shows all tips flat.
2. **Weather-aware "Shop All" search term** (`shopAll`, `index.php:4196`).
3. **"premium " prefix on Amazon searches** (`buildAmazonLink`, `index.php:2843`).
4. **Richer weather detail rows** (visibility, wind direction, dew point, UV protection/peak hours — see 5c).
5. **GPS help section** (`renderGPSHelp`, `index.php:2687`) — troubleshooting UI when location permission is denied. Android's `PermissionScreen.kt` should be checked for equivalent guidance.

---

## Priority Fix List (for Sol's complaints)

1. **[P0]** Item tap → remove `ShopSheet` intermediate; open Amazon directly (`HeroMainScreen.kt:278`).
2. **[P0]** Outfit card icons → replace emoji base layer with SVG category icons (`OutfitCard.kt`; port `categoryIcons` from `index.php:3267`).
3. **[P0]** Shop All rows → add AI thumbnail overlay (`ShopItemRow` in `BottomSheets.kt`).
4. **[P1]** Amazon URL parity → "premium " prefix, apostrophe gender, proper encoding (`buildAmazonSearchUrl`); weather-aware Shop All term (`buildShopAllUrl`).
5. **[P1]** Weather detail rows → add missing rows; fix precipitation unit (mm → in when imperial).
6. **[P2]** Copy alignment → FTC disclosure text, tip card ("Pro Tip" + expandable), onboarding labels, share text + URL.
7. **[P2]** Date picker → match web's two-line (label + sublabel) cell style vs Android's 2-col grid.
