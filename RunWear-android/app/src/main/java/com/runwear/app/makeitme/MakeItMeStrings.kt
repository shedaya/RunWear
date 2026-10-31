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
    const val C2C_CONFIRM_FIRST_LOOK = "Make my first look"
    const val C2C_CONFIRM_SAVE = "Save photos"
    const val C3A_REJECT_FACE_UNCLEAR = "We can't see your face clearly in this one. Try a photo without sunglasses or a hat shadow."
    const val C3B_REJECT_MULTIPLE = "This photo has more than one person in it. Pick one that's just you."
    const val C3C_REJECT_TOO_DARK = "This one is too dark to use. Try a photo in brighter light."
    const val C3D_REJECT_TOO_CLOSE = "This one is cropped too close. A head-and-shoulders or half-body photo works best."

    // G — First generation (spec §2.4). G3 (PROPOSAL P1) intentionally absent.
    // G4 is silence by spec — no string exists.
    const val G1_PROGRESS = "Making this outfit yours…"
    const val G2_PROGRESS_SUB = "About 10 seconds. Your recommendation is ready either way — the standard photo stays up until yours is done."

    // S — Settings (spec §3)
    const val S1_SECTION_HEADER = "Make it me"
    const val S2_STATE_A_ROW = "See yourself in every outfit"
    const val S2_STATE_A_SUB = "Make your outfit photos from your own photos."
    const val S3_STATUS = "You're in your photos."
    const val S5_STATE_C_SUB = "Subscribed — your photo is ready for every outfit, instantly."
    const val S6_PHOTOS_ROW = "Your photos"
    const val S6_PHOTOS_SUB = "View or replace"
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
