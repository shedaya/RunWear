package com.runwear.app.makeitme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Counter unification (Sol's ruling, Nov 9, 2026 — the RW-128
 * one-story rule enforced in code): the user gets 10 free Make
 * It Me looks, and EVERY surface that reports the count — the
 * hero pill, the "This outfit isn't you — yet" card, Settings,
 * the paywall meter — reads the ONE counter
 * (MakeItMeUiState.freeGenerationsUsed) against the ONE
 * allowance (MakeItMeUpsellRules.FREE_ALLOWANCE). The card
 * previously hardcoded "You've seen yourself in 5 outfits" —
 * a stale residue of the old allowance of 5 — while the pill
 * beside it read the real counter out of 10.
 */
class MimCounterUnificationTest {

    private fun source(path: String): String {
        val candidates = listOf(
            "app/src/main/java/$path",
            "src/main/java/$path",
            "../app/src/main/java/$path"
        )
        return candidates.map { File(it) }.first { it.exists() }.readText()
    }

    @Test
    fun `the one allowance is 10`() {
        assertEquals(10, MakeItMeUpsellRules.FREE_ALLOWANCE)
        assertEquals(10, FreeLooksMeter.ALLOWANCE)
    }

    @Test
    fun `every reporting surface states the same used count of the same 10`() {
        for (used in listOf(0, 1, 3, 7, 10)) {
            assertEquals(
                "$used of 10 looks", MakeItMeStrings.heroPillLookCount(used)
            )
            assertTrue(
                "card body must carry the same counter ($used): " +
                    MakeItMeStrings.u1cBody(used),
                MakeItMeStrings.u1cBody(used)
                    .startsWith("You've seen yourself in $used outfits.")
            )
            assertEquals(
                "$used of 10 free looks used", MakeItMeStrings.s4FreeUsed(used)
            )
            assertTrue(
                MakeItMeStrings.u2MeterLine(used).startsWith("$used of 10 used")
            )
        }
        assertEquals(
            "10 of 10 used", MakeItMeStrings.HERO_PILL_EXHAUSTED_COUNT
        )
        assertEquals(
            "All 10 free looks used", MakeItMeStrings.s4FreeUsed(11)
        )
    }

    @Test
    fun `the card body is a function of the counter - the hardcoded 5 is gone`() {
        val strings = source("com/runwear/app/makeitme/MakeItMeStrings.kt")
        assertFalse(
            "no user-visible string may hardcode the old allowance story",
            strings.contains("in 5 outfits") || strings.contains("of 5 looks") ||
                strings.contains("of 5 free")
        )
        assertTrue(strings.contains("fun u1cBody(freeGenerationsUsed: Int)"))
        val screens = source("com/runwear/app/makeitme/MakeItMeScreens.kt")
        assertTrue(screens.contains("MakeItMeStrings.u1cBody(freeGenerationsUsed)"))
        assertFalse(screens.contains("U1C_BODY"))
    }

    @Test
    fun `the card is fed from the same state field the pill reads`() {
        val screen = source("com/runwear/app/ui/screens/HeroMainScreen.kt")
        val cardFeeds = Regex(
            "MakeItMeUpsellCard\\("
        ).findAll(screen).count()
        val counterFeeds = Regex(
            "freeGenerationsUsed = makeItMeState\\.freeGenerationsUsed"
        ).findAll(screen).count()
        assertEquals("every card call site must pass the counter", 3, cardFeeds)
        // One feed is the hero pill's own state assembly; the other
        // three are the card's call sites (one per layout).
        assertTrue(
            "pill + all card sites read the one field (found $counterFeeds)",
            counterFeeds >= 4
        )
    }
}
