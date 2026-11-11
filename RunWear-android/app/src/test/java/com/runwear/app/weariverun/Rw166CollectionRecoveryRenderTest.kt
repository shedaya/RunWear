package com.runwear.app.weariverun

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * RW-166 (Nov 10, 2026): the collection-cover recovery, rendered.
 *
 * A New York chapter whose NEWEST entry settled with NO hero
 * reference (the defect shape) and whose OLDER entry also settled
 * empty — but the older entry's outfit cell IS banked. Pre-fix,
 * every thumbnail in the chapter renders the honest placeholder
 * (zero "Entry photo" nodes) and nothing ever heals. Post-fix,
 * the older entry's day card recovers its banked hero through
 * the seam, reports the heal, and its slot renders as a photo,
 * while the newest entry — its cell unbanked — stays honestly
 * empty (the named unrecoverable class, never fabricated).
 *
 * The cover's own re-pick onto the healed entry is a store
 * round-trip — the model half is pinned in Rw166UnitTest; on
 * device the healed entry re-enters through the entries flow
 * and the cover recomputes.
 *
 * Own class, harness discipline per Rw158Harness (PAUSED
 * looper, one state-write scenario per class). An earlier draft
 * of this test drove the compose-ui-test junit4 rule, which is
 * not on this repo's unit-test classpath — the harness's
 * ComposeView + semantics-walk pattern is the repo's way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(
    org.robolectric.annotation.LooperMode.Mode.PAUSED
)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw166CollectionRecoveryRenderTest : Rw158Harness() {

    private val kitKeys = listOf(
        "LONG_SLEEVE_LIGHT",
        "SHORTS",
        "LIGHT_BEANIE",
        "SUNGLASSES"
    )

    private fun emptyRefEntry(
        date: LocalDate,
        moment: String,
        feelsLikeF: Double = 58.0
    ) =
        WearIveRunDayEntry(
            date = date,
            place = "New York, NY",
            feelsLikeF = feelsLikeF,
            conditionLabel = "Clear sky",
            isSnowing = false,
            isRaining = false,
            items = kitKeys,
            itemKeys = kitKeys,
            heroRef = null,
            heroDurableRef = null,
            momentLabel = moment,
            settledAtMillis = 1_762_696_800_000L,
            sessionId = "rw166-$date"
        )

    private fun photoSlots(stage: Stage, root: androidx.compose.ui.semantics.SemanticsNode): Int =
        stage.nodes(root).count {
            it.config.contains(SemanticsProperties.ContentDescription) &&
                it.config[SemanticsProperties.ContentDescription]
                    .any { d -> d == "Entry photo" }
        }

    private fun noPhotoSlots(stage: Stage, root: androidx.compose.ui.semantics.SemanticsNode): Int =
        stage.nodes(root).count {
            it.config.contains(SemanticsProperties.ContentDescription) &&
                it.config[SemanticsProperties.ContentDescription]
                    .any { d -> d == "No photo for this entry" }
        }

    @Test
    fun `chapter day card recovers its banked hero and reports the heal`() {
        // The newest entry sits in a different temp bracket
        // (72°F → WARM): recovery never crosses brackets, so its
        // cell is genuinely unbanked even though the outfit,
        // weather, and time band match the older entry's. (With
        // identical carried identity the two entries would map
        // to the SAME bank cell and both would — correctly —
        // recover it; run 328's count of 2 proved exactly that.)
        val newest = emptyRefEntry(
            LocalDate.of(2026, 11, 9), "9 a.m.", feelsLikeF = 72.0
        )
        val older = emptyRefEntry(LocalDate.of(2026, 11, 7), "7 a.m.")
        // The bank holds the OLDER entry's exact cell only.
        val bankId = HeroRecovery.candidateCombinationIds(
            older, "MALE", ZoneId.systemDefault()
        ).first()
        val bankPath = "user-1/heroes/$bankId/v1.png"
        val bank = mapOf(bankId to bankPath)
        val heals = mutableListOf<Pair<LocalDate, String>>()

        val stage = stage {
            WearIveRunCollectionScreen(
                entries = listOf(newest, older),
                today = today,
                onDismiss = {},
                heroResign = { path -> "file:///rw166/$path" },
                heroRecovery = HeroRecoverySeam(
                    genderSegment = "MALE",
                    bankPaths = { bank }
                ),
                onHeroRecovered = { e, path -> heals += e.date to path }
            )
        }
        val collection = awaitCollectionDialog(stage)
        clickCardAtText(stage, dialogRoot(collection), "New York, NY")

        // The recovery is asynchronous (bank read → re-sign):
        // poll the chapter's semantics until the older entry's
        // slot renders as a photo.
        var photos = 0
        repeat(60) {
            if (photos != 1) {
                stage.settle(5)
                photos = runCatching {
                    photoSlots(stage, dialogRoot(collection))
                }.getOrDefault(0)
            }
        }
        assertEquals(
            "exactly one thumbnail recovers (the banked older entry)",
            1, photos
        )
        assertTrue(
            "the newest entry's slot stays honestly empty (unbanked cell)",
            noPhotoSlots(stage, dialogRoot(collection)) >= 1
        )
        // The heal was reported for the older entry, with the
        // bank path — the persistence that makes it happen once.
        assertTrue(
            "expected a heal for ${older.date} with $bankPath, got $heals",
            heals.any { it.first == older.date && it.second == bankPath }
        )
        assertEquals(
            "the newest entry must not heal (its cell is unbanked)",
            0,
            heals.count { it.first == newest.date }
        )
        stage.controller.pause().stop().destroy()
    }
}
