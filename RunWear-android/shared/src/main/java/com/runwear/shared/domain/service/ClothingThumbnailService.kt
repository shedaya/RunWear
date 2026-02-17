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

    /**
     * Get the thumbnail URL for a clothing item based on gender preference.
     *
     * @param item The clothing item
     * @param gender The user's gender preference (UNISEX defaults to male)
     * @return The full URL to the thumbnail image, or null if no thumbnail exists
     */
    fun getThumbnailUrl(item: ClothingItem, gender: GenderPreference): String? {
        val thumbnailKey = THUMBNAIL_KEYS[item] ?: return null
        val genderSuffix = when (gender) {
            GenderPreference.FEMALE -> "female"
            else -> "male" // MALE and UNISEX both use male thumbnails
        }
        return "$BASE_URL/$thumbnailKey-$genderSuffix.webp"
    }

    /**
     * Check if a clothing item has a thumbnail available.
     */
    fun hasThumbnail(item: ClothingItem): Boolean {
        return THUMBNAIL_KEYS.containsKey(item)
    }
}
