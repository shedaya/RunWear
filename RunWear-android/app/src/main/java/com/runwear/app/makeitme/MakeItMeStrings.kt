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
    // pass. G5's wording is a PLACEHOLDER pending Sol's pick from
    // the options put to him Nov 1, 2026 — wired as this single
    // constant so the swap is a one-string change. Verbatim-
    // identical to the iOS deck (MakeItMeCopy.lookFailed).
    const val G5_LOOK_FAILED = "We couldn't make this look yet — tap to try again."
    const val G6_RETRY = "Try again"

    // S — Settings (spec §3)
    const val S1_SECTION_HEADER = "Make it me"
    const val S2_STATE_A_ROW = "See yourself in every outfit"
    const val S2_STATE_A_SUB = "Make your outfit photos from your own photos."
    const val S3_STATUS = "You're in your photos."
    const val S5_STATE_C_SUB = "Subscribed — your photo is ready for every outfit, instantly."
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
    const val S8_STATE_B_SUBSCRIBE_ROW = "Every outfit, instantly"
    const val S8_STATE_B_SUBSCRIBE_SUB = "Subscribe, and your photo is ready before you open the app. $0.99/month."
    const val S9_MANAGE_SUBSCRIPTION = "Manage subscription"
    const val S10_DELETE_ROW = "Delete my photos"
    const val S11_DELETE_CONFIRM_TITLE = "Delete your photos?"
    const val S12_DELETE_CONFIRM_BODY = "Your photos and every picture made from them will be deleted from our server and this phone. Your outfit pictures go back to the standard photos, and Make it me turns off. This can't be undone."
    const val S13_DELETE_CONFIRM = "Delete my photos"
    const val S13_KEEP = "Keep my photos"
    const val S14_POST_DELETE = "Your photos and pictures are deleted."

    /** S4 — free-tier sub-status with the user's real count. */
    fun s4FreeUsed(used: Int): String =
        if (used >= 5) "All 5 free looks used" else "$used of 5 free looks used"

    // U — Upsell and subscription sheet (spec §5)
    const val U1A_EYEBROW = "MAKE IT ME"
    const val U1B_HEADLINE = "This outfit isn't you — yet."
    const val U1C_BODY = "You've seen yourself in 5 outfits. This is a new one, so it wears a standard photo. Subscribe, and RunWear makes your photo for every outfit in your weather ahead of time — you, on screen the moment you open the app."
    const val U1D_PRIMARY = "See myself in every outfit — $0.99/month"
    const val U1E_NOT_NOW = "Not now"
    const val U2A_SHEET_TITLE = "Every outfit, instantly."
    const val U2B_MONTHLY = "$0.99 / month"
    const val U2B_ANNUAL = "$9.99 / year — about 16% less than paying monthly"
    const val U2C_FINE_PRINT = "Your outfits for your local weather are made when you subscribe. A brand-new outfit or a new place takes about 10 seconds to make the first time — after that, it's ready instantly. Cancel any time in your App Store or Google Play subscriptions."

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
