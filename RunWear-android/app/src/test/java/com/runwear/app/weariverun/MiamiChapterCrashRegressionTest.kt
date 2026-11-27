package com.runwear.app.weariverun

import android.app.Application
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-5 (RW-28) — REGRESSION for the Miami crash (Sol's Fold8,
 * gen-4 1.2.20, Nov 5, 2026): tapping the collated Miami, FL
 * chapter killed the app ("this app has a bug" system dialog,
 * then a black screen on relaunch).
 *
 * DIAGNOSED MECHANISM (diagnosis before fix, per Sol's rule):
 * Miami's chapter held TWO entries for the SAME date (Nov 5) at
 * the same place — two sessions in one day (the Nov 4-evening
 * walkthrough, whose Miami-local date was already Nov 5, and the
 * Nov 5-morning walkthrough). `staysOf` split them into two stays
 * with the SAME start date (its rule merged only
 * consecutive-date neighbours), and ChapterDetail keyed its lazy
 * items by that start date and by (date, place) — duplicate
 * LazyColumn keys, an IllegalArgumentException the moment the
 * detail composed. Kansas City's chapter (Nov 3 + Nov 5 entries,
 * distinct stay starts) opened fine.
 *
 * Sol's thumbnail hypothesis was tested first: Miami's cover
 * entry indeed has NO photo (heroRef null — visible in his
 * recording as the blank thumbnail slot), but every dereference
 * of heroRef on the place-open path is null-checked (ChapterCover,
 * the TODAY card, MorningPoster), so a missing thumbnail cannot
 * throw there; it is a co-occurring trait of the same entry, not
 * the mechanism. The fix still gives a thumbnail-less entry the
 * designed placeholder treatment, and this suite includes the
 * thumbnail-less shape in the open-every-entry set.
 *
 * The data below reproduces Sol's collection as recorded:
 * Kansas City TODAY (36°, 6 items) + a collated Kansas City
 * chapter (2 stays · 2 days · Last stay Nov 5 · 36°) + a collated
 * Miami chapter (2 stays · 2 days · Last stay Nov 5 · 78°, cover
 * thumbnail missing).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class MiamiChapterCrashRegressionTest : WearElementHarness() {

    private val today: LocalDate = LocalDate.of(2026, 11, 5)

    private fun entry(
        date: LocalDate,
        place: String,
        feels: Double,
        items: List<String>,
        sessionId: String,
        heroRef: String?,
        settledAt: Long
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = feels,
        conditionLabel = if (feels > 60) "Clear sky" else "Clear sky",
        isSnowing = false, isRaining = false,
        items = items, heroRef = heroRef,
        sessionId = sessionId, settledAtMillis = settledAt
    )

    /** Sol's recorded collection (see class doc). */
    private fun solEntries(): List<WearIveRunDayEntry> = listOf(
        // Miami, session 2 (most recent; its photo never landed —
        // the missing thumbnail Sol spotted).
        entry(
            today, "Miami, FL", 78.0,
            listOf("Tank Top / Singlet", "Short Shorts (3\")", "Reflective Gear"),
            "session-miami-2", null, 3_000L
        ),
        // Kansas City TODAY session entry (the pinned card).
        entry(
            today, "Kansas City, MO", 36.0,
            listOf(
                "Ear Warmer / Headband", "Light Long Sleeve",
                "Quarter-Zip Pullover", "Light Tights", "Light Gloves",
                "Reflective Gear"
            ),
            "session-kc-today", "https://example.invalid/kc-today.jpg", 4_000L
        ),
        // Miami, session 1 — SAME date, different session.
        entry(
            today, "Miami, FL", 78.0,
            listOf("Tank Top / Singlet", "Short Shorts (3\")", "Reflective Gear"),
            "session-miami-1", "https://example.invalid/miami-1.jpg", 1_000L
        ),
        // Kansas City chapter: an older stay + a Nov 5 stay.
        entry(
            LocalDate.of(2026, 11, 3), "Kansas City, MO", 36.0,
            listOf("Light Long Sleeve", "Light Tights"),
            "session-kc-old", "https://example.invalid/kc-old.jpg", 500L
        ),
        entry(
            today, "Kansas City, MO", 36.0,
            listOf("Light Long Sleeve", "Light Tights"),
            "session-kc-2", "https://example.invalid/kc-2.jpg", 2_000L
        )
    )

    @Test
    fun `miami shape - two same-date session entries form ONE stay and the cover is the newest entry with a loadable photo`() {
        // RE-PINNED (RW-152 §3 + §4, Nov 10, 2026 — supersedes the
        // RW-136-era cover rule): the cover is the newest entry
        // WITH a loadable reference (here session-miami-1), not
        // the newest entry overall — the cover carries the delete
        // affordance now (J2: its trash deletes the cover entry),
        // so it must be a real photo-bearing entry when one
        // exists. The RW-136 no-substitution rule stands in
        // spirit: the cover IS that entry, photo and identity
        // together — never another entry's photo on this entry.
        val chapters = WearIveRunCollectionLogic.chapters(solEntries())
        val miami = chapters.first { it.place == "Miami, FL" }
        // Pre-fix this read 2 stays with the same start date — the
        // duplicate-key setup. One day at one place is one stay.
        assertEquals(1, miami.stayCount)
        // RW-29 FA-23: two entries on ONE calendar day count as
        // ONE day (the gen-4 count read "2 days" for this shape —
        // the very line in Sol's recording).
        assertEquals(1, miami.dayCount)
        assertEquals(2, miami.days.size)
        assertEquals("session-miami-1", miami.cover?.sessionId)
        assertEquals("https://example.invalid/miami-1.jpg", miami.cover?.heroRef)
        val kc = chapters.first { it.place == "Kansas City, MO" }
        assertEquals(2, kc.stayCount)
    }

    @Test
    fun `opening the miami chapter renders both entries without crashing`() {
        val chapters = WearIveRunCollectionLogic.chapters(solEntries())
        val miami = chapters.first { it.place == "Miami, FL" }
        val r = render("gen5-miami-chapter", dark = false) {
            ChapterDetail(chapter = miami, today = today, onShare = {})
        }
        val all = texts(r.root)
        assertTrue("place header renders", all.contains("Miami, FL"))
        assertTrue(
            "both same-day entries render their kit",
            all.count { it.contains("Tank Top / Singlet") } >= 2
        )
    }

    @Test
    fun `opening the kansas city chapter renders without crashing`() {
        val chapters = WearIveRunCollectionLogic.chapters(solEntries())
        val kc = chapters.first { it.place == "Kansas City, MO" }
        val r = render("gen5-kc-chapter", dark = false) {
            ChapterDetail(chapter = kc, today = today, onShare = {})
        }
        val all = texts(r.root)
        assertTrue(all.contains("Kansas City, MO"))
        assertTrue(all.any { it.contains("Light Long Sleeve") })
    }

    @Test
    fun `the today card renders with its last saved line`() {
        val todayEntry = WearIveRunCollectionLogic.todaysEntry(solEntries(), today)
        assertEquals("Kansas City, MO", todayEntry?.place)
        val label = WearIveRunCollectionLogic.lastSavedLabel(
            todayEntry!!, todayEntry.settledAtMillis + 30_000L
        )
        assertEquals("Last saved just now", label)
        val r = render("gen5-today-card", dark = false) {
            DayCard(
                entry = todayEntry,
                today = today,
                onShare = {},
                place = todayEntry.place,
                thumbnailRef = todayEntry.heroRef,
                lastSavedLabel = label,
                onView = {}
            )
        }
        val all = texts(r.root)
        assertTrue(all.contains("Kansas City, MO"))
        assertTrue(all.contains("Last saved just now"))
        assertTrue(all.contains("View Kansas City, MO chapter"))
    }

    @Test
    fun `a thumbnail-less chapter cover renders its placeholder slot without crashing`() {
        // RE-PINNED fixture (RW-152 §3, Nov 10, 2026): under the
        // cover-selection rule the recorded Miami chapter's cover
        // now HAS a photo, so the honest-empty-slot case is
        // exercised on a chapter whose entries ALL lack a loadable
        // reference — the cover falls back to the newest entry and
        // its slot renders the placeholder treatment.
        val chapters = WearIveRunCollectionLogic.chapters(
            listOf(
                entry(
                    today, "Miami, FL", 78.0,
                    listOf("Tank Top / Singlet"),
                    "session-miami-x", null, 3_000L
                )
            )
        )
        val miami = chapters.first { it.place == "Miami, FL" }
        assertEquals("session-miami-x", miami.cover?.sessionId)
        val r = render("gen5-miami-cover", dark = false) {
            ChapterCover(chapter = miami, onClick = {})
        }
        val all = texts(r.root)
        assertTrue(all.contains("Miami, FL"))
        assertTrue(all.any { it.contains("78°") })
    }
}
