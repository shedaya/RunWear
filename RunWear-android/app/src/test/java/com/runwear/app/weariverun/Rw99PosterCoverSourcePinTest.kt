package com.runwear.app.weariverun

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RE-PINNED (RW-152 §3, Nov 10, 2026 — supersedes the RW-99 pin
 * below in full): collection covers no longer compose the poster
 * at all. The RW-99 structure (a 360×450 poster canvas held at
 * natural size and scaled into the slot) is retired for
 * collection surfaces: covers and stay cards load the entry's
 * photo DIRECTLY through the loader, resolved through
 * HeroRefResolver, at display size. The 360×450 MorningPoster
 * composition remains only for share/export.
 *
 * Original RW-99 pin (Nov 6, 2026): the collection cover's scaled
 * render could not be pixel-proved in the Robolectric harness,
 * so the fix's structure was pinned at source level: PosterCover
 * composed the poster canvas at its natural 360×450 via
 * required-size modifiers and scaled it into the slot through
 * the graphics layer. RW-152 §3's measured verdict (design of
 * record §3) is that this indirection is also why covers read
 * blank in the shipped build; the direct loader replaces it.
 */
class Rw99PosterCoverSourcePinTest {

    private fun source(): String {
        val path =
            "src/main/java/com/runwear/app/weariverun/WearIveRunCollectionScreen.kt"
        val file = File(path)
        assertTrue("source file readable at $path", file.exists())
        return file.readText()
    }

    @Test
    fun `collection covers load directly through the resolver - no poster composition`() {
        val src = source()
        assertFalse(
            "the poster-composition cover is retired (RW-152 §3)",
            src.contains("fun PosterCover")
        )
        assertFalse(
            "no scaled-canvas trick remains on collection surfaces",
            src.contains("requiredWidth(360.dp)")
        )
        assertTrue(
            "the thumbnail loads through the collection loader",
            src.contains("fun CollectionThumbnail")
        )
        assertTrue(
            "the loader requests at display size x2",
            src.contains("widthPx * 2")
        )
        assertTrue(
            "the thumbnail resolves through HeroRefResolver",
            src.contains("HeroRefResolver.resolveDisplay")
        )
        assertTrue(
            "the photo keeps the settled top-centre poster framing",
            src.contains("Alignment.TopCenter")
        )
    }
}
