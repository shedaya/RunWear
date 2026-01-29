# RunWear Implementation Guide

This document captures the complete design system, features, and implementation details from the PWA v2.9. Use this to ensure consistency across Android, iOS, watchOS, and Wear OS apps.

---

## Table of Contents
1. [Design Tokens (Colors)](#design-tokens)
2. [Typography](#typography)
3. [Component Specifications](#component-specifications)
4. [Feature Implementations](#feature-implementations)
5. [Hero Image System](#hero-image-system)
6. [Weather Display](#weather-display)
7. [User Preferences](#user-preferences)
8. [Affiliate Integration](#affiliate-integration)
9. [UX Patterns](#ux-patterns)

---

## Design Tokens

### Color Palette

```
// Background Colors
--bg-dark: #0A0A0A          // Main app background
--bg-card: #1A1A1A          // Outfit cards (SOLID, not glass)
--bg-card-light: #262626    // Hover/pressed states, toggle backgrounds

// Text Colors
--text-primary: #FFFFFF     // Main text
--text-secondary: #B3B3B3   // Descriptions, subtitles
--text-muted: #737373       // Hints, chevrons

// Brand Colors
--primary: #00796B          // Teal - buttons, selected states
--primary-dark: #004D40     // Darker teal
--primary-light: #4DB6AC    // Lighter teal - gradients, labels
--primary-glow: rgba(0, 121, 107, 0.3)  // Button shadows

// Category Colors (for outfit item icons)
--cat-head: #2E7D32         // Green - hats, headwear
--cat-top: #00796B          // Teal - shirts, jackets
--cat-bottom: #1565C0       // Blue - shorts, pants
--cat-hands: #5E35B1        // Purple - gloves
--cat-accessories: #E65100  // Orange - sunglasses, etc.

// Glass Morphism (weather pills only, NOT outfit cards)
--glass-background: rgba(255, 255, 255, 0.1)   // 10% white
--glass-border: rgba(255, 255, 255, 0.15)      // 15% white
```

### Temperature Bracket Colors

```
Freezing (<20°F):  Blue tint overlay
Cold (20-34°F):    Light blue tint
Cool (35-49°F):    Cyan/teal tint
Mild (50-64°F):    Green tint
Warm (65-79°F):    Orange tint
Hot (80°F+):       Red/warm tint
```

---

## Typography

### Fonts
- **Display Font**: Bebas Neue (temperature, section headers)
- **Body Font**: System default (San Francisco on iOS, Roboto on Android)

### Text Styles

```
// Temperature Display
font-family: Bebas Neue
font-size: 96px (mobile), scales with screen
font-weight: 400

// Section Header ("5 Items for Your Run")
font-family: Bebas Neue
font-size: 28px
letter-spacing: 0.02em (0.56px at 28px)

// Outfit Card Name
font-size: 15px
font-weight: 600 (SemiBold)

// Outfit Card Category
font-size: 13px
font-weight: 400
color: --text-secondary

// Weather Pill Text
font-size: 13px
font-weight: 500 (Medium)

// Pro Tip Label
font-size: 14px
font-weight: 700
text-transform: uppercase
letter-spacing: 0.5px
color: --primary-light
```

---

## Component Specifications

### Outfit Card

```
Container:
- background: #1A1A1A (SOLID, not glass)
- border-radius: 16px
- padding: 14px
- gap: 14px (between icon, text, chevron)

Icon Container:
- size: 48x48px
- border-radius: 14px
- background: category color at 15% opacity

Icon:
- size: 24px
- color: category color (full opacity)

Chevron:
- size: 18px
- color: --text-muted

Press State:
- background: #262626
- transform: translateX(4px)

Animation:
- slideUp 0.4s ease
- stagger: 50ms per card (index * 50ms delay)
```

### Gender Toggle

```
Container:
- background: #262626
- border-radius: 22px
- padding: 3px
- gap: 2px

Option:
- min-width: 50px (for text labels)
- height: 32px
- border-radius: 16px
- font-size: 12px
- font-weight: 600
- padding: 0 12px

Selected State:
- background: #00796B (--primary)
- color: white

Unselected State:
- background: transparent
- color: #B3B3B3 (--text-secondary)

Behavior:
- Toggle: tap selected option again to deselect
- Deselected state = "all" (unisex) in backend
- Labels: "Male" / "Female" (text, not icons)
```

### Weather Pill

```
Container:
- background: rgba(255, 255, 255, 0.1)
- border-radius: 100px (pill shape)
- padding: 8px 12px
- gap: 6px

Icon:
- size: 14px
- opacity: 0.8

Text:
- font-size: 13px
- font-weight: 500
- color: white
```

### Shop Button

```
- background: linear-gradient(135deg, #00796B, #4DB6AC)
- border-radius: 50px (pill)
- padding: 10px 18px
- box-shadow: 0 4px 16px rgba(0, 121, 107, 0.3)

Icon:
- size: 16px
- color: white

Text:
- font-size: 13px
- font-weight: 600
- color: white
```

### Pro Tip Card

```
Container:
- background: linear-gradient(135deg, rgba(0,121,107,0.15), rgba(0,121,107,0.05))
- border: 1px solid rgba(0, 121, 107, 0.2)
- border-radius: 16px
- padding: 18px

Icon Container:
- size: 32px
- border-radius: 10px
- background: #00796B (solid)

Icon:
- Lightbulb
- size: 18px
- color: white

Label:
- "PRO TIP"
- uppercase
- color: #4DB6AC (--primary-light)

Text:
- font-size: 14px
- line-height: 1.5 (21px)
- color: #B3B3B3 (--text-secondary)
```

---

## Feature Implementations

### 1. Onboarding Flow

**Screen Content:**
- Logo: "Run**Wear**" (Wear in accent color)
- Title: "Welcome! 👋"
- Subtitle: "Let's prepare your perfect run. You can always change these later."

**Preferences Collected:**
1. Temperature Unit (°F / °C)
   - Default: °F for US locale, °C for others
   - Full-width toggle buttons

2. Fit Preference (Male / Female)
   - Default: neither selected (backend: "all"/unisex)
   - Toggleable: tap again to deselect
   - Text labels, not icons

3. Body Temperature (comfort slider)
   - Options: "Run Cold" / "Slightly Cold" / "Neutral" / "Slightly Hot" / "Run Hot"
   - Maps to: -10, -5, 0, +5, +10 temperature adjustment

**Storage:**
- localStorage (web) / UserDefaults (iOS) / SharedPreferences (Android)
- Keys: `useCelsius`, `gender`, `comfort`, `hasCompletedOnboarding`

### 2. Settings Modal

**Accessible via:** Settings icon (gear) in hero section

**Settings Available:**
1. Temperature Unit toggle
2. Body Temperature slider
3. Fit Preference (Male/Female) - moved from main page

**Behavior:**
- Closes on backdrop tap
- Closes on swipe down (100px threshold)
- Changes apply immediately and persist

### 3. Location System

**Priority Order:**
1. Saved manual location (if set)
2. Saved GPS location (if available)
3. Fresh GPS request

**Manual Location:**
- Search via Nominatim/OpenStreetMap
- USA results prioritized for zip codes
- Minimum 2 characters before search
- 300ms debounce

**GPS:**
- 15 second timeout
- Falls back to manual entry on failure
- Permission handling with platform-specific help text

### 4. Date/Time Selection

**Date Picker:**
- 7-day grid (today through +6 days)
- "Today" / "Tomorrow" labels for first two days
- Bottom sheet modal

**Time Picker:**
- Hours: 5 AM to 9 PM
- Grid layout
- Bottom sheet modal

---

## Hero Image System

### Temperature Brackets

```javascript
function getTempBracket(feelsLike) {
    const f = useCelsius ? feelsLike * 9/5 + 32 : feelsLike;
    if (f >= 80) return 'hot';
    if (f >= 65) return 'warm';
    if (f >= 50) return 'mild';
    if (f >= 35) return 'cool';
    if (f >= 20) return 'cold';
    return 'freezing';
}
```

### Default Images (Preloaded)

Each temperature bracket has a default Unsplash image that loads instantly:

```javascript
const DEFAULT_HERO_IMAGES = {
    hot: 'https://images.unsplash.com/photo-1571008887538-b36bb32f4571?w=800&h=1200&fit=crop',
    warm: 'https://images.unsplash.com/photo-1486218119243-13883505764c?w=800&h=1200&fit=crop',
    mild: 'https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=800&h=1200&fit=crop',
    cool: 'https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=800&h=1200&fit=crop',
    cold: 'https://images.unsplash.com/photo-1517649763962-0c623066013b?w=800&h=1200&fit=crop',
    freezing: 'https://images.unsplash.com/photo-1544899489-a083461b088c?w=800&h=1200&fit=crop'
};
```

### Preloading Strategy

1. **HTML `<link rel="preload">`** for all 6 default images
2. **JavaScript `new Image()`** preload on page load
3. **Immediate display** of default image when weather loads
4. **Background fetch** from Supabase for variety (updates if found)

### Supabase Integration

**Combination ID Format:**
```
{gender}_{weather}_{tempBracket}_{timeOfDay}_{outfitHash}
Example: "UNISEX_CLEAR_WARM_MORNING_abc123"
```

**Tables:**
- `outfit_combinations` - unique weather/outfit combos
- `generated_images` - AI-generated hero images
- `library_stats` - serve counts for replenishment
- `generation_jobs` - queue for new image generation

---

## Weather Display

### Weather Pills (shown in hero section)

1. **Condition** - weather icon only (sun, cloud, rain, etc.)
2. **Wind** - wind icon + speed (e.g., "12 mph")
3. **Humidity** - moisture gauge icon + percentage (e.g., "65%")
4. **Precipitation** - water drop icon + probability (e.g., "30%") - *only shown if > 0%*
5. **UV** - sun icon + index (e.g., "UV 6") - *only shown if > 0*

### Weather Detail Modal

Tapping a pill opens expanded details:

**Condition:**
- Weather description
- Cloud cover %
- Temperature vs feels like
- Running recommendation

**Wind:**
- Current speed
- Gust speed
- Direction
- Feels like impact

**Humidity:**
- Relative humidity
- Dew point
- Comfort level (Muggy/Dry/Comfortable)
- Sweat impact

**Precipitation:**
- Chance of rain %
- Expected amount
- Recommendation (bring jacket, etc.)
- Running impact

**UV:**
- UV Index value
- Risk level (Low/Moderate/High/Very High)
- Protection recommendation
- Peak hours warning

---

## User Preferences

### Gender/Fit Preference

**Backend Values:**
- `"male"` - Men's products in affiliate search
- `"female"` - Women's products in affiliate search
- `"all"` - No gender prefix (unisex)

**UI Behavior:**
- Two buttons: "Male" / "Female"
- Neither selected by default
- Toggleable: tap selected to deselect
- Deselected = `"all"` in backend

**Affiliate Search Impact:**
```javascript
const genderPrefix = gender === 'male' ? 'mens' :
                     gender === 'female' ? 'womens' : '';
const searchTerm = genderPrefix ? `${genderPrefix} ${item}` : item;
```

### Comfort/Body Temperature

**Values:** -10, -5, 0, +5, +10

**Effect:** Adjusts the "feels like" temperature for outfit recommendations
```javascript
const adjustedTemp = weather.feelsLike - comfort;
// If user "runs hot" (+10), we recommend lighter clothes
// If user "runs cold" (-10), we recommend warmer clothes
```

---

## Affiliate Integration

### Amazon Associates

```javascript
const AFFILIATE_TAG = 'runwear-20';
const PLATFORM_SUBTAG = 'pwa';  // or 'ios', 'android', 'wearos'

const amazonUrl = `https://www.amazon.com/s?k=${searchTerm}&tag=${AFFILIATE_TAG}&ascsubtag=${PLATFORM_SUBTAG}`;
```

### Platform Subtags for Analytics

| Platform | Subtag |
|----------|--------|
| PWA | `pwa` |
| iOS | `ios` |
| watchOS | `watchos` |
| Android | `android` |
| Wear OS | `wearos` |

---

## UX Patterns

### Modal Swipe-to-Close

All bottom sheet modals support:
1. **Backdrop tap** to close
2. **Swipe down** from handle area (top 60px)
3. **Threshold**: 100px swipe to close
4. **Animation**: 0.3s ease transition

### Pull-to-Refresh

- Available on main screen
- 80px pull threshold
- Resets hero image to placeholder during refresh
- Spinner animation while loading

### Staggered Animations

Outfit cards animate in sequence:
- Animation: `slideInVertically` + `fadeIn`
- Duration: 400ms
- Easing: FastOutSlowInEasing
- Stagger: 50ms delay per card

### Press States

Outfit cards show feedback:
- Background: #1A1A1A → #262626
- Transform: translateX(4px)
- No ripple (custom feedback)

---

## Version History

| Version | Changes |
|---------|---------|
| v2.9 | Preloaded hero images for instant display |
| v2.8 | Version footer, non-blocking image load, full-width gender toggle |
| v2.7 | Gender moved to settings, precipitation pill, swipe-to-close, humidity icon change |
| v2.6 | Hero image refresh fix on location/date change |

---

## Implementation Checklist for Native Apps

### Android (Jetpack Compose)
- [ ] Implement `RunWearColors` object with all design tokens
- [ ] Add Bebas Neue font (embedded TTF)
- [ ] Create `OutfitCard` with press state and staggered animation
- [ ] Create `GenderToggle` component (toggleable Male/Female)
- [ ] Create `WeatherPill` with precipitation support
- [ ] Implement modal swipe-to-close gesture
- [ ] Preload default hero images by temperature bracket
- [ ] Add version to footer/about screen

### iOS (SwiftUI)
- [ ] Define `RunWearColors` with all design tokens
- [ ] Add Bebas Neue font to asset catalog
- [ ] Create `OutfitCard` view with press feedback
- [ ] Create `GenderToggle` with toggle-to-deselect
- [ ] Create `WeatherPill` with precipitation
- [ ] Implement sheet drag-to-dismiss
- [ ] Preload default hero images
- [ ] Add version to footer

---

*Generated from RunWear PWA v2.9 implementation*
