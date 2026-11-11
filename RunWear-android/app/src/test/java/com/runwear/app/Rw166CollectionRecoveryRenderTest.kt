package com.runwear.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.runwear.app.weariverun.HeroRecovery
import com.runwear.app.weariverun.HeroRecoverySeam
import com.runwear.app.weariverun.WearIveRunCollectionScreen
import com.runwear.app.weariverun.WearIveRunDayEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * RW-166 (Nov 10, 2026): the collection-cover recovery, rendered.
 *
 * The chapter's newest entry settled with NO hero reference (the
 * defect shape); an OLDER entry in the same chapter also settled
 * empty, but ITS outfit cell is banked. Pre-fix, the cover rule
 * (newest WITH a loadable reference, else the newest) lands on
 * the newest empty entry and every thumbnail in the chapter
 * renders the honest placeholder — zero "Entry photo" nodes.
 * Post-fix, the older entry's card recovers its banked hero
 * through the seam, reports the heal, and its slot renders as a
 * photo. (The cover's own re-pick onto the healed entry is a
 * store round-trip — the model half is pinned in Rw166UnitTest;
 * on device the healed entry re-enters through the entries flow
 * and the cover recomputes.)
 *
 * Graphics + looper discipline follows Rw152CollectionRenderTest:
 * LEGACY looper (the PAUSED discipline silently drops this
 * screen's recompositions) and ONE state write per test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.LEGACY)
@Config(qualifiers = "w360dp-h640dp")
class Rw166CollectionRecoveryRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val kitKeys = listOf(
        "Light Long Sleeve",
        "Running Shorts (5-7\")",
        "Thin Crew Socks",
        "Pegasus 41"
    )

    private fun entry(date: LocalDate, moment: String) = WearIveRunDayEntry(
        date = date,
        place = "New York, NY",
        feelsLikeF = 58.0,
        conditionLabel = "Clear sky",
        items = kitKeys,
        itemKeys = kitKeys,
        heroRef = null,
        heroDurableRef = null,
        momentLabel = moment,
        settledAtMillis = 1_762_700_400_000L,
        sessionId = "rw166-$date"
    )

    @Test
    fun chapterCardRecoversItsBankedHeroAndReportsTheHeal() {
        val today = LocalDate.of(2026, 11, 10)
        val newest = entry(LocalDate.of(2026, 11, 9), "9 a.m.")
        val older = entry(LocalDate.of(2026, 11, 7), "7 a.m.")
        // The bank holds the OLDER entry's exact cell only.
        val bankId = HeroRecovery.candidateCombinationIds(
            older, "MALE", ZoneId.systemDefault()
        ).first()
        val bankPath = "user-1/heroes/$bankId/v1.png"
        val bank = mapOf(bankId to bankPath)
        val heals = mutableListOf<Pair<LocalDate, String>>()
        var shown by mutableStateOf<List<WearIveRunDayEntry>>(emptyList())

        composeRule.setContent {
            MaterialTheme {
                WearIveRunCollectionScreen(
                    entries = shown,
                    today = today,
                    onDismiss = {},
                    heroResign = { path -> "file:///rw166/$path" },
                    heroRecovery = HeroRecoverySeam(
                        genderSegment = "MALE",
                        bankPaths = { bank }
                    ),
                    onHeroRecovered = { e, path ->
                        heals += e.date to path
                    }
                )
            }
        }
        composeRule.waitForIdle()
        // The ONE state write (the Rw152CollectionRenderTest
        // discipline): the entries land.
        shown = listOf(newest, older)
        composeRule.waitForIdle()

        // The older entry's thumbnail recovered: exactly one
        // "Entry photo" slot in the chapter (the cover slot shows
        // the newest, still-empty entry — its own cell is not
        // banked, so it stays honestly empty).
        composeRule.onAllNodesWithContentDescription("Entry photo")
            .assertCountEquals(1)
        // The heal was reported for the older entry, with the
        // bank path — the persistence that makes it happen once.
        assertTrue(
            "expected a heal for ${older.date}, got $heals",
            heals.any { it.first == older.date && it.second == bankPath }
        )
        assertEquals(
            "the newest entry must not heal (its cell is unbanked)",
            0,
            heals.count { it.first == newest.date }
        )
    }
}
