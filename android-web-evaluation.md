# RunWear: Android vs Web — Evaluation & Recommendations

**Date:** 2026-10-10
**Basis:** `android-web-audit.md` (code audit) + live visual audit of runwear.ai (browser screenshots)
**Stance:** Web is the reference for *behavioral* parity, but not automatically the winner. Where Android is genuinely better UX, the recommendation is to port Android's version TO the web. Each item gets a verdict, a concrete recommendation with business reasoning, and a t-shirt effort size.

---

## 1. Item-Tap Behavior (bottom sheet vs direct-to-Amazon)

**Evaluation:** Web wins, decisively. The visual audit confirmed it: tap an outfit row on web → Amazon search page opens in a new tab immediately, zero intermediate UI. Android's `ShopSheet` bottom sheet (emoji + "Shop on Amazon" button) adds a full extra tap to the single highest-value action in the app. Every step in an affiliate funnel bleeds conversion — the sheet adds no information the user doesn't already have from the card itself. Notably, the PWA *contains* a dead `showOutfitDetail()` bottom-sheet that is never called: the team already tried the intermediate step and abandoned it. That's a decision, not an accident.

**Recommendation:** Change Android to match web — tap opens the Amazon URL directly via `Intent(ACTION_VIEW)`. Delete `ShopSheet`.
**Business case:** Direct revenue impact. Fewer taps → more affiliate clicks → more commission.
**Effort:** S

---

## 2a. Outfit Card Icons (emoji vs SVG category icons)

**Evaluation:** Web wins. The visual audit confirmed zero emojis in the web product UI — outfit rows use teal-tinted SVG category icons layered under real product photo thumbnails from Supabase. Android's emoji base layer (👕 etc.) renders inconsistently across OEM skins (Samsung vs Pixel draw the same emoji differently), looks amateurish next to real photography, and signals "prototype" to Play Store reviewers. SVG icons are deterministic, on-brand, and cheap.

**Recommendation:** Port the 5 category SVGs (`head`, `top`, `bottom`, `hands`, `accessories`) to Android as vector drawables; replace the emoji `Text` fallback in `OutfitCard`.
**Business case:** App Store quality signals. Visual polish is what separates 4.2★ from 4.7★, and rating is the #1 driver of organic install conversion.
**Effort:** S (mechanical port)

---

## 2b. Shop All Rows (thumbnails missing on Android)

**Evaluation:** Web wins, and this is the highest-ROI visual fix on the list. Product thumbnails are the single highest-converting element in any commerce UI — users buy what they can see. The thumbnails already exist in Supabase storage; Android simply isn't loading them in `ShopItemRow`. Emoji-only rows look unfinished by comparison.

**Recommendation:** Add the same Coil `AsyncImage` thumbnail loading used in `OutfitCard` to `ShopItemRow`'s icon box.
**Business case:** Direct affiliate revenue impact — thumbnail → tap → Amazon.
**Effort:** S

---

## 2c. Shop Modal Header, FTC Text, Close Control

**Evaluation:** Split verdict.
- **FTC text: web wins.** Amazon's Associates agreement requires specific disclosure language. Web's "we may earn a small commission at no extra cost to you" is closer to Amazon's recommended wording; Android's vaguer "This helps support the app" is the riskier phrasing. In an audit, you want the safer text everywhere.
- **Title styling: Android wins.** The Bebas Neue all-caps "SHOP YOUR OUTFIT" is brand-distinctive; web's "🛒 Shop Your Outfit" is generic (and the 🛒 is the one place web uses emoji — harmless, but not a reason to copy it).
- **Close control: web wins.** An explicit Close button beats drag-handle-only. Swipe-to-dismiss is undiscoverable for a meaningful share of users, and a stuck-feeling modal is a 1-star review waiting to happen.

**Recommendation:** Adopt web's FTC text verbatim on Android. Keep Android's title styling. Add an explicit Close button to Android's `ShopAllSheet`.
**Business case:** Legal safety (FTC text) + reduced support/negative-review risk (Close button).
**Effort:** S

---

## 3a. Outfit Section Header ("N Items for Your Run" + Shop button)

**Evaluation:** Near-parity, don't churn. Android's gradient + glow Shop button is arguably nicer than web's flat teal pill, but per-platform polish differences are fine — no user compares the two side by side. There are 20 bigger fish.

**Recommendation:** No change on either side.
**Effort:** —

---

## 3b. Date Picker Layout (horizontal row vs 2-column grid)

**Evaluation:** Web wins on usability. A horizontal scroll row of day cells with two-line labels (day name + date) is the standard pattern across every weather app on both platforms — users already know how to read it. Android's 2-column grid is unusual, burns vertical space in a bottom sheet, and the single-line "Mon, Oct 12" labels scan worse. This is a usability downgrade for no reason.

**Recommendation:** Rebuild Android's `DatePickerSheet` as a horizontal scroll row matching web's two-line cells.
**Business case:** Date/time selection is core to the product (planning tomorrow's run). Friction here hits the primary use case.
**Effort:** M

---

## 3c. Tip Card (expandable single tip vs all-tips-flat)

**Evaluation:** Web wins on interaction design. Showing all tips stacked flat (Android) overwhelms and pushes content down; web's single visible tip with tap-to-expand is cleaner and creates a small moment of engagement. "Pro Tip" (title case) vs "PRO TIP" (all caps) is trivia — take web's, it reads friendlier.

**Recommendation:** Port web's expandable single-tip card to Android, including the "Pro Tip" casing.
**Business case:** The tip card is the "why" behind the recommendation — it's what makes RunWear smarter than a weather app. Better presentation = better perceived intelligence = retention.
**Effort:** S

---

## 3d. Onboarding Copy

**Evaluation:** Web wins on copy, and copy is conversion. Specifics:
- "Temperature" + sublabel "How do you measure the weather?" explains *why* you're asking. "Temperature Unit" is jargon with no sublabel.
- The comfort question "Do you get cold easily or overheat quickly?" with three plain options (Cold / Neither / Hot) is human language. Android's five-point "Run Cold / Slightly Cold / Neutral / Slightly Hot / Run Hot" scale is a questionnaire nobody asked for — more options = more friction, and the gradations don't meaningfully change the outfit output.
- Web's teal-gradient "Let's Go! 🏃" CTA gives a moment of commitment; Android dumping straight into location buttons feels abrupt.
- Fewer, clearer choices = higher onboarding completion. Every confusing label costs users.

**Recommendation:** Adopt web's onboarding copy verbatim on Android — labels, sublabels, the 3-option comfort scale, and the "Let's Go!" CTA button.
**Business case:** Onboarding completion is the top of the entire funnel. This is the cheapest conversion win available.
**Effort:** S (copy changes + one button)

---

## 3e. Location Search Layout

**Evaluation:** Parity. Functionally equivalent, visual order matches. 
**Recommendation:** No change.
**Effort:** —

---

## 4a. Animated Temperature Count-Up (Android-only)

**Evaluation:** Android wins, and it's not close. The 800ms count-up tween is a signature moment — it makes the app feel alive and premium the instant it loads. Web's static text is fine but forgettable. This is exactly the kind of native-app polish that justifies having a native app instead of a bookmark.

**Recommendation:** Keep on Android AND port to web (cheap CSS/JS animation).
**Business case:** First-impression quality. The temperature is the first thing every user looks at; animating it is brand differentiation for near-zero cost.
**Effort:** S (web port)

---

## 4b. Tappable Temperature Toggles °F/°C (Android-only)

**Evaluation:** Android wins. The unit toggle is the most-touched setting in any weather product, and Android puts it one tap away with zero navigation. Web burying it in a Settings sheet is strictly worse UX.

**Recommendation:** Keep on Android; make web's temperature display clickable to toggle units too.
**Business case:** Reduces settings-navigation friction; small but daily-used interaction.
**Effort:** S (web port)

---

## 4c. Theme: Web Is Light, Android Is Dark

**Evaluation:** This is the biggest visual finding from the browser audit and the code audit understated it: **the web app is light theme** (white outfit cards, teal-tinted icon tiles, light blue-grey page background) while **Android is dark theme**. That's not a "small difference" — it's a different-looking product. A brand should look like itself everywhere, so one direction has to win. My call: **dark wins.** The usage context is pre-dawn and post-sunset runs; dark is easier on the eyes at 5am, looks premium, differentiates from every stock weather app, and photographs better for App Store screenshots.

**Recommendation:** Standardize on dark theme (Android's direction). Port the dark theme to the web app. Keep Android's card styling as the reference.
**Business case:** Brand consistency across surfaces; premium perception; App Store screenshot quality.
**Effort:** M–L (the PWA is a single 5,374-line file with inline CSS — a theme pass touches a lot of it)

---

## 4d. Section Title Font (Bebas Neue vs default sans)

**Evaluation:** Android wins. Bebas Neue section titles are brand-distinctive; web's default sans is generic. In the context of the dark-theme standardization above, the web should adopt the brand typography, not the other way around.

**Recommendation:** Keep Android as-is; bring Bebas Neue to web section titles as part of the theme pass (4c).
**Business case:** Brand consistency. Fold into 4c's effort.
**Effort:** S (fold into 4c)

---

## 5a. Amazon Search URL Construction

**Evaluation:** Web wins, and this one is money. Three concrete advantages:
1. The `"premium "` prefix filters Amazon results toward higher-quality, higher-price items → higher average order value → higher affiliate commission (Amazon pays a percentage of sale).
2. `"men's "` / `"women's "` (apostrophe) matches Amazon's own category filter labels → better search relevance than Android's `"mens "` / `"womens "`.
3. Proper `encodeURIComponent` vs Android's naive space→`+`: items with special characters (e.g. `Short Shorts (3")`) produce broken or degraded links on Android. A broken affiliate link earns exactly $0.

**Recommendation:** Port web's `buildAmazonLink` logic to Android exactly — "premium " prefix, apostrophe gender forms, full encoding. Keep `ascsubtag=android` for per-platform attribution.
**Business case:** Direct, measurable affiliate revenue impact on every single tap. This is probably the highest-ROI line of code in the app.
**Effort:** S

---

## 5b. Shop All Search Term (weather-aware vs hardcoded)

**Evaluation:** Web wins, same revenue logic as 5a. `"premium cloudy weather running gear"` converts better than `"running gear outfit"` because it matches purchase intent — the user is shopping for a condition, not a vibe. One-line change.

**Recommendation:** Port web's weather-description logic into Android's `buildShopAllUrl`.
**Business case:** Higher-intent search → higher conversion → more commission.
**Effort:** S

---

## 5c. Weather Detail Popups (Android shows fewer rows)

**Evaluation:** Web wins on completeness, but completeness isn't the goal — *decision relevance* is. Most of the missing rows are trivia for an outfit app: nobody picks a shirt based on dew point or visibility. The rows that actually change the outfit decision:
- **Precipitation amount with correct units** — Android hardcodes `"${precip} mm"` even in imperial mode. Showing "3 mm" to an American is a trust killer. This is a P0 bug, not a feature gap.
- **Wind direction** — headwind vs tailwind changes layering decisions.
- **UV protection / peak hours** — sun protection is an outfit decision (hat, sunglasses, sunscreen).
- **Precipitation recommendation / running impact** — directly actionable.
- Skip: visibility, dew point, "feels like impact" prose — more rows dilute the rows that matter.

**Recommendation:** Fix the mm→in unit bug immediately (P0). Then port wind direction, UV protection/peak hours, and precip recommendation/impact. Skip the trivia rows.
**Business case:** Unit bug erodes trust in the core data product; the ported rows strengthen the "RunWear tells you what weather *means*" differentiator.
**Effort:** S (unit bug fix) + M (selective row ports)

---

## 5d. Share Text (wording + URL split)

**Evaluation:** Web wins on content — the temp bracket and weather description line make the shared card more informative, and informative shares get tapped more by recipients (that's the viral loop working). But the real issue is the URL split: web shares `runwear.ai`, Android shares `runwear.app`. Two canonical URLs is a brand bug that compounds across shared links, SEO, and word-of-mouth. `runwear.ai` is the live site — standardize on it everywhere.

**Recommendation:** Adopt web's share text on Android verbatim. Standardize `runwear.ai` as the single canonical URL on both platforms.
**Business case:** Share is the primary organic growth loop. Every shared link should resolve to the same place and carry the richest preview.
**Effort:** S

---

## 5e. "Now" Shortcut Button

**Evaluation:** Web has it (pill with reset icon inside the datetime pill when viewing a non-current time); Android has the `onNowClick` prop but visual parity is unconfirmed.
**Recommendation:** Verify Android renders it conditionally the same way; port if missing.
**Business case:** Core UX for the "planning tomorrow, back to today" loop.
**Effort:** S

---

## 6. Android-Only Features — Verdicts

1. **Home screen widget** — KEEP. It's the best retention asset in the product (see Overall Improvements). No PWA equivalent possible; that's fine.
2. **Debug overlay** — KEEP. Dev-only, invisible to users.
3. **Animated temperature count-up** — KEEP + PORT TO WEB (see 4a).
4. **Tappable temperature unit toggle** — KEEP + PORT TO WEB (see 4b).
5. **Haptic feedback** — KEEP. Optionally add `navigator.vibrate` on web item-tap for partial parity where supported (S, nice-to-have).
6. **Pull-to-refresh** — parity confirmed, no action.

---

## 7. Web-Only Features — Verdicts

1. **Expandable tip card** — PORT (see 3c). S.
2. **Weather-aware Shop All term** — PORT (see 5b). S.
3. **"premium " prefix on Amazon searches** — PORT (see 5a). S.
4. **Richer weather detail rows** — SELECTIVE PORT (see 5c). S+M.
5. **GPS help section** — PORT. This one matters more than it looks: location-denied users are dead users, and a troubleshooting UI recovers a real share of them. Check Android's `PermissionScreen` for equivalent guidance; if it's a dead-end "please grant permission" screen, port web's `renderGPSHelp` content. **Effort:** M.

---

## Consolidated Priority List

| Priority | Item | Direction | Effort |
|---|---|---|---|
| P0 | Item tap → direct to Amazon, delete ShopSheet | Android → web | S |
| P0 | Precipitation mm→in unit bug | Fix on Android | S |
| P0 | Amazon URL parity ("premium", apostrophes, encoding) | Android → web | S |
| P0 | Outfit card SVG icons (replace emoji) | Android → web | S |
| P0 | Shop All thumbnails (add Coil loading) | Android → web | S |
| P1 | Weather-aware Shop All term | Android → web | S |
| P1 | Share text + single canonical URL (runwear.ai) | Android → web | S |
| P1 | Onboarding copy parity | Android → web | S |
| P1 | Date picker → horizontal two-line row | Android → web | M |
| P1 | Selective weather detail rows (wind dir, UV, precip rec) | Android → web | M |
| P1 | GPS help section | Android → web | M |
| P1 | Tip card → expandable "Pro Tip" | Android → web | S |
| P1 | FTC disclosure text | Android → web | S |
| P2 | Dark theme standardization | Web → Android's direction | M–L |
| P2 | Animated temp + tappable unit toggle | Web → Android's direction | S |
| P2 | Bebas Neue section titles on web | Web → Android's direction | S (in 4c) |
| P2 | Close button on ShopAllSheet | Android → web | S |
| P2 | "Now" pill parity check | Verify/port | S |

---

## Overall Improvements — Beyond Parity

These are product-level recommendations, not parity fixes. Stated plainly:

### 1. The affiliate flow: direct-to-Amazon is right for now, wrong forever
Shipping the P0 (direct links) is correct today — fewer taps, more clicks, more commission. But long-term, renting 100% of the shopping experience to Amazon means zero data, zero customer relationship, and commission rates Amazon can (and historically does) cut unilaterally. The roadmap should be an **owned consideration layer**: not the current emoji popup, but a real native sheet showing the product thumbnail, price range, and 2–3 top Amazon results embedded via the Product Advertising API, letting the user choose *before* leaving for Amazon. That buys (a) tap-intent data for tuning recommendations, (b) A/B testing on presentation, (c) a premium feel that justifies the app's existence over a bookmark. Never build checkout — Amazon wins checkout. Own the consideration step.

### 2. Onboarding: prime the location permission
The app dies without location, and both platforms fumble the permission moment. Add a pre-permission priming screen on Android ("we need your location for hyperlocal weather — it's never stored or shared") *before* the system dialog. iOS/Android data consistently shows priming screens lift grant rates 20–30%. Every denied user is $0 in lifetime affiliate value; this is the cheapest growth lever in the product.

### 3. Retention: the widget is the hook, the notification is the habit
RunWear is high-intent, low-frequency — "check before you run." The home screen widget is the best retention asset (glanceable, zero friction, already built). Next, in order:
- **Morning push (opt-in):** "55° at 6am — shorts and a tee. Details in RunWear." This is the killer feature for runners and the single highest-ROI retention mechanic available.
- **Wear OS glance:** the Wear scaffolding already exists — a complication showing feels-like temp + one-line outfit is a natural extension.
- **Feedback loop (the moat):** post-run, ask "was this right?" and tune the fit profile. A learning personal profile is something no weather app and no Amazon search page can replicate. This is the long-term differentiator.

### 4. Double down on interpretation, not data
Weather apps report temperature; RunWear's job is telling runners what it *means*. The expandable tips are a start — push further into condition-specific guidance ("15mph north wind — windbreaker for the outbound leg, you'll warm up on the return"). Every piece of "why" copy widens the gap between RunWear and a weather widget.

### 5. Monetization beyond affiliate
Affiliate is the right v1 — zero friction, immediate revenue. But it's capped and platform-risky. In order of sanity:
- **Premium tier:** marathon-weekend packing lists ("3-day packing list for Chicago Marathon: 42° start"), advanced metrics, no-brainer at $2–3/mo for serious runners.
- **Brand placements:** Nike/Adidas/New Balance pay for sponsored placement in the recommendation feed — higher margin than affiliate %, and it looks native.
- **White-label:** the recommendation engine licensed to running publications, coaches, race organizers.
- **Don't:** display ads. They trash the premium feel for pennies and actively work against the brand-placement play.

### 6. One theme, one URL, one brand
Dark theme everywhere (4c), `runwear.ai` everywhere (5d), Bebas section titles everywhere (4d). These are individually small; collectively they're the difference between "a few apps" and "a brand." Do them as one pass, not three tickets.

### 7. Kill the dead code as you go
The PWA's dead `showOutfitDetail()` and Android's soon-to-be-deleted `ShopSheet` are symptoms: the codebase carries the ghosts of reversed decisions. Make "delete the losing experiment" part of every parity ticket. Dead code is where the next divergence breeds.
