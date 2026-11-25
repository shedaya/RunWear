package com.runwear.app

import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.ui.components.stripTipEmoji
import com.runwear.app.weariverun.WearIveRunEntry
import com.runwear.app.weariverun.WearIveRunSubtitle
import com.runwear.app.weariverun.posterMomentLine
import com.runwear.app.weariverun.WearIveRunDayEntry
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import com.runwear.app.ui.screens.FitPreferenceOptions
import com.runwear.app.ui.screens.fitPreferenceAfterTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-5 (RW-29 further audit) — the fix round's pins, one per
 * register item where a pure seam exists; render-level items are
 * pinned in their own suites (ShareArtifactRenderTest for
 * FA-17/FA-21, PosterThumbnailGeometryTest for FA-28,
 * TimePickerDraftTest for FA-39, Gen3SettingsTest for FA-10).
 */
class Gen5FurtherAuditTest {

    private fun source(path: String): String = listOf(
        "src/main/java/$path",
        "app/src/main/java/$path",
        "../RunWear-android/app/src/main/java/$path",
        // LOC-1 WP-R2: resource files live under res/, not java/.
        "src/main/$path",
        "app/src/main/$path"
    ).map { File(it) }.first { it.exists() }.readText()

    // ── FA-6: the first-look count line composes from the meter ──

    @Test
    fun `FA-6 - first look count line composes from the shared meter`() {
        assertEquals(
            MakeItMeStrings.FIRST_LOOK_COUNT_LINE,
            MakeItMeStrings.firstLookCountLine(1)
        )
        // RW-128 (Nov 7, 2026): free allowance 5 → 10.
        assertEquals(
            "That was look 3 of your 10 free looks — 7 remain, whenever you want them.",
            MakeItMeStrings.firstLookCountLine(3)
        )
        assertEquals(
            "That was look 10 of your 10 free looks — 0 remain, whenever you want them.",
            MakeItMeStrings.firstLookCountLine(10)
        )
        // A stale or out-of-range meter read can never produce a
        // nonsense count on the moment.
        assertEquals(
            MakeItMeStrings.firstLookCountLine(10),
            MakeItMeStrings.firstLookCountLine(15)
        )
    }

    // ── COR-8: the Pro Tip emoji strip ────────────────────────────

    @Test
    fun `COR-8 - tip emoji are stripped at the Android render seam`() {
        assertEquals(
            "High heat risk — consider running early morning or evening.",
            stripTipEmoji("🌡️ High heat risk — consider running early morning or evening.")
        )
        assertEquals(
            "UV is high — reapply sunscreen.",
            stripTipEmoji("☀️ UV is high — reapply sunscreen.")
        )
        assertEquals("Plain tip, untouched.", stripTipEmoji("Plain tip, untouched."))
    }

    // ── FA-34: the expanded subtitle carries the outfit line ─────

    @Test
    fun `FA-34 - expanded subtitle names the kit, not only its size`() {
        val entry = WearIveRunEntry(
            place = "Kansas City, MO",
            timeLabel = "Now",
            feelsLikeF = 36.0,
            itemCount = 3,
            itemNames = listOf("Light Long Sleeve", "Light Tights", "Running Hat")
        )
        assertEquals(
            "36° feels like · 3 items · Light Long Sleeve · Light Tights · Running Hat",
            WearIveRunSubtitle.detail(entry)
        )
    }

    // ── FA-21: the poster's human moment line ─────────────────────

    @Test
    fun `FA-21 - poster moment line prefers the recorded moment reading`() {
        val withMoment = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 5),
            place = "Miami, FL",
            feelsLikeF = 78.0,
            conditionLabel = "Clear sky",
            isSnowing = false,
            isRaining = false,
            items = emptyList(),
            heroRef = null,
            momentLabel = "9 p.m."
        )
        assertEquals("Thursday · 9 p.m.", posterMomentLine(withMoment))
        val bare = withMoment.copy(momentLabel = null, settledAtMillis = 0L)
        assertEquals("Thursday", posterMomentLine(bare))
    }

    // ── FA-7/FA-8/FA-9: the paywall's settled structure ──────────

    @Test
    fun `FA-7 FA-8 FA-9 - paywall restores, withholds CTAs when subscribed, and buys the selected plan`() {
        val screens = source("com/runwear/app/makeitme/MakeItMeScreens.kt")
        assertTrue(screens.contains("Restore purchases"))
        assertTrue(screens.contains("viewModel.restorePurchases"))
        // GEN-6 (DA-11): the subscribed state renders the SETTLED
        // string constant, gated on real bank coverage — the gen-5
        // hard-coded literal this pin used to require is the defect
        // and must NOT come back.
        assertTrue(screens.contains("MR.strings.mim_s5_state_c_sub"))
        assertFalse(
            screens.contains(
                "You're subscribed — your photo is ready before you open the app."
            )
        )
        // The one CTA buys the SELECTED plan; neither plan surface
        // purchases on its own tap any more.
        assertTrue(screens.contains("buy(if (annualSelected) annual else monthly)"))
        assertFalse(screens.contains("onClick = { buy(monthly) }"))
        assertFalse(screens.contains("onClick = { buy(annual) }"))
        val billing = source("com/runwear/app/makeitme/BillingManager.kt")
        assertTrue(billing.contains("awaitRestore()"))
    }

    // ── FA-15: authored error bodies, bounded forecast reads ─────

    @Test
    fun `FA-15 - the home error screen never renders the raw exception`() {
        val hero = source("com/runwear/app/ui/screens/HeroMainScreen.kt")
        val errorScreen = hero.substringAfter("private fun HeroErrorScreen")
        assertFalse(errorScreen.contains("text = error,"))
        assertTrue(errorScreen.contains("The weather service didn't answer."))
        assertTrue(
            errorScreen.contains(
                "Your places, settings, and photos stay on this phone — nothing is lost."
            )
        )
        val wearVm = listOf(
            "../wear/src/main/java/com/runwear/wear/presentation/viewmodel/WearViewModel.kt",
            "wear/src/main/java/com/runwear/wear/presentation/viewmodel/WearViewModel.kt",
            "../RunWear-android/wear/src/main/java/com/runwear/wear/presentation/viewmodel/WearViewModel.kt"
        ).map { File(it) }.first { it.exists() }.readText()
        assertFalse(wearVm.contains("e.message"))
    }

    // ── FA-14/FA-16/FA-26/FA-33/FA-35/FA-50: source-level pins ───

    @Test
    fun `FA-14 - both persisted decoders tolerate a malformed record`() {
        val store = source("com/runwear/app/weariverun/WearIveRunStore.kt")
        assertTrue(store.contains("mapNotNull"))
        val recents = source("com/runwear/app/ui/screens/RecentLocations.kt")
        assertTrue(recents.contains("mapNotNull"))
    }

    @Test
    fun `FA-16 - the empty collection speaks the settled frame`() {
        val screen = source("com/runwear/app/weariverun/WearIveRunCollectionScreen.kt")
        assertFalse(screen.contains("Nothing here yet"))
        // WP-R2 re-pin: the empty-state copy is the
        // collection_empty_title / collection_empty_body resources;
        // the screen references them and the resources carry the
        // settled sentences verbatim.
        assertTrue(screen.contains("R.string.collection_empty_title"))
        assertTrue(screen.contains("R.string.collection_empty_body"))
        val res = source("res/values/strings.xml")
        assertTrue(res.contains("Your scrapbook starts with today"))
        assertTrue(res.contains("aren\\'t backfilled"))
    }

    @Test
    fun `FA-26 - the share tap is guarded and the render has a timeout`() {
        // GEN-6 Wave B reconciliation: the share flow moved from
        // the collection screen into the Share Studio
        // (ShareStudioComponents.kt) — the FA-26 guarantees move
        // with it: a bounded render (20 s timeout onto the honest
        // failure surface) and a guarded share tap whose failure
        // surfaces instead of escaping.
        val studio = source("com/runwear/app/weariverun/ShareStudioComponents.kt")
        assertTrue(studio.contains("withTimeoutOrNull(20_000)"))
        assertTrue(studio.contains("RenderTimedOut"))
        assertTrue(studio.contains("shareFailed"))
        // WP-R2 re-pin: the failure label is the
        // share_couldnt_share resource at the site.
        assertTrue(studio.contains("R.string.share_couldnt_share"))
    }

    @Test
    fun `FA-33 - the expanded mini bar body tap settles back to mini`() {
        val bar = source("com/runwear/app/weariverun/WearIveRunMiniBar.kt")
        assertTrue(
            bar.contains("WearIveRunElementKind.EXPANDED -> onCollapseClick()")
        )
    }

    @Test
    fun `FA-35 - the pinned TODAY card answers to the weather filter`() {
        val screen = source("com/runwear/app/weariverun/WearIveRunCollectionScreen.kt")
        assertTrue(screen.contains("pinnedTodayEntry"))
        assertTrue(
            screen.contains("WearIveRunCollectionLogic.matches(it, filter)")
        )
    }

    @Test
    fun `FA-50 - the subscribed Settings state is plain, not an offer card`() {
        val screens = source("com/runwear/app/makeitme/MakeItMeScreens.kt")
        val section = screens.substringAfter("val card = MakeItMeStrings.premiumOfferCard(")
        val subscribedBranch = section.substringAfter("if (subscribed) {")
            .substringBefore("} else {")
        // §13's subscribed panel carries no offer card and no
        // pitch line at all — only the manage door stands here.
        assertFalse(subscribedBranch.contains("BorderStroke"))
        assertFalse(subscribedBranch.contains("card.benefitLine"))
        assertTrue(subscribedBranch.contains("MR.strings.mim_s9_manage_subscription"))
    }

    // ── Cream map: tokens and the worst offenders ─────────────────

    @Test
    fun `cream - hairline and paywall tint tokens exist and the settings lines use them`() {
        val theme = source("com/runwear/app/ui/theme/Theme.kt")
        assertTrue(theme.contains("val Hairline: Color"))
        assertTrue(theme.contains("val PaywallTint: Color"))
        assertTrue(theme.contains("Color(0xFFF2F8F5)"))
        assertTrue(theme.contains("surfaceContainer ="))
        val sheets = source("com/runwear/app/ui/screens/BottomSheets.kt")
        assertTrue(sheets.contains("HorizontalDivider(color = RunWearColors.Hairline"))
        val outfit = source("com/runwear/app/ui/components/OutfitCard.kt")
        assertTrue(outfit.contains("else Color.Transparent"))
    }

    @Test
    fun `cream - splash and widget grounds carry the design values`() {
        val colors = listOf(
            "src/main/res/values/colors.xml",
            "app/src/main/res/values/colors.xml"
        ).map { File(it) }.first { it.exists() }.readText()
        assertTrue(colors.contains("#FFF5F1E8"))
        // Addendum 9 (Candidate C icon): the launcher background
        // color resource is the cream ground itself; the pine
        // #FF175E4C moved out of this file into the icon's shirt
        // fill, so it is no longer asserted here.
        assertTrue(colors.contains("ic_launcher_background\">#FFF5F1E8"))
        assertFalse(colors.contains("0A0A0A"))
        val widgetBg = listOf(
            "src/main/res/drawable/widget_background.xml",
            "app/src/main/res/drawable/widget_background.xml"
        ).map { File(it) }.first { it.exists() }.readText()
        assertTrue(widgetBg.contains("#16130F"))
    }

    // ── Addendum 8: convergence + narration + fit ───────────────

    @Test
    fun `A8 defect 1 - the fill call names forecast priority cells`() {
        val service = source("com/runwear/app/makeitme/PersonalHeroService.kt")
        assertTrue(service.contains("fillRequestBody(priorityCells)"))
        assertTrue(service.contains("\"priority_cells\""))
        assertFalse(service.contains("callFunction(\"start-personal-fill\", \"{}\")"))
        val mainVm = source("com/runwear/app/ui/viewmodel/MainViewModel.kt")
        assertTrue(mainVm.contains("onForecastDerived(response, forecast, now)"))
        assertTrue(mainVm.contains("personalHeroRepository.startPersonalFill(cells)"))
        assertTrue(mainVm.contains("noteFillTarget(cells)"))
        // The grant path in the Make It Me view model no longer
        // fires a cell-less fill call.
        val mimVm = source("com/runwear/app/makeitme/MakeItMeViewModel.kt")
        assertFalse(mimVm.contains("repository.startPersonalFill()"))
    }

    @Test
    fun `A8 defect 2 - coverage is measured against the target set`() {
        val service = source("com/runwear/app/makeitme/PersonalHeroService.kt")
        assertTrue(service.contains("targetCells: Set<String>"))
        assertTrue(service.contains("heroCells.count { it in targetCells }"))
    }

    @Test
    fun `A8 item 3 - the serve engagement is guaranteed outside the hero job`() {
        val repo = source("com/runwear/app/makeitme/PersonalHeroRepository.kt")
        assertTrue(repo.contains("suspend fun ensureEngagement"))
        val mainVm = source("com/runwear/app/ui/viewmodel/MainViewModel.kt")
        assertTrue(mainVm.contains("personalHeroRepository.ensureEngagement(engagementCell)"))
    }

    @Test
    fun `A8 item 2 - a garment payload is unrepresentable in the narration model`() {
        val models = source("com/runwear/app/weariverun/WearIveRunModels.kt")
        assertTrue(models.contains("sealed interface WearIveRunNarrationPayload"))
        assertTrue(models.contains("fun update(payload: WearIveRunNarrationPayload)"))
        assertFalse(models.contains("fun update(payload: String)"))
        // The bar entry carries no payload field fed from the kit.
        assertFalse(models.contains("payload = entry.items.firstOrNull()"))
    }

    @Test
    fun `A8 item 3 - settings fit preference carries exactly two options (RW-81)`() {
        // RW-81 re-pin (Sol's ruling, Nov 6, 2026; supersedes the
        // three-option pin): exactly two labelled options, and
        // UNISEX is NOT among them — neutral is the unselected
        // state, reached by tapping the selected option again.
        assertEquals(
            listOf("Men's", "Women's"),
            FitPreferenceOptions.map { it.first }
        )
        assertEquals(
            listOf(
                com.runwear.shared.domain.model.GenderPreference.MALE,
                com.runwear.shared.domain.model.GenderPreference.FEMALE
            ),
            FitPreferenceOptions.map { it.second }
        )
        assertFalse(
            FitPreferenceOptions.any {
                it.second == com.runwear.shared.domain.model.GenderPreference.UNISEX
            }
        )
        // The tap rule: selected again → neutral; unselected → it.
        assertEquals(
            com.runwear.shared.domain.model.GenderPreference.UNISEX,
            fitPreferenceAfterTap(
                com.runwear.shared.domain.model.GenderPreference.MALE,
                com.runwear.shared.domain.model.GenderPreference.MALE
            )
        )
        assertEquals(
            com.runwear.shared.domain.model.GenderPreference.FEMALE,
            fitPreferenceAfterTap(
                com.runwear.shared.domain.model.GenderPreference.UNISEX,
                com.runwear.shared.domain.model.GenderPreference.FEMALE
            )
        )
        // The stored value round-trips: UNISEX is ordinal 2, the
        // repository's default when nothing is stored — the data
        // model is unchanged by RW-81, so a stored UNISEX still
        // loads, and now displays as nothing selected.
        assertEquals(
            com.runwear.shared.domain.model.GenderPreference.UNISEX,
            com.runwear.shared.domain.model.GenderPreference.entries[2]
        )
    }
}
