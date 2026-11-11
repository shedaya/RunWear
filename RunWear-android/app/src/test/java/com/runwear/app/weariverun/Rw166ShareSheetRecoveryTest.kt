package com.runwear.app.weariverun

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * RW-166 (Nov 10, 2026): the Share Studio's Postcard for an
 * entry that settled with NO hero reference (RW-160's Android
 * form — the New York Nov 9 shape from Sol's device pass).
 *
 * Pre-fix, the sheet's hero gate short-circuits an empty-ref
 * entry to NoPhoto before any resolution: "Your Postcard
 * couldn't be made" while the Kit Card renders. Post-fix, with
 * the recovery seam present, the entry's own banked hero is
 * re-linked, reported for the heal, and loaded — the poster
 * renders and offers the full photo, exactly as
 * Rw158ExpiredRefSheetTest proved for the re-sign path (this
 * class reuses its harness and its route: collection chapter →
 * day row → sheet).
 *
 * Own class, harness discipline per Rw158Harness (PAUSED
 * looper, one state-write scenario per class). An earlier draft
 * of this test drove the compose-ui-test junit4 rule and
 * harness helpers that do not exist; this is the repo's real
 * pattern.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(
    org.robolectric.annotation.LooperMode.Mode.PAUSED
)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw166ShareSheetRecoveryTest : Rw158Harness() {

    @Test
    fun `empty ref entry postcard renders from its recovered bank hero`() {
        val photo = photoFile(0xFF8A9B7C.toInt(), "rw166hero")
        val kitKeys = listOf(
            "LONG_SLEEVE_LIGHT",
            "SHORTS",
            "LIGHT_BEANIE",
            "SUNGLASSES"
        )
        val ny = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 9),
            place = "New York, NY",
            feelsLikeF = 58.0,
            conditionLabel = "Clear sky",
            isSnowing = false,
            isRaining = false,
            items = kitKeys,
            itemKeys = kitKeys,
            heroRef = null,
            heroDurableRef = null,
            momentLabel = "9 a.m.",
            settledAtMillis = 1_762_696_800_000L,
            sessionId = "rw166-share",
            // The harness's own entries carry the stored anchor;
            // without one the poster render detours through ML
            // Kit face detection, which never completes under
            // Robolectric (Rw158ExpiredRefSheetTest's shape).
            faceAnchor = ShareFaceAnchor.DEFAULT_UPPER_MIDDLE
        )
        val bankId = HeroRecovery.candidateCombinationIds(
            ny, "MALE", ZoneId.systemDefault()
        ).first()
        val bankPath = "user-1/heroes/$bankId/v1.png"
        val heals = mutableListOf<String>()

        val stage = stage {
            WearIveRunCollectionScreen(
                entries = listOf(ny),
                today = today,
                onDismiss = {},
                onDeleteEntry = {},
                onDeletePlace = {},
                heroResign = { path ->
                    if (path == bankPath) photo else null
                },
                heroRecovery = HeroRecoverySeam(
                    genderSegment = "MALE",
                    bankPaths = { mapOf(bankId to bankPath) }
                ),
                onHeroRecovered = { _, path -> heals += path }
            )
        }
        val collection = awaitCollectionDialog(stage)
        clickCardAtText(stage, dialogRoot(collection), "New York, NY")
        clickCardAtText(stage, dialogRoot(collection), "Mon, Nov 9")
        val sheet = awaitNewDialog(stage, collection)
            ?: throw AssertionError("share studio sheet not shown")

        assertTrue(
            "the poster preview renders from the recovered photo",
            awaitText(stage, { dialogRoot(sheet) }, "View full photo", rounds = 120)
        )
        val texts = stage.texts(dialogRoot(sheet))
        assertFalse(
            "the empty ref must NOT surface as a photo failure",
            texts.any { it.contains("photo couldn't load") }
        )
        assertFalse(
            "the empty ref must NOT fail the Postcard",
            texts.any { it.contains("couldn't be made") }
        )
        // The recovery reported the heal with the bank path (it
        // may be reported by more than one surface resolving the
        // same entry; the store heal itself happens once — that
        // half is pinned in Rw166UnitTest).
        assertTrue(
            "expected the heal to be reported with $bankPath, got $heals",
            heals.isNotEmpty() && heals.all { it == bankPath }
        )
        stage.controller.pause().stop().destroy()
    }
}
