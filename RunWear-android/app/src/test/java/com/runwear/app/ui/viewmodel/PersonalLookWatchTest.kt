package com.runwear.app.ui.viewmodel

import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.makeitme.PersonalLookStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-79 (owner device evidence + live backend trace, Nov 6, 2026):
 * a personal-look job failed terminally 82 seconds after the
 * request while Home kept claiming MAKING for minutes.
 *
 * Diagnosis (client-diagnosis.md): the MAKING pill and the failed
 * line are two renderings of MainUiState.personalLookStatus, fed
 * only by get-personal-hero look-state reads; the only periodic
 * reader was the backoff poll, hosted INSIDE the cancellable
 * heroResolveJob, and no lifecycle event — foregrounding, paywall
 * dismissal, leaving the first-look sheet — re-read the state or
 * re-armed a dead poll.
 *
 * The fix pinned here:
 *  1. The poll runs ONLY in its own viewModelScope job
 *     (ensurePersonalPoll → personalPollJob), so hero
 *     re-resolution can no longer cancel it.
 *  2. The poll and the lifecycle re-check share ONE apply path
 *     (applyPersonalLookRead).
 *  3. Lifecycle events re-read and re-arm: foreground (first thing
 *     in onForegrounded, ahead of its early returns), the Home
 *     paywall's dismissal, the first-look paywall's dismissal and
 *     purchase, and leaving the first-look sheet for Home.
 *  4. Whether a re-read is owed is PersonalLookRecheckPolicy's
 *     decision, unit-tested below.
 *  5. The terminal-failure surface is the owner's settled wording,
 *     verbatim, with its Try again.
 *
 * MainViewModel is a Hilt view model over network-backed
 * repositories and is not constructed in this suite (the repo's
 * established pattern); its wiring is pinned at the source, the
 * decision logic is pinned as behaviour.
 */
class PersonalLookWatchTest {

    private fun mainSource(path: String): String {
        val f = File("src/main/java/$path").takeIf { it.exists() }
            ?: File("app/src/main/java/$path")
        return f.readText()
    }

    private fun viewModelSource(): String =
        mainSource("com/runwear/app/ui/viewmodel/MainViewModel.kt")

    private fun occurrences(haystack: String, needle: String): Int =
        haystack.split(needle).size - 1

    // ── The re-check decision (behaviour) ────────────────────────

    @Test
    fun `a recheck is owed exactly while Home claims Making for an in-view cell`() {
        assertTrue(
            PersonalLookRecheckPolicy.isRecheckOwed(
                personalLayerEngages = true,
                hasCellInView = true,
                status = PersonalLookStatus.Making
            )
        )
    }

    @Test
    fun `no recheck is owed once the look has resolved - terminal states are already the truth`() {
        assertFalse(
            PersonalLookRecheckPolicy.isRecheckOwed(
                true, true, PersonalLookStatus.Ready
            )
        )
        assertFalse(
            PersonalLookRecheckPolicy.isRecheckOwed(
                true, true, PersonalLookStatus.Failed(PersonalLookStatus.Reason.ERROR)
            )
        )
    }

    @Test
    fun `a recheck IS owed for an unresolved look - RW-134 widening`() {
        // RW-134 / review §4.5 (Nov 7, 2026): None is NOT a
        // terminal state — it is the unresolved state the old
        // Making-only rule stranded (a delivered exact cell never
        // replaced the stock serve on return). The recheck set is
        // widened to Making-or-None; this assertion reverses the
        // pre-wave pin on None deliberately.
        assertTrue(
            PersonalLookRecheckPolicy.isRecheckOwed(
                true, true, PersonalLookStatus.None
            )
        )
    }

    @Test
    fun `no recheck is owed when the personal layer is off or no cell is in view`() {
        assertFalse(
            PersonalLookRecheckPolicy.isRecheckOwed(
                personalLayerEngages = false,
                hasCellInView = true,
                status = PersonalLookStatus.Making
            )
        )
        assertFalse(
            PersonalLookRecheckPolicy.isRecheckOwed(
                personalLayerEngages = true,
                hasCellInView = false,
                status = PersonalLookStatus.Making
            )
        )
    }

    // ── Hosting: the poll survives hero re-resolution ────────────

    @Test
    fun `the poll is launched only by ensurePersonalPoll, into its own job`() {
        val vm = viewModelSource()
        // Exactly ONE direct poll invocation exists in the whole
        // view model, and it is the dedicated job's launch.
        assertEquals(1, occurrences(vm, "pollForPersonalHero(cell)"))
        assertTrue(
            vm.contains(
                "personalPollJob = viewModelScope.launch { pollForPersonalHero(cell) }"
            )
        )
        // Every former in-resolution call site now ensures instead:
        // the personal-hit branch, the overlay's MAKING branch, the
        // overlay's accepted-request branch, the retry, and the two
        // re-check re-arms.
        assertEquals(6, occurrences(vm, "ensurePersonalPoll(cell)"))
    }

    @Test
    fun `hero re-resolution never touches the poll job`() {
        val vm = viewModelSource()
        val resolve = vm.substringAfter("private fun resolveHeroImage")
            .substringBefore("private suspend fun applyPersonalHeroOverlay")
        assertTrue(resolve.contains("heroResolveJob?.cancel()"))
        assertFalse(
            "a superseding resolution must not reach the poll (RW-79)",
            resolve.contains("personalPollJob")
        )
        assertFalse(resolve.contains("pollForPersonalHero"))
    }

    @Test
    fun `ensuring the poll dedupes the same cell and replaces a different one`() {
        val vm = viewModelSource()
        val ensure = vm.substringAfter("private fun ensurePersonalPoll")
            .substringBefore("private suspend fun applyPersonalLookRead")
        assertTrue(
            ensure.contains(
                "if (personalPollJob?.isActive == true && personalPollCell == cell) return"
            )
        )
        assertTrue(ensure.contains("personalPollJob?.cancel()"))
        assertTrue(ensure.contains("personalPollCell = cell"))
    }

    // ── One apply path for the poll and the re-check ─────────────

    @Test
    fun `the poll applies its reads through the shared apply path`() {
        val vm = viewModelSource()
        val poll = vm.substringAfter("private suspend fun pollForPersonalHero")
            .substringBefore("fun retryPersonalLook")
        assertTrue(poll.contains("applyPersonalLookRead(cell, look)"))
        // The poll keeps the settled schedule + horizon (the defect
        // was hosting/lifecycle, not the horizon).
        assertTrue(poll.contains("PersonalPollPolicy.shouldPollAgain"))
        assertTrue(poll.contains("PersonalPollPolicy.delayBeforeAttemptMs"))
    }

    @Test
    fun `the recheck reads once through the shared apply path and re-arms while still Making`() {
        val vm = viewModelSource()
        val recheck = vm.substringAfter("fun recheckPersonalLookState")
            .substringBefore("Poll get-personal-hero while")
        assertTrue(recheck.contains("PersonalLookRecheckPolicy.isRecheckOwed"))
        assertTrue(recheck.contains("personalHeroRepository.cellLookFor(cell)"))
        assertTrue(recheck.contains("applyPersonalLookRead(cell, look)"))
        // Still Making (apply returned null) → the watch is re-armed;
        // a failed read concludes nothing but also re-arms.
        assertTrue(
            recheck.contains(
                "if (applyPersonalLookRead(cell, look) == null) ensurePersonalPoll(cell)"
            )
        )
        assertEquals(2, occurrences(recheck, "ensurePersonalPoll(cell)"))
    }

    // ── Lifecycle wiring ─────────────────────────────────────────

    @Test
    fun `foreground re-checks before every early return in onForegrounded`() {
        val vm = viewModelSource()
        // The region from the function's head to its FIRST early
        // return (the manual-location return): the re-check call
        // must already have happened by then, so no location mode
        // or debounce can swallow it.
        val foreground = vm.substringAfter("fun onForegrounded")
            .substringBefore("locationSource == \"manual\"")
        assertTrue(foreground.contains("recheckPersonalLookState()"))
    }

    @Test
    fun `paywall dismissal and leaving the first-look sheet re-check from MainActivity`() {
        val activity = mainSource("com/runwear/app/MainActivity.kt")
        // First-look sheet exit + first-look paywall dismissal +
        // first-look paywall purchase.
        assertEquals(3, occurrences(activity, "viewModel.recheckPersonalLookState()"))
        // The Home paywall's dismissal rides the screen callback.
        assertTrue(activity.contains("viewModel::recheckPersonalLookState"))
    }

    @Test
    fun `the Home paywall dismissal invokes the recheck callback`() {
        val hero = mainSource("com/runwear/app/ui/screens/HeroMainScreen.kt")
        assertTrue(hero.contains("onPersonalLookRecheck: () -> Unit = {}"))
        val paywall = hero.substringAfter("if (showMakeItMePaywall")
            .substringBefore("Location Picker")
        assertTrue(paywall.contains("onPersonalLookRecheck()"))
    }

    // ── The terminal-failure surface, verbatim ───────────────────

    @Test
    fun `the failed line is the owner's settled wording, unchanged`() {
        assertEquals(
            "Your look couldn't be made from these photos — tap to try again.",
            MakeItMeStrings.G5_LOOK_FAILED
        )
        assertEquals("Try again", MakeItMeStrings.G6_RETRY)
        // And the failed status renders it in the Home hero region.
        val screens = mainSource("com/runwear/app/makeitme/MakeItMeScreens.kt")
        assertTrue(screens.contains("text = stringResource(MR.strings.mim_g5_look_failed)"))
    }
}
