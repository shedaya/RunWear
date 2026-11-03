package com.runwear.app.makeitme

/**
 * RW-14 "Make it me" copy deck — spec §7 of
 * ~/workspace/release-prep/marketing/runwear-rw14-ux-identity-spec-2026-10-31.md,
 * verbatim. Strings are NOT edited silently: copy changes amend the spec's
 * §7 by dated note first (spec rule). PROPOSAL strings (P1's first-swap
 * caption) are deliberately ABSENT — they are not in the build until Sol
 * approves them.
 */
object MakeItMeStrings {

    // F — Onboarding fork (spec §2.2)
    const val F1_HEADLINE = "See yourself in every outfit."
    const val F2_LEAD = "That's a model in your outfit photo. It could be you — in this morning's weather, on a road like yours, wearing exactly what RunWear recommends."
    const val F3_OPTION_A_TITLE = "Make it me"
    const val F4_OPTION_A_BODY = "Upload 1 to 3 photos of yourself, and RunWear makes your outfit photos from them — starting with the one you're looking at. Your first 5 looks are free."
    const val F5_OPTION_A_BUTTON = "Use my photos"
    const val F6_OPTION_B_TITLE = "Use the standard photos"
    const val F7_OPTION_B_BODY = "Keep the photo library RunWear matches to your outfit and your weather. You can switch to Make it me any time in Settings."
    const val F8_OPTION_B_BUTTON = "Use standard photos"
    const val F9_FOOTER = "Either way, your recommendations don't change — only the photo does."

    // C — Consent, attestation, capture (spec §2.3)
    const val C1A_CONSENT_TITLE = "Before you add photos"
    const val C1B_CONSENT_BODY = "RunWear will upload your photos to our server. They're used for one thing only: making pictures of you wearing your recommended outfits.\n\nYour photos are never used to train AI models. They're never shown to other users, never added to RunWear's standard photo library, and never shared.\n\nPictures made from your photos are kept only for you, so your outfits are ready when you open the app.\n\nYou can delete your photos and every picture made from them at any time: Settings → Make it me → Delete my photos. Deleting is permanent and takes effect right away."
    const val C1C_ATTESTATION = "These photos are of me, and I am 18 or older."
    const val C1D_CONTINUE = "Continue"
    const val C1E_NOT_NOW = "Not now"
    const val C2A_CAPTURE_TITLE = "Add 1 to 3 photos of you"
    const val C2B_CAPTURE_GUIDANCE = "Front-facing works best: head and shoulders or half body, good light, no sunglasses. The outfit needs a body to land on."
    // C2D–C2G added Oct 31, 2026 (RW-16.14, Sol's directives — spec §7
    // amended by dated note): the full-body guidance (guidance only,
    // never a validation rule) and the camera/library source choice.
    // C2D is verbatim-identical to the iOS deck (MakeItMeCopy).
    const val C2D_FULL_BODY_GUIDANCE = "Photos of you running work best. Include one full-body photo in plain athletic wear, head to shoes, with just you in the frame — full-length outfits like tights and jackets come out right when your whole body is in the photo. Photos with other people in them can't be used."
    const val C2E_SOURCE_DIALOG_TITLE = "Add a photo"
    const val C2F_TAKE_PHOTO = "Take photo"
    const val C2G_CHOOSE_FROM_LIBRARY = "Choose from library"
    const val C2C_CONFIRM_FIRST_LOOK = "Make my first look"
    const val C2C_CONFIRM_SAVE = "Save photos"
    // C2J added Oct 31, 2026 (RW-16.15 — spec §7 amended by dated
    // note): the save confirmation, shown after a successful commit
    // before the flow dismisses. Verbatim-identical to the iOS deck
    // (MakeItMeCopy.saveConfirmed). Neither platform confirmed a
    // save before this — the flow just vanished, which read to the
    // user as "my photos didn't save."
    const val C2J_SAVE_CONFIRMED = "Your photos are saved."
    const val C3A_REJECT_FACE_UNCLEAR = "We can't see your face clearly in this one. Try a photo without sunglasses or a hat shadow."
    const val C3B_REJECT_MULTIPLE = "This photo has more than one person in it. Pick one that's just you."
    const val C3C_REJECT_TOO_DARK = "This one is too dark to use. Try a photo in brighter light."
    const val C3D_REJECT_TOO_CLOSE = "This one is cropped too close. A head-and-shoulders or half-body photo works best."
    // C3E/C3F added Nov 2, 2026 (RW-26 guided-selfie follow-up): the
    // retake prompts the guided capture and the upload door share —
    // same checks, same strings, camera offered first.
    const val C3E_RETAKE_GUIDED = "Let's retake that one with the camera — face the light, keep your whole face inside the oval, and hold still."
    const val C3F_RETAKE_UPLOAD = "That photo can't be used. Retake with the camera, or choose a brighter photo that's just you, face forward."

    // G — First generation (spec §2.4). G3 (PROPOSAL P1) intentionally absent.
    // G4 was silence by spec — SUPERSEDED Nov 1, 2026 (RW-16.16, spec
    // §7 amended by dated note): a terminal failure is now a visible,
    // durable state at the hero (G5 + the G6 retry action), driven by
    // the server's per-cell look state. G2 amended the same day: the
    // "About 10 seconds" figure was false against a measured 72 s job
    // and a 2–4 minute backend norm — the line no longer promises a
    // number the system misses.
    const val G1_PROGRESS = "Making this outfit yours…"
    const val G2_PROGRESS_SUB = "This usually takes a few minutes. Your recommendation is ready either way — the standard photo stays up until yours is done."
    // G5/G6 added Nov 1, 2026 (RW-16.16 — spec §7 amended by dated
    // note). One failure line for every reason class in this build;
    // the class travels in the payload for a later class-specific
    // pass. DECIDED Nov 1, 2026 (RW-16.16): Sol picked the final
    // wording from the options put to him — wired as this single
    // constant so the swap is a one-string change. Verbatim-
    // identical to the iOS deck (MakeItMeCopy.lookFailed).
    const val G5_LOOK_FAILED = "Your look couldn't be made from these photos — tap to try again."
    const val G6_RETRY = "Try again"

    // S — Settings (spec §3)
    const val S1_SECTION_HEADER = "Make it me"
    const val S2_STATE_A_ROW = "See yourself in every outfit"
    const val S2_STATE_A_SUB = "Make your outfit photos from your own photos."
    const val S3_STATUS = "You're in your photos."
    // RW-26 §H: subscribed = management only, NO sales copy — this is
    // a state fact, not a benefit line.
    const val S5_STATE_C_SUB = "Subscribed — your photo is ready before you open the app."
    // RW-26 §9 (frame 35 settings line, verbatim): the two benefit
    // CONCEPTS (readiness, no cap) in crafted language — the concept
    // labels themselves never print.
    const val S18_SETTINGS_PREMIUM_LINE = "Made ahead, without limit — every outfit is ready when you open the app."
    const val S6_PHOTOS_ROW = "Your photos"
    const val S6_PHOTOS_SUB = "View or replace"
    // S15–S17 added Oct 31, 2026 (RW-16.15 — spec §7 amended by dated
    // note): the Your Photos tile states. A tile that cannot load is
    // a labeled, tappable retry — never a silent blank box (the
    // contract iOS build (13) established with ReferenceTileLoader).
    const val S15_PHOTO_LOADING = "Loading"
    const val S16_PHOTO_LOAD_FAILED = "Couldn't load this photo"
    const val S17_PHOTO_RETRY = "Tap to retry"
    // S18/S19 added Oct 31, 2026 (RW-16.15 increment): the Your
    // Photos SCREEN-level load failure — the bounded load always
    // lands on content or this labeled retry state, never a long
    // spinner into blankness.
    const val S18_PHOTOS_LOAD_FAILED = "Couldn't load your photos"
    const val S19_TRY_AGAIN = "Try again"
    const val S7_REPLACE_NOTE = "Replacing your photos remakes today's look right away. Your other looks update as they're made again."
    // RW-26 frame 38: the epoch rule in plain words, on the selfie
    // management screen — a retake re-makes what's AHEAD and never
    // rewrites the Wear I've Run collection behind it.
    const val S20_EPOCH_RULE = "A new photo re-makes the looks ahead of you. It never changes the outfits already in your Wear I've Run collection."
    const val S8_STATE_B_SUBSCRIBE_ROW = "Every outfit, instantly"
    // Price-free by design (RW-16.30 pricing experiment, Nov 2 2026):
    // prices render ONLY from the fetched RevenueCat offering, so no
    // string in the paywall path may carry one — a user assigned a
    // $19.99/$29.99 experiment offering must never see the $9.99 price.
    const val S8_STATE_B_SUBSCRIBE_SUB = S18_SETTINGS_PREMIUM_LINE
    const val S9_MANAGE_SUBSCRIPTION = "Manage subscription"
    const val S10_DELETE_ROW = "Delete my photos"
    const val S11_DELETE_CONFIRM_TITLE = "Delete your photos?"
    const val S12_DELETE_CONFIRM_BODY = "Your photos and every picture made from them will be deleted from our server and this phone. Your outfit pictures go back to the standard photos, and Make it me turns off. This can't be undone."
    const val S13_DELETE_CONFIRM = "Delete my photos"
    const val S13_KEEP = "Keep my photos"
    const val S14_POST_DELETE = "Your photos and pictures are deleted."

    // S20–S25 added Nov 2, 2026 (RW-16.30, privacy decision 5a):
    // Delete my data — the full account deletion, one step beyond
    // S10–S14's photos-only erasure. The body keeps S12's scope
    // discipline (server + phone, what goes, no undo) and adds the
    // two facts the photos copy doesn't carry: the account itself
    // goes, and deleting data does NOT cancel a store subscription.
    const val S20_DELETE_DATA_ROW = "Delete my data"
    const val S21_DELETE_DATA_CONFIRM_TITLE = "Delete all your data?"
    const val S22_DELETE_DATA_CONFIRM_BODY = "Everything RunWear holds for you will be deleted from our server and this phone: your photos, every picture made from them, and your account itself. This can't be undone. Deleting your data does not cancel a subscription — you can manage that from your store account."
    const val S23_DELETE_DATA_CONFIRM = "Delete my data"
    const val S24_DELETE_DATA_KEEP = "Keep my data"
    const val S25_POST_DELETE_DATA = "Your data and account are deleted."

    /** S4 — free-tier sub-status with the user's real count. */
    fun s4FreeUsed(used: Int): String =
        if (used >= 5) "All 5 free looks used" else "$used of 5 free looks used"

    // U — Upsell and subscription sheet (spec §5)
    const val U1A_EYEBROW = "MAKE IT ME"
    const val U1B_HEADLINE = "This outfit isn't you — yet."
    const val U1C_BODY = "You've seen yourself in 5 outfits. This is a new one, so it wears a standard photo. Subscribe, and RunWear makes your photo for every outfit in your weather ahead of time — you, on screen the moment you open the app."
    const val U1D_PRIMARY = "See myself in every outfit"
    const val U1E_NOT_NOW = "Not now"
    // RW-26 frame 22: the paywall's title is the "Make It Me" SCRIPT
    // wordmark (rendered in Great Vibes by the sheet); this string is
    // its text. The highlighted card is "Premium" / "All of Make It
    // Me." and the CTA carries NO price.
    const val U2A_SHEET_TITLE = "Make It Me"
    const val U2D_FREE_BAR = "Free plan · 5 looks included"
    const val U2E_HOW_PREMIUM_WORKS = "How Premium works"
    const val U2F_HOW_PREMIUM_WORKS_BODY = "Looks of you for every scenario. This week's weather is made first; the rest are made as you need them, and each one is banked for good once it's made."
    const val U2G_CARD_TITLE = "Premium"
    const val U2H_CARD_LINE = "All of Make It Me."
    const val U2I_CTA = "Keep going with Premium"
    /** Free-looks meter line (frame 22 verbatim pattern, real numbers). */
    fun u2MeterLine(used: Int): String =
        "$used of 5 used · Free looks never expire — and your first ones take about a minute to make."
    // Fallback row labels shown only until the current offering
    // resolves (the rows stay disabled until then). Price-free on
    // purpose — see S8 note above. Resolved rows are built by
    // [monthlyRowLabel] / [annualRowLabel] from the offering's own
    // formatted prices.
    const val U2B_MONTHLY = "Monthly"
    const val U2B_ANNUAL = "Annual"

    /** U2 monthly row, from the offering's formatted price. */
    fun monthlyRowLabel(monthlyFormatted: String): String = "$monthlyFormatted / month"

    /**
     * U2 annual row, from the offering's formatted price. The
     * "less than paying monthly" suffix is COMPUTED from the two
     * fetched amounts and appears only when the annual price really
     * is lower than twelve monthly payments — at the base prices
     * ($0.99/mo, $9.99/yr) it reproduces the original "about 16%"
     * copy; at the $19.99/$29.99 experiment arms it correctly
     * disappears instead of making a false claim.
     */
    fun annualRowLabel(
        annualFormatted: String,
        annualAmountMicros: Long,
        monthlyAmountMicros: Long?
    ): String {
        val pct = annualSavingsPercent(annualAmountMicros, monthlyAmountMicros)
        return if (pct != null) {
            "$annualFormatted / year — about $pct% less than paying monthly"
        } else {
            "$annualFormatted / year"
        }
    }

    /** Whole-percent saving of annual vs 12 × monthly, or null when
     *  annual is not cheaper (or monthly is unknown). */
    fun annualSavingsPercent(annualAmountMicros: Long, monthlyAmountMicros: Long?): Int? {
        if (monthlyAmountMicros == null || monthlyAmountMicros <= 0L) return null
        val monthlyYearMicros = monthlyAmountMicros * 12L
        if (annualAmountMicros >= monthlyYearMicros) return null
        return Math.round(
            (1.0 - annualAmountMicros.toDouble() / monthlyYearMicros.toDouble()) * 100.0
        ).toInt()
    }
    const val U2C_FINE_PRINT = "Your outfits for your local weather are made when you subscribe. A brand-new outfit or a new place takes about 10 seconds to make the first time — after that, it's ready instantly. Cancel any time in your App Store or Google Play subscriptions."

    // RW-26 §9 frame 34: the first-look band — the recommended pair,
    // verbatim. The concept labels ("immediate results"/"unlimited
    // results") must NOT appear as copy anywhere.
    const val FIRST_LOOK_BAND_READINESS = "Ready the moment you open the app — made ahead, never while you wait."
    const val FIRST_LOOK_BAND_NO_CAP = "Every outfit, every day — no number to run out of."
    const val FIRST_LOOK_PRIMARY = "See My Outfit"
    const val FIRST_LOOK_SECOND_DOOR = "See Premium"

    /**
     * The consent-text version recorded with the backend on opt-in
     * (personal_consents.consent_version). Bump when §7's C1 body changes.
     */
    const val CONSENT_VERSION = "rw14-consent-v1"

    /**
     * Operational (non-spec) transport-failure line for the upload flow.
     * The spec defines no string for a dead network — this is UI plumbing,
     * not product copy, and is flagged as such in the build report.
     */
    const val TRANSPORT_ERROR = "Couldn't reach the server. Check your connection and try again."
}
