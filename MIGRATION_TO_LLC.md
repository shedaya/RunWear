# Migration to Uptime Ventures LLC — Publishing Checklist

Moving RunWear from Sol Hedaya's **personal** developer accounts to
**Uptime Ventures LLC** before first publication. Nothing is publicly live
yet (iOS never submitted; Android only in closed testing), so this is a
re-home of drafts, **not** a live-app transfer.

## Entity reference

| Field | Value |
|---|---|
| Legal name | Uptime Ventures LLC |
| Type | Single-member domestic LLC (New York), member-managed |
| Sole member | Sol Hedaya |
| EIN | 99-4772290 (assigned 2024-09-04) |
| IRS name control | UPTI |
| NY DOS ID | 7411305 |
| Formation date | 2024-09-03 (perpetual) |
| Mailing address | Uptime Ventures, 4th Floor, Ste 473, New York, NY 10016 |
| Business phone | 833-878-4630 |
| Tax classification | Disregarded entity (default) |

## Confirmed starting state

| Platform | Store | Account | Identifier | Status |
|---|---|---|---|---|
| Android | Google Play | personal (solhedaya@gmail.com) | `com.runwear.wear` | Closed testing published → **name burned to personal account**, no production |
| iOS/watchOS | App Store Connect | personal (solhedaya@gmail.com) | `com.runwear.app` (Apple ID 6758366686, SKU runwear-ios-001) | "1.0 Prepare for Submission" → **never submitted** |
| Affiliate | Amazon Associates | personal (assumed) | tag `runwear-20` | In use across all clients |

## Decisions made

- **Android package** → `com.runwear.app` (fresh Play listing under the LLC;
  aligns Android with the iOS bundle ID). `com.runwear.wear` is abandoned.
- **Store listing** for the Play app → regenerate fresh from this project (the
  copy lives here), no transfer needed.
- **iOS bundle ID** → keep `com.runwear.app` (delete + re-register under LLC;
  Apple bundle IDs are reusable, so no code change).
- **Amazon** → update the existing `runwear-20` account's tax entity to the LLC
  rather than opening a new account (keeps the tag → no code change).

---

## The long pole: D-U-N-S number

Both Apple (Organization enrollment) and Google (organization account) require a
**D-U-N-S number** for Uptime Ventures LLC. Everything below is gated on it.

- [ ] D-U-N-S number issued (**applied for — in progress**)
- [ ] LLC business bank account open (needed for store payouts)
- [ ] W-9 / tax details ready for store payment profiles

---

## Apple (iOS + watchOS)

Apple **cannot transfer** an app that was never approved, so this is a
delete-and-recreate — but the bundle ID is preserved.

- [ ] Enroll **Uptime Ventures LLC as an Organization** in the Apple Developer
      Program ($99/yr) — requires D-U-N-S + entity verification
- [ ] On the **personal** account: delete the draft app record (Apple ID
      `6758366686`)
- [ ] On the **personal** account: delete the `com.runwear.app` App ID from
      Certificates, Identifiers & Profiles (frees it for reuse)
- [ ] On the **LLC org** account: register `com.runwear.app` and
      `com.runwear.app.watchkitapp` App IDs
- [ ] Create the App Store Connect app record under the LLC org
- [ ] Re-point Xcode signing to the LLC **Team ID** (regenerate provisioning
      profiles / signing certs)
- [ ] Sign the Paid Apps Agreement + enter LLC banking/tax under the org account

Bundle IDs (unchanged): `RunWear-iOS/RunWear.xcodeproj/project.pbxproj`
(`com.runwear.app`, `com.runwear.app.watchkitapp`).

## Google Play (Android + Wear OS)

Going to an **organization** account matters here: org accounts are **exempt**
from the "12 testers / 14 days closed testing" requirement that blocks new
personal accounts. The closed-testing progress on the personal account is not
worth preserving.

- [x] Repackage `applicationId` → `com.runwear.app` on phone + wear modules
      (`RunWear-android/app/build.gradle.kts`, `RunWear-android/wear/build.gradle.kts`)
- [ ] Create a **new Google Play developer account owned by the LLC** ($25
      one-time), verified as an **organization** (D-U-N-S)
- [ ] Create the app fresh under the LLC org account
- [ ] Set up **Play App Signing** (fresh upload key is fine — unpublished)
- [ ] Regenerate the store listing (copy lives in this project)
- [ ] Upload the AAB and proceed toward production (no 12-tester gate for orgs)
- [ ] Enter LLC banking/tax in the Play payments profile

## Amazon Associates

Prefer updating the existing account over opening a new one (avoids the
180-day / 3-qualifying-sales rule and a code-wide tag swap).

- [ ] Confirm `runwear-20` is a live account you own (check for link activity)
- [ ] Update Account Settings → Payment and Tax Information → business entity
      = Uptime Ventures LLC (EIN 99-4772290)
- [ ] **If** entity type can't be changed: open a new LLC account ~2 weeks
      before launch, then swap the tag in all 4 locations:
  - `RunWear-iOS/RunWear/Models/Outfit.swift`
  - `RunWear-iOS/RunWear/Views/Modals/ShopModal.swift`
  - `RunWear-android/app/src/main/java/com/runwear/app/ui/screens/BottomSheets.kt`
  - `runwearpwa22/index.php`

## Legal / metadata cleanup

- [ ] Privacy policy names Uptime Ventures LLC
- [ ] Support URL / contact reflects the LLC
- [ ] Affiliate disclosure text references Uptime Ventures LLC / RunWear
      (Android `BottomSheets.kt` currently says "RunWear")
- [ ] Copyright / developer name in both store listings = Uptime Ventures LLC

---

## Notes

- **Backend (Supabase)** is account-agnostic; just ensure the project is owned
  by an LLC-controlled login for continuity.
- There is a stale duplicate project tree at `RunWear-android/RunWear/` using
  the old `com.runwear.app`/`com.runwear.wear` split — not the tree that ships.
  Consider removing it to avoid confusion.
- The Wear tile launches its activity via `Context.getPackageName()`, so it
  followed the `applicationId` change automatically — no code change needed there.
