# RunWear App Icon Package

This package contains all the icon assets needed for RunWear across all platforms: PWA, Android, and iOS.

## Design Specifications

- **Primary Color:** Teal `#00796B` (matches RunWear brand)
- **Icon Design:** White running shirt silhouette (tank top + long sleeve merged) on teal circular background
- **Style:** Clean, minimal, athletic

## Directory Structure

```
runwear-icons/
├── source/                          # Original high-res source files
│   ├── ic_launcher_background.png   # 432x432 solid teal background
│   ├── ic_launcher_foreground.png   # 432x432 white shirt on teal circle
│   └── ic_launcher_playstore.png    # 512x512 composite icon
│
├── android/                         # Android Adaptive Icons
│   ├── mipmap-mdpi/                 # 48dp (108px adaptive)
│   ├── mipmap-hdpi/                 # 72dp (162px adaptive)
│   ├── mipmap-xhdpi/                # 96dp (216px adaptive)
│   ├── mipmap-xxhdpi/               # 144dp (324px adaptive)
│   └── mipmap-xxxhdpi/              # 192dp (432px adaptive)
│
├── ios/                             # iOS/watchOS App Icons
│   ├── Icon-20.png through Icon-1024.png
│   └── Icon-Watch-*.png
│
└── pwa/                             # Web/PWA Icons
    ├── favicon.ico
    ├── favicon-16.png, favicon-32.png
    ├── apple-touch-icon.png (180x180)
    └── icon-72.png through icon-512.png
```

---

## Android Integration

### 1. Copy Icon Files

Copy the contents of `android/` to your project:

```
android/mipmap-*/ → app/src/main/res/mipmap-*/
```

### 2. Create Adaptive Icon XML

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
```

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
```

### 3. Update AndroidManifest.xml

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    ...>
```

### 4. Play Store Icon

Use `source/ic_launcher_playstore.png` (512x512) for Google Play Console upload.

---

## iOS Integration

### 1. Add to Asset Catalog

1. Open your Xcode project
2. Select `Assets.xcassets`
3. Right-click → "App Icons & Launch Images" → "New iOS App Icon" (or select existing AppIcon)
4. Drag the appropriate icon files to each slot:

| Slot | File |
|------|------|
| iPhone Notification 20pt @2x | Icon-20@2x.png |
| iPhone Notification 20pt @3x | Icon-20@3x.png |
| iPhone Settings 29pt @2x | Icon-29@2x.png |
| iPhone Settings 29pt @3x | Icon-29@3x.png |
| iPhone Spotlight 40pt @2x | Icon-40@2x.png |
| iPhone Spotlight 40pt @3x | Icon-40@3x.png |
| iPhone App 60pt @2x | Icon-60@2x.png |
| iPhone App 60pt @3x | Icon-60@3x.png |
| iPad Notification 20pt @1x | Icon-20.png |
| iPad Notification 20pt @2x | Icon-20@2x.png |
| iPad Settings 29pt @1x | Icon-29.png |
| iPad Settings 29pt @2x | Icon-29@2x.png |
| iPad Spotlight 40pt @1x | Icon-40.png |
| iPad Spotlight 40pt @2x | Icon-40@2x.png |
| iPad App 76pt @1x | Icon-76.png |
| iPad App 76pt @2x | Icon-76@2x.png |
| iPad Pro App 83.5pt @2x | Icon-83.5@2x.png |
| App Store 1024pt | Icon-1024.png |

### 2. watchOS Icons

For Apple Watch, create a separate `AppIcon` in your Watch App's Assets:

| Slot | File |
|------|------|
| 24pt @2x | Icon-Watch-24@2x.png |
| 27.5pt @2x | Icon-Watch-27.5@2x.png |
| 29pt @2x | Icon-Watch-29@2x.png |
| 29pt @3x | Icon-Watch-29@3x.png |
| 40pt @2x | Icon-Watch-40@2x.png |
| 44pt @2x | Icon-Watch-44@2x.png |
| 50pt @2x | Icon-Watch-50@2x.png |
| 98pt @2x | Icon-Watch-98@2x.png |
| 108pt @2x | Icon-Watch-108@2x.png |

### 3. Contents.json

See `ios/Contents.json` for the complete asset catalog configuration.

---

## PWA Integration

### 1. Copy Files to Your Web Root

```bash
cp pwa/* /path/to/your/pwa/public/
```

### 2. Update manifest.json

```json
{
  "name": "RunWear",
  "short_name": "RunWear",
  "icons": [
    {
      "src": "icon-72.png",
      "sizes": "72x72",
      "type": "image/png",
      "purpose": "any maskable"
    },
    {
      "src": "icon-96.png",
      "sizes": "96x96",
      "type": "image/png",
      "purpose": "any maskable"
    },
    {
      "src": "icon-128.png",
      "sizes": "128x128",
      "type": "image/png",
      "purpose": "any maskable"
    },
    {
      "src": "icon-144.png",
      "sizes": "144x144",
      "type": "image/png",
      "purpose": "any maskable"
    },
    {
      "src": "icon-152.png",
      "sizes": "152x152",
      "type": "image/png",
      "purpose": "any maskable"
    },
    {
      "src": "icon-192.png",
      "sizes": "192x192",
      "type": "image/png",
      "purpose": "any maskable"
    },
    {
      "src": "icon-384.png",
      "sizes": "384x384",
      "type": "image/png",
      "purpose": "any maskable"
    },
    {
      "src": "icon-512.png",
      "sizes": "512x512",
      "type": "image/png",
      "purpose": "any maskable"
    }
  ],
  "theme_color": "#00796B",
  "background_color": "#00796B",
  "display": "standalone"
}
```

### 3. Update HTML Head

```html
<head>
    <!-- Favicon -->
    <link rel="icon" type="image/x-icon" href="/favicon.ico">
    <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16.png">
    
    <!-- Apple Touch Icon -->
    <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
    
    <!-- PWA Manifest -->
    <link rel="manifest" href="/manifest.json">
    
    <!-- Theme Color -->
    <meta name="theme-color" content="#00796B">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
</head>
```

---

## Icon Sizes Reference

### Android (Adaptive Icons)

| Density | Legacy Icon | Adaptive Layer | Ratio |
|---------|-------------|----------------|-------|
| mdpi | 48×48 | 108×108 | 1x |
| hdpi | 72×72 | 162×162 | 1.5x |
| xhdpi | 96×96 | 216×216 | 2x |
| xxhdpi | 144×144 | 324×324 | 3x |
| xxxhdpi | 192×192 | 432×432 | 4x |

### iOS

| Context | Size | Scale | Pixels |
|---------|------|-------|--------|
| iPhone Notification | 20pt | @2x, @3x | 40, 60 |
| iPhone Settings | 29pt | @2x, @3x | 58, 87 |
| iPhone Spotlight | 40pt | @2x, @3x | 80, 120 |
| iPhone App | 60pt | @2x, @3x | 120, 180 |
| iPad Notification | 20pt | @1x, @2x | 20, 40 |
| iPad Settings | 29pt | @1x, @2x | 29, 58 |
| iPad Spotlight | 40pt | @1x, @2x | 40, 80 |
| iPad App | 76pt | @1x, @2x | 76, 152 |
| iPad Pro | 83.5pt | @2x | 167 |
| App Store | 1024pt | @1x | 1024 |

### PWA

| Context | Size |
|---------|------|
| Favicon | 16, 32, ICO |
| Apple Touch | 180 |
| Android Chrome | 192, 512 |
| Splash/General | 72, 96, 128, 144, 152, 384 |

---

## Regenerating Icons

If you need to update the icon design, modify the source files and run:

```bash
# Requires ImageMagick
# See the generation script in this package for exact commands
```

---

## Brand Guidelines

- **Primary Color:** Teal `#00796B`
- **Icon should always appear on the teal background**
- **White shirt silhouette for maximum contrast**
- **No text in the icon itself**

---

*Generated for RunWear by Claude*
*Last updated: January 2025*
