package com.runwear.shared.domain.service

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundled-asset contract: for every item that has a thumbnail, the
 * asset path and the remote fallback URL must name the SAME file — the UI
 * tries the bundled copy first and falls back to the URL on failure, so a
 * drift between the two would silently fetch a different (or missing)
 * image. Asset files on disk: assets/thumbnails/<key>-<gender>.webp.
 */
class ClothingThumbnailServiceTest {

    private val genders = listOf(
        GenderPreference.MALE,
        GenderPreference.FEMALE,
        GenderPreference.UNISEX
    )

    @Test
    fun `asset path and remote url agree on the file name for every thumbnailed item`() {
        for (item in ClothingItem.entries) {
            for (gender in genders) {
                val url = ClothingThumbnailService.getThumbnailUrl(item, gender)
                val assetPath = ClothingThumbnailService.getThumbnailAssetPath(item, gender)
                assertEquals(
                    "url/asset presence must agree for $item/$gender",
                    url != null,
                    assetPath != null
                )
                if (url != null && assetPath != null) {
                    assertEquals(
                        "asset file must be the url's file for $item/$gender",
                        url.substringAfterLast('/'),
                        assetPath.substringAfterLast('/')
                    )
                    assertTrue(assetPath.startsWith("thumbnails/"))
                }
            }
        }
    }

    @Test
    fun `unisex resolves to the male files, female to female`() {
        val item = ClothingItem.LONG_SLEEVE_LIGHT
        assertEquals(
            ClothingThumbnailService.getThumbnailAssetPath(item, GenderPreference.MALE),
            ClothingThumbnailService.getThumbnailAssetPath(item, GenderPreference.UNISEX)
        )
        assertTrue(
            ClothingThumbnailService.getThumbnailAssetPath(item, GenderPreference.FEMALE)!!
                .endsWith("-female.webp")
        )
    }

    @Test
    fun `asset uri is the android_asset form of the asset path`() {
        val item = ClothingItem.TANK_TOP
        val path = ClothingThumbnailService.getThumbnailAssetPath(item, GenderPreference.MALE)
        assertNotNull(path)
        assertEquals(
            "file:///android_asset/$path",
            ClothingThumbnailService.getThumbnailAssetUri(item, GenderPreference.MALE)
        )
    }

    @Test
    fun `items without a thumbnail have neither url nor asset`() {
        // Every ClothingItem currently maps to a key; if one ever loses its
        // mapping, both lookups must agree it has no thumbnail.
        for (item in ClothingItem.entries) {
            if (!ClothingThumbnailService.hasThumbnail(item)) {
                assertNull(ClothingThumbnailService.getThumbnailUrl(item, GenderPreference.MALE))
                assertNull(ClothingThumbnailService.getThumbnailAssetPath(item, GenderPreference.MALE))
            }
        }
    }
}
