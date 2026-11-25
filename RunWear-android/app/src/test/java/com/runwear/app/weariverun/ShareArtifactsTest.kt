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
 *    caller's composed caption carried verbatim — for BOTH
 *    collection share choices. A text-only payload is
 *    unrepresentable in [SharePayload]'s spec and absent from
 *    the sources. (GEN-6 Wave B: the caption is a parameter
 *    composed by [ShareCaptionComposer]; the gen-5 bare-hashtag
 *    constant is retired.)
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

    private fun shareStudioComponents() = sourceFile(
        "app/src/main/java/com/runwear/app/weariverun/ShareStudioComponents.kt"
    ).readText()

    private fun heroMainScreen() = sourceFile(
        "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"
    ).readText()

    private fun heroSection() = sourceFile(
        "app/src/main/java/com/runwear/app/ui/components/HeroSection.kt"
    ).readText()

    // ---- The payload shape ----

    @Test
    fun `both share choices produce an image payload carrying the composed caption verbatim`() {
        // GEN-6 Wave B (RW-31): the caption is a payload parameter
        // now — composed by ShareCaptionComposer from the day
        // record, carried verbatim for BOTH collection share
        // choices. The bare-hashtag constant is retired.
        val caption = ShareCaptionComposer.caption(
            ShareCaptionTemplate.FEELS_LIKE_STORY,
            ShareCaptionInput(
                place = "Kansas City, MO",
                captionMoment = "this morning",
                isPastChapter = false,
                feels = 57, actual = 61, low = 55, high = 63,
                swingF = 8.0, feelsF = 57.0,
                conditionLower = "clear sky",
                kitNames = listOf("Short-sleeve shirt", "Running shorts", "Sunglasses")
            )
        )
        for (kind in ShareArtifactKind.entries) {
            val spec = SharePayload.spec(kind, caption)
            assertEquals("image/png", spec.mimeType)
            assertEquals(caption, spec.caption)
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
        // GEN-6 Wave B (stage 2): the payload is the OFFSCREEN
        // EXPORT RENDER, verified before the share fires. The
        // collection screen opens the Share Studio; the Studio
        // renders via ShareExportRenderer, gates on
        // ShareRasterVerifier, and hands the verified bitmap to
        // ShareArtifacts.share. The gen-5 preview capture
        // (ShareCaptureHandle / captureBitmap) is deleted — the
        // share path must not contain it anywhere.
        val screen = collectionScreen()
        assertFalse(screen.contains("fireShare"))
        assertFalse(screen.contains("text/plain"))
        assertTrue(
            "the collection's share doors open the Share Studio",
            screen.contains("ShareStudioSheet")
        )
        assertFalse(
            "the old chooser sheet is deleted",
            screen.contains("ShareChoiceSheet")
        )
        val studio = shareStudioComponents()
        assertTrue(studio.contains("ShareExportRenderer"))
        assertTrue(studio.contains("ShareRasterVerifier"))
        assertTrue(studio.contains("ShareArtifacts.share"))
        assertFalse(studio.contains("captureBitmap"))
        val artifacts = shareArtifacts()
        assertTrue(artifacts.contains("Intent.EXTRA_STREAM"))
        assertTrue(artifacts.contains("Bitmap.CompressFormat.PNG"))
        assertTrue(artifacts.contains("FileProvider.getUriForFile"))
        assertFalse(
            "the capture handle is retired from the payload path",
            artifacts.contains("ShareCaptureHandle")
        )
        assertFalse(artifacts.contains("captureBitmap"))
        assertFalse(artifacts.contains("toImageBitmap()"))
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
        // GEN-6 Wave B: the ONE share surface is the Studio's M3
        // modal sheet (in ShareStudioComponents.kt); the
        // collection screen itself hosts no second sheet.
        val studio = shareStudioComponents()
        assertTrue(studio.contains("ModalBottomSheet"))
        assertTrue(studio.contains("skipPartiallyExpanded = true"))
        val screen = collectionScreen()
        assertFalse(screen.contains("ModalBottomSheet"))
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
                PersonalHeroFlagReason.WRONG_OUTFIT,
                PersonalHeroFlagReason.SOMETHING_ELSE
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
