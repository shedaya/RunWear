package com.runwear.app.weariverun

import com.runwear.app.makeitme.PersonalFlagSheetContent
import com.runwear.app.makeitme.PersonalHeroFlagReason
import com.runwear.shared.domain.model.ClothingItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * WS-6 (D-15/D-16/D-17): the share payload's pins.
 *
 *  - The ONLY payload shape is an image stream (PNG) with the
 *    #RunAnyWear caption pre-filled — for BOTH collection share
 *    choices. A text-only payload is unrepresentable in
 *    [SharePayload]'s spec and absent from the sources.
 *  - The home text share is gone: no `shareOutfit`, no `text/plain`
 *    ACTION_SEND anywhere in the home or collection sources, and
 *    the photo-zone chrome carries no Share button (D-15).
 *  - The rasterizer is wired: the collection screen shares through
 *    [ShareArtifacts] (EXTRA_STREAM), never an inline intent.
 *  - Kit Card garment resolution: entry display names resolve to
 *    the engine items whose renders the card draws (D-16's "real
 *    garment renders"); unknown names resolve to nothing.
 *  - D-17: the personal flag sheet's hierarchy — the lead phrase
 *    is the heading (and its control), the consequence line is
 *    frame 45's, and the heading reason is not repeated as a row.
 */
class ShareArtifactsTest {

    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        val candidates = listOf(
            androidRootRelative,
            "RunWear-android/$androidRootRelative",
            "../$androidRootRelative"
        )
        while (dir != null) {
            for (candidate in candidates) {
                val file = File(dir, candidate)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        fail("source file not found: $androidRootRelative (user.dir=${System.getProperty("user.dir")})")
        throw AssertionError("unreachable")
    }

    private fun collectionScreen() = sourceFile(
        "app/src/main/java/com/runwear/app/weariverun/WearIveRunCollectionScreen.kt"
    ).readText()

    private fun shareArtifacts() = sourceFile(
        "app/src/main/java/com/runwear/app/weariverun/ShareArtifacts.kt"
    ).readText()

    private fun heroMainScreen() = sourceFile(
        "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"
    ).readText()

    private fun heroSection() = sourceFile(
        "app/src/main/java/com/runwear/app/ui/components/HeroSection.kt"
    ).readText()

    // ---- The payload shape ----

    @Test
    fun `both share choices produce an image payload with the hashtag caption`() {
        for (kind in ShareArtifactKind.entries) {
            val spec = SharePayload.spec(kind)
            assertEquals("image/png", spec.mimeType)
            assertEquals("#RunAnyWear", spec.caption)
            assertTrue(spec.carriesImageStream)
            assertFalse("no text-only payload exists", spec.carriesTextOnlyPayload)
            assertTrue(kind.chooserTitle.contains("Share"))
        }
    }

    // (The intent itself is asserted under Robolectric in
    // ShareArtifactRenderTest — android.content.Intent is stubbed
    // on the plain JVM classpath.)

    // ---- No text-only path survives ----

    @Test
    fun `the collection share sends the rasterized artifact, never text`() {
        val screen = collectionScreen()
        assertFalse(screen.contains("fireShare"))
        assertFalse(screen.contains("text/plain"))
        assertTrue(screen.contains("ShareArtifacts.share"))
        assertTrue(screen.contains("captureBitmap()"))
        val artifacts = shareArtifacts()
        assertTrue(artifacts.contains("Intent.EXTRA_STREAM"))
        assertTrue(artifacts.contains("Bitmap.CompressFormat.PNG"))
        assertTrue(artifacts.contains("FileProvider.getUriForFile"))
        // The capture readback itself (GraphicsLayer.toImageBitmap —
        // the device path; its Robolectric snapshot fallback is the
        // library's own limitation, see ShareArtifactRenderTest).
        assertTrue(artifacts.contains("toImageBitmap()"))
    }

    @Test
    fun `the home text share and its emoji payload are removed`() {
        val main = heroMainScreen()
        assertFalse(main.contains("shareOutfit"))
        assertFalse(main.contains("text/plain"))
        assertFalse(main.contains("🏃"))
        assertFalse(main.contains("Get your personalized running outfit"))
        val section = heroSection()
        assertFalse(section.contains("onShareClick"))
        assertFalse(section.contains("Icons.Outlined.Share"))
    }

    @Test
    fun `the share choice is an M3 sheet on the one full anchor`() {
        val screen = collectionScreen()
        assertTrue(screen.contains("ModalBottomSheet"))
        assertTrue(screen.contains("skipPartiallyExpanded = true"))
        assertFalse(screen.contains("ShareChoiceDialog"))
    }

    // ---- Kit Card garment renders ----

    @Test
    fun `kit card resolves entry names to the engine garment items`() {
        assertEquals(
            ClothingItem.LONG_SLEEVE_LIGHT,
            clothingItemForDisplayName("Light Long Sleeve")
        )
        assertEquals(
            ClothingItem.LIGHT_TIGHTS,
            clothingItemForDisplayName("Light Tights")
        )
        assertEquals(
            ClothingItem.REFLECTIVE_GEAR,
            clothingItemForDisplayName("Reflective Gear")
        )
        // Every display name the engine can put on an entry resolves.
        for (item in ClothingItem.entries) {
            assertEquals(item, clothingItemForDisplayName(item.displayName))
        }
        assertNull(clothingItemForDisplayName("Not A Real Garment"))
    }

    // ---- D-17 hierarchy ----

    @Test
    fun `the personal flag sheet leads with its phrase as the heading`() {
        assertEquals("This doesn't look like me", PersonalFlagSheetContent.heading)
        assertEquals(
            PersonalHeroFlagReason.DOESNT_LOOK_LIKE_ME,
            PersonalFlagSheetContent.headingReason
        )
        assertEquals(
            "doesnt_look_like_me", PersonalFlagSheetContent.headingReason.code
        )
        assertEquals(
            "We'll stop showing this photo and make it again.",
            PersonalFlagSheetContent.HEADING_CONSEQUENCE
        )
        // The heading reason is not demoted to (or repeated as) a row.
        assertFalse(PersonalFlagSheetContent.rowReasons.contains(PersonalFlagSheetContent.headingReason))
        assertEquals(
            listOf(
                PersonalHeroFlagReason.LOOKS_BROKEN,
                PersonalHeroFlagReason.INAPPROPRIATE,
                PersonalHeroFlagReason.OUTFIT_MISMATCH
            ),
            PersonalFlagSheetContent.rowReasons
        )
        // The sheet source renders the model: heading block first,
        // rows from rowReasons — and the pool sheet keeps its own
        // generic header (frame 45 is the personal variant only).
        val main = heroMainScreen()
        assertTrue(main.contains("PersonalFlagSheetContent.heading"))
        assertTrue(main.contains("PersonalFlagSheetContent.rowReasons"))
    }
}
