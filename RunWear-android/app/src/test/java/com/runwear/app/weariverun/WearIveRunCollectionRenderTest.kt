package com.runwear.app.weariverun

import android.app.Application
import com.runwear.shared.domain.model.GenderPreference
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-2 (W-1b, W-9, W-10): rendered evidence for the collection
 * fixes, on the production composables:
 *
 * - W-1 (b): a chapter row carries TWO buttons on its right side —
 *   a view button (opens that chapter) and a share button (opens
 *   the share flow for the chapter's most recent day);
 * - W-10: the pinned TODAY card names its place and shows its photo
 *   thumbnail, like the chapter cards;
 * - W-9: the "Share this day" chooser presents rendered THUMBNAILS
 *   of the Morning Poster and the Kit Card (content descriptions
 *   name them as thumbnails), and tapping one selects that card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class WearIveRunCollectionRenderTest : WearElementHarness() {

    private val today: LocalDate = LocalDate.of(2026, 11, 4)

    private fun day(
        date: LocalDate,
        place: String = "Kansas City, MO",
        items: List<String> = listOf("Light Long Sleeve", "Light Tights")
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = 44.0,
        conditionLabel = "Overcast", isSnowing = false, isRaining = false,
        items = items, heroRef = null
    )

    @Test
    fun chapterRowCarriesViewAndShareButtonsOnItsRightSide() {
        var views = 0
        var shared: WearIveRunDayEntry? = null
        val chapter = WearIveRunCollectionLogic.chapters(
            listOf(day(today), day(today.minusDays(1)))
        ).single()
        val r = render("gen2-chapter-row", dark = false) {
            ChapterCover(
                chapter = chapter,
                onClick = { views++ },
                onShare = { shared = it }
            )
        }
        val all = texts(r.root)
        assertTrue(
            "view button present",
            all.contains("View Kansas City, MO chapter")
        )
        // GEN-5 (RW-29 FA-66): the cover's share door NAMES the
        // day it will share — the gen-4 bare "share a day" door
        // silently picked the most recent day.
        assertTrue(
            "share door names the day it shares",
            all.contains("Share Nov 4 from Kansas City, MO")
        )
        clickByDescription(r.root, "View Kansas City, MO chapter")
        assertEquals(1, views)
        clickByDescription(r.root, "Share Nov 4 from Kansas City, MO")
        assertNotNull("share must hand over a day", shared)
        assertEquals("the chapter's most recent day is shared", today, shared!!.date)
        assertEquals("Kansas City, MO", shared!!.place)
    }

    @Test
    fun todayCardNamesItsPlaceAndShowsItsThumbnailSlot() {
        val entry = day(today)
        val r = render("gen2-today-card", dark = false) {
            DayCard(
                entry = entry,
                today = today,
                onShare = {},
                place = entry.place,
                thumbnailRef = entry.heroRef
            )
        }
        val all = texts(r.root)
        assertTrue(
            "the TODAY card must name its place",
            all.any { it == "Kansas City, MO" }
        )
        assertTrue(
            "the TODAY card keeps its day line",
            all.any { it.contains("Today · Wed, Nov 4") }
        )
        // The thumbnail slot renders at the same 56dp geometry as
        // the chapter cards / expanded element: its centre pixel is
        // inside the card (not the theme ground showing through).
        val cx = (12 * r.density + 28 * r.density).toInt()
        val cy = (12 * r.density + 28 * r.density).toInt()
        val ground = 0xFFF5F1E8.toInt()
        assertTrue(
            "thumbnail slot must be composed on the card",
            r.bitmap.getPixel(cx, cy) != ground
        )
    }

    @Test
    fun shareDoorsOpenTheStudioWithRenderedThumbnails() {
        // GEN-6 Wave B reconciliation: the gen-2/gen-5 chooser
        // stage (ShareChooserStage, composed live in the sheet) is
        // replaced by the Share Studio — a ModalBottomSheet, which
        // cannot be driven under Robolectric — so the chooser's
        // rendered-thumbnail contract is pinned at the source:
        // the collection's share doors open ShareStudioSheet, and
        // the Studio presents both cards as rendered-export
        // thumbnails (semantics name them as thumbnails), not bare
        // text rows.
        fun source(relative: String): String {
            var dir: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
            while (dir != null) {
                for (candidate in listOf(
                    relative,
                    "RunWear-android/$relative",
                    "../$relative"
                )) {
                    val file = java.io.File(dir, candidate)
                    if (file.isFile) return file.readText()
                }
                dir = dir.parentFile
            }
            throw AssertionError("source not found: $relative")
        }
        val screen = source(
            "app/src/main/java/com/runwear/app/weariverun/WearIveRunCollectionScreen.kt"
        )
        assertTrue(
            "the collection's share doors open the Share Studio",
            screen.contains("ShareStudioSheet")
        )
        assertTrue(
            "the gen-5 chooser sheet is deleted",
            !screen.contains("ShareChooserStage")
        )
        val studio = source(
            "app/src/main/java/com/runwear/app/weariverun/ShareStudioComponents.kt"
        )
        // WP-R2 re-pin: the studio's door labels are resources.
        assertTrue(studio.contains("R.string.share_this_day"))
        assertTrue(studio.contains("R.string.share_card_morning_poster"))
        assertTrue(studio.contains("R.string.share_kit_card"))
        assertTrue(
            "poster choice is a rendered thumbnail, not a bare text row",
            studio.contains("R.string.share_card_morning_poster") &&
                studio.contains("thumbnail")
        )
        assertTrue(
            "kit choice is a rendered thumbnail, not a bare text row",
            // WP-R2 re-pin: the kit label is the share_kit_card resource.
            studio.contains("R.string.share_kit_card") &&
                studio.contains("thumbnail")
        )
    }
}
