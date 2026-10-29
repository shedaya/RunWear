package com.runwear.shared.domain.service

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference

/**
 * Service for generating clothing thumbnail URLs.
 *
 * Thumbnails are AI-generated product images stored in Supabase Storage.
 * 52 total images: 26 clothing items × 2 genders (male/female).
 *
 * Style: Dark gray/charcoal clothing on pure black background,
 * professional product photography aesthetic.
 */
object ClothingThumbnailService {

    private const val BASE_URL = "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/public/clothing-thumbnails"

    /**
     * Map ClothingItem enum names to thumbnail file keys.
     * Keys use lowercase with hyphens (e.g., TANK_TOP → tank-top).
     */
    private val THUMBNAIL_KEYS: Map<ClothingItem, String> = mapOf(
        // Tops - Base Layer
        ClothingItem.TANK_TOP to "tank-top",
        ClothingItem.SHORT_SLEEVE to "short-sleeve",
        ClothingItem.LONG_SLEEVE_LIGHT to "long-sleeve-light",
        ClothingItem.LONG_SLEEVE_THERMAL to "thermal-long-sleeve",

        // Tops - Mid Layer
        ClothingItem.QUARTER_ZIP to "quarter-zip",
        ClothingItem.FLEECE_PULLOVER to "fleece-pullover",
        ClothingItem.HALF_ZIP_THERMAL to "half-zip-thermal",

        // Tops - Outer Layer
        ClothingItem.LIGHT_VEST to "light-vest",
        ClothingItem.WINDBREAKER to "windbreaker",
        ClothingItem.LIGHT_JACKET to "light-jacket",
        ClothingItem.RAIN_JACKET to "rain-jacket",
        ClothingItem.INSULATED_JACKET to "insulated-jacket",

        // Bottoms
        ClothingItem.SHORT_SHORTS to "short-shorts",
        ClothingItem.SHORTS to "running-shorts",
        ClothingItem.CAPRIS to "light-tights", // Capris use light-tights thumbnail
        ClothingItem.LIGHT_TIGHTS to "light-tights",
        ClothingItem.THERMAL_TIGHTS to "thermal-tights",

        // Head
        ClothingItem.VISOR to "visor",
        ClothingItem.BASEBALL_CAP to "running-cap",
        ClothingItem.HEADBAND to "headband",
        ClothingItem.LIGHT_BEANIE to "light-beanie",
        ClothingItem.THERMAL_BEANIE to "thermal-beanie",
        ClothingItem.BALACLAVA to "balaclava",

        // Hands
        ClothingItem.LIGHT_GLOVES to "light-gloves",
        ClothingItem.THERMAL_GLOVES to "thermal-gloves",
        ClothingItem.MITTENS to "mittens",

        // Accessories
        ClothingItem.SUNGLASSES to "sunglasses",
        ClothingItem.SUNSCREEN to "sunscreen",
        ClothingItem.REFLECTIVE_GEAR to "reflective-gear",
        ClothingItem.NECK_GAITER to "neck-gaiter"
    )

    private fun genderSuffix(gender: GenderPreference): String = when (gender) {
        GenderPreference.FEMALE -> "female"
        else -> "male" // MALE and UNISEX both use male thumbnails
    }

    /**
     * Get the thumbnail URL for a clothing item based on gender preference.
     *
     * @param item The clothing item
     * @param gender The user's gender preference (UNISEX defaults to male)
     * @return The full URL to the thumbnail image, or null if no thumbnail exists
     */
    fun getThumbnailUrl(item: ClothingItem, gender: GenderPreference): String? {
        val thumbnailKey = THUMBNAIL_KEYS[item] ?: return null
        return "$BASE_URL/$thumbnailKey-${genderSuffix(gender)}.webp"
    }

    /**
     * Path of the BUNDLED copy of the thumbnail inside the app's assets
     * (`assets/thumbnails/…`), or null if the item has no thumbnail. The set
     * is closed (26 items × 2 genders), so the full set ships with the app
     * and cards never wait on the network for a known item; the remote URL
     * from [getThumbnailUrl] is the fallback only (e.g. an asset that fails
     * to decode, or an item added before its bundle ships).
     */
    fun getThumbnailAssetPath(item: ClothingItem, gender: GenderPreference): String? {
        val thumbnailKey = THUMBNAIL_KEYS[item] ?: return null
        return "thumbnails/$thumbnailKey-${genderSuffix(gender)}.webp"
    }

    /** `file:///android_asset/` URI for [getThumbnailAssetPath], loadable by Coil as-is. */
    fun getThumbnailAssetUri(item: ClothingItem, gender: GenderPreference): String? =
        getThumbnailAssetPath(item, gender)?.let { "file:///android_asset/$it" }

    /**
     * Check if a clothing item has a thumbnail available.
     */
    fun hasThumbnail(item: ClothingItem): Boolean {
        return THUMBNAIL_KEYS.containsKey(item)
    }

    // -- Name-based seam (Apple platforms) --

    /**
     * The name-based seam the Apple platforms consume: they hold item
     * identities as [ClothingItem] enum case names (the `key`), not as
     * Kotlin enum instances. Unknown names resolve to null / false —
     * never an exception — so a name the engine doesn't know renders as
     * "no thumbnail", exactly like an unmapped item on Android.
     */
    private fun itemForName(itemName: String): ClothingItem? =
        ClothingItem.entries.find { it.name == itemName }

    private fun genderForName(genderName: String): GenderPreference =
        GenderPreference.entries.find { it.name == genderName } ?: GenderPreference.UNISEX

    /** Thumbnail file key for an item name (e.g. "TANK_TOP" → "tank-top"), or null. */
    fun thumbnailKeyForName(itemName: String): String? =
        itemForName(itemName)?.let { THUMBNAIL_KEYS[it] }

    /** [getThumbnailUrl] by item name and gender name ("MALE" / "FEMALE" / "UNISEX"). */
    fun getThumbnailUrl(itemName: String, genderName: String): String? {
        val item = itemForName(itemName) ?: return null
        return getThumbnailUrl(item, genderForName(genderName))
    }

    /** [hasThumbnail] by item name. */
    fun hasThumbnail(itemName: String): Boolean {
        return thumbnailKeyForName(itemName) != null
    }
}
