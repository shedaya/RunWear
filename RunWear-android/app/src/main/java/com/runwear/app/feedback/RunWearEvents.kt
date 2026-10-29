package com.runwear.app.feedback

/**
 * The RunWear analytics event set (spec §3.1) — outcomes of the
 * recommendation engine, not screen views. Names and prop keys are
 * exactly the intake's `runwear-events/1` allowlists
 * (feedback-intake/src/schemas.js); anything outside them is
 * rejected server-side with 422 — one bad event poisons its whole
 * batch — so this file is the one place event payloads are shaped.
 */
object RunWearEvents {

    /** An outfit was produced. Props: engineVersion, tempBand, comfortClass, itemCount. */
    const val OUTFIT_GENERATED = "outfit_generated"

    /** An item changed under identical conditions. Props: slot, itemName, replacementName. */
    const val ITEM_SWAPPED = "item_swapped"

    /** An item disappeared under identical conditions. Props: slot, itemName. */
    const val ITEM_REMOVED = "item_removed"

    /** The time frame flipped between Now and an explicit pick. Props: mode ("now" | "explicit"). */
    const val NOW_VS_EXPLICIT = "now_vs_explicit"

    /** An affiliate link was tapped. Props: category only — never the product, never the price. */
    const val AFFILIATE_TAPPED = "affiliate_tapped"

    fun outfitGeneratedProps(
        engineVersion: String,
        tempBand: String,
        comfortClass: String,
        itemCount: Int
    ): Map<String, Any?> = mapOf(
        "engineVersion" to engineVersion,
        "tempBand" to tempBand,
        "comfortClass" to comfortClass,
        "itemCount" to itemCount
    )

    fun itemSwappedProps(
        slot: String,
        itemName: String,
        replacementName: String
    ): Map<String, Any?> = mapOf(
        "slot" to slot,
        "itemName" to itemName,
        "replacementName" to replacementName
    )

    fun itemRemovedProps(slot: String, itemName: String): Map<String, Any?> = mapOf(
        "slot" to slot,
        "itemName" to itemName
    )

    fun nowVsExplicitProps(mode: String): Map<String, Any?> = mapOf("mode" to mode)

    fun affiliateTappedProps(category: String): Map<String, Any?> = mapOf("category" to category)
}
