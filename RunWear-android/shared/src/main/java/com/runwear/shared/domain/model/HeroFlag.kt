package com.runwear.shared.domain.model

/**
 * Why a user flagged a hero photo. [code] is the stable value stored in
 * `public.hero_flags.reason` (the table carries a CHECK constraint on
 * exactly these codes); [label] is the text shown in the flag sheet.
 *
 * Flags are the human quality signal for the hero pool: because new
 * generations auto-approve (Sol's 2026-10-27 auto-replenishment call),
 * a flag is how a bad photo gets reported for manual review and
 * retraction. Nothing acts on a flag automatically.
 */
enum class HeroFlagReason(val code: String, val label: String) {
    LOOKS_BROKEN("looks_broken", "Looks broken"),
    INAPPROPRIATE("inappropriate", "Inappropriate"),
    OUTFIT_MISMATCH("outfit_mismatch", "Doesn't match the outfit")
}
