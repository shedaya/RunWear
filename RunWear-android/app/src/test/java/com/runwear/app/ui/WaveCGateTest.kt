package com.runwear.app.ui

import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.ui.components.DayChartBandPolicy
import com.runwear.app.ui.components.DayChartModel
import com.runwear.app.ui.components.OutfitHeaderCopy
import com.runwear.app.ui.components.HomeLoadingCopy
import com.runwear.app.ui.screens.WeatherSheetCopy
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * GEN-6 WAVE C string pins + policy pins, in the gen-5 style:
 * settled copy asserted verbatim, and source pins where the
 * settled surface is a composable's literal.
 */
class WaveCGateTest {

    private fun mainSource(path: String): String {
        val f = File("src/main/java/$path").takeIf { it.exists() }
            ?: File("app/src/main/java/$path")
        return f.readText()
    }

    // ── RW-52 → RW-78: fork doors navigate directly ─────────────
    // The RW-52 select-then-commit pin that stood here ("fork
    // carries the settled Continue door": doors select into
    // forkChoice; a separate Continue proceeds) is SUPERSEDED by
    // RW-78's owner device evidence (Nov 6, 2026): on gen-6 the
    // tapped "Use my photos" button only marked its card selected
    // while Continue sat below the fold — the primary call to
    // action read as a button and behaved as a radio selection.
    // Re-pinned, not deleted: the doors now navigate directly (the
    // gen-5 form), and no selection state or Continue may return
    // to the fork screen. The tap-level behaviour is pinned by
    // Rw78ForkNavigationTest.

    @Test
    fun `fork door B title is the settled Standard photos`() {
        assertEquals("Standard photos", MakeItMeStrings.F6_OPTION_B_TITLE)
        assertEquals("Use standard photos", MakeItMeStrings.F8_OPTION_B_BUTTON)
        assertEquals("Make It Me", MakeItMeStrings.F3_OPTION_A_TITLE)
        assertEquals("Use my photos", MakeItMeStrings.F5_OPTION_A_BUTTON)
    }

    @Test
    fun `fork doors navigate directly - no selection state, no Continue`() {
        val source = mainSource("com/runwear/app/makeitme/MakeItMeScreens.kt")
        val fork = source.substringAfter("fun MakeItMeForkScreen")
            .substringBefore("private fun ForkOptionCard")
        // Each door's button carries its navigation handler itself.
        assertTrue(fork.contains("onClick = onUseMyPhotos"))
        assertTrue(fork.contains("onClick = onUseStandardPhotos"))
        // The superseded select-then-commit machinery is gone (the
        // word Continue survives only inside the RW-78 comment
        // recording the supersession — the control does not).
        assertFalse(fork.contains("forkChoice"))
        assertFalse(fork.contains("Text(\"Continue\""))
        // …and the supersession is recorded where the next reader
        // of the RW-52 reading will find it.
        assertTrue(fork.contains("RW-78"))
        assertTrue(fork.contains("SUPERSEDES"))
    }

    // ── RW-54: outfit header ─────────────────────────────────────

    @Test
    fun `outfit header is the settled sentence case`() {
        assertEquals("6 items for your run", OutfitHeaderCopy.header(6))
        val hero = mainSource("com/runwear/app/ui/screens/HeroMainScreen.kt")
        assertFalse(hero.contains("\"\$itemCount Items for Your Run\""))
        assertTrue(hero.contains("OutfitHeaderCopy.header(itemCount)"))
    }

    // ── RW-60: settled loading line ──────────────────────────────

    @Test
    fun `home loading line is the settled frame-46 line`() {
        assertEquals(
            "Getting your first recommendation…",
            HomeLoadingCopy.FIRST_RECOMMENDATION
        )
        val shimmer = mainSource("com/runwear/app/ui/components/ShimmerLoading.kt")
        assertTrue(shimmer.contains("HomeLoadingCopy.FIRST_RECOMMENDATION"))
    }

    // ── DA-24: branded loading is the home skeleton, not a logo ──

    @Test
    fun `branded loading renders the home-shaped skeleton`() {
        val shimmer = mainSource("com/runwear/app/ui/components/ShimmerLoading.kt")
        val branded = shimmer.substringAfter("fun BrandedLoadingScreen")
        assertTrue(branded.contains("HomeSkeletonScreen("))
    }

    @Test
    fun `location search shows a searching state tied to the query`() {
        val sheets = mainSource("com/runwear/app/ui/screens/BottomSheets.kt")
        assertTrue(sheets.contains("Searching for "))
        // RW-55: results render below Current location / recents —
        // the results block's source position follows the recents'.
        val resultsIdx = sheets.indexOf("RW-55 + DA-24")
        val recentsIdx = sheets.indexOf("text = \"RECENT LOCATIONS\"")
        assertTrue("results block must follow the recents section", resultsIdx > recentsIdx)
    }

    // ── RW-55: settled first-run hint ────────────────────────────

    @Test
    fun `location hint carries the no-save assurance`() {
        val sheets = mainSource("com/runwear/app/ui/screens/BottomSheets.kt")
        assertTrue(
            sheets.contains(
                "Nothing is saved until you choose a place."
            )
        )
        assertTrue(
            sheets.contains(
                "Your recent places will wait here — Pick a place once and it joins a short list — home, the office, trip cities — so jumping back takes one tap."
            )
        )
    }

    // ── RW-56: Rate RunWear in Settings; Appearance period ──────

    @Test
    fun `settings carries Rate RunWear and the Appearance line's final period`() {
        val sheets = mainSource("com/runwear/app/ui/screens/BottomSheets.kt")
        assertTrue(sheets.contains("SettingsSectionId.RATE_RUNWEAR"))
        assertTrue(sheets.contains("\"Rate RunWear\""))
        assertTrue(sheets.contains("Match your device, or always light or dark."))
    }

    // ── DA-19: Settings body temperature is the slider form ─────

    @Test
    fun `settings body temperature uses the slider control, not the segmented selector`() {
        val sheets = mainSource("com/runwear/app/ui/screens/BottomSheets.kt")
        val settingsBody = sheets.substringAfter("SettingsSectionId.BODY_TEMPERATURE")
            .substringBefore("SettingsSectionId.MAKE_IT_ME")
        assertTrue(settingsBody.contains("ComfortSliderControl("))
        assertFalse(settingsBody.contains("ComfortSelector("))
    }

    // ── DA-18: guided capture library door opens the picker ─────

    @Test
    fun `guided capture library door is wired to the picker, not only dismiss`() {
        val capture = mainSource("com/runwear/app/makeitme/GuidedSelfieCapture.kt")
        assertTrue(capture.contains("onChooseFromLibrary"))
        assertTrue(capture.contains("TextButton(onClick = onChooseFromLibrary)"))
        val screens = mainSource("com/runwear/app/makeitme/MakeItMeScreens.kt")
        assertTrue(screens.contains("onChooseFromLibrary = {"))
        assertTrue(screens.contains("launchPicker()"))
    }

    // ── RW-64: shop door + disclosure clause ────────────────────

    @Test
    fun `shop sheet door is Shop and disclosure carries the outfit clause`() {
        val sheets = mainSource("com/runwear/app/ui/screens/BottomSheets.kt")
        assertTrue(sheets.contains("text = \"Shop ›\""))
        assertFalse(sheets.contains("text = \"Shop on Amazon\""))
        assertTrue(
            sheets.contains("It never changes the outfit RunWear recommends.")
        )
    }

    // ── RW-63: one unified weather sheet ────────────────────────

    @Test
    fun `weather sheet is unified - no per-metric dispatch remains`() {
        val sheets = mainSource("com/runwear/app/ui/screens/BottomSheets.kt")
        assertTrue(sheets.contains("UnifiedWeatherDetail"))
        assertFalse(sheets.contains("\"condition\" -> WeatherConditionDetail"))
        assertFalse(sheets.contains("fun WindDetail"))
        assertFalse(sheets.contains("fun UVDetail"))
    }

    @Test
    fun `weather sheet copy - context line and running tip`() {
        assertEquals(
            "Kansas City · Now",
            WeatherSheetCopy.contextLine("Kansas City", "Now")
        )
        val overcastCool = WeatherConditions(
            dateTime = LocalDateTime(2026, 11, 5, 7, 0),
            temperature = 48.0, feelsLike = 45.0, humidity = 60,
            windSpeed = 6.0, windGusts = 10.0, precipitationProbability = 5,
            precipitation = 0.0, isRaining = false, isSnowing = false,
            cloudCover = 90, uvIndex = 1.0, weatherCode = WeatherCode.OVERCAST
        )
        assertEquals(
            "Overcast and cool — a light layer you can shed beats one you can't.",
            WeatherSheetCopy.fallbackTip(overcastCool)
        )
    }

    // ── RW-53(ii): sunrise-sunset night shading ─────────────────

    @Test
    fun `night shading follows sunrise and sunset when the model carries them`() {
        val model = DayChartModel(
            date = LocalDate.of(2026, 11, 5),
            hours = emptyList(), low = null, high = null,
            changeHours = emptyList(), horizonLastHour = null,
            sunriseMinutes = 6 * 60 + 12, sunsetMinutes = 19 * 60 + 48
        )
        assertEquals(
            listOf(0 to 372, 1188 to 1440),
            DayChartBandPolicy.nightRanges(model)
        )
        assertTrue(DayChartBandPolicy.isNightMinute(model, 5 * 60))
        assertFalse(DayChartBandPolicy.isNightMinute(model, 7 * 60))
        assertFalse(DayChartBandPolicy.isNightMinute(model, 19 * 60))
        assertTrue(DayChartBandPolicy.isNightMinute(model, 20 * 60))
    }

    @Test
    fun `night shading falls back to the fixed window without sun times`() {
        val model = DayChartModel(
            date = LocalDate.of(2026, 11, 5),
            hours = emptyList(), low = null, high = null,
            changeHours = emptyList(), horizonLastHour = null
        )
        assertEquals(
            listOf(0 to 360, 1200 to 1440),
            DayChartBandPolicy.nightRanges(model)
        )
    }

    // ── RW-53(i): settled picker instruction line ────────────────

    @Test
    fun `picker instruction line is the settled snap line`() {
        val picker = mainSource("com/runwear/app/ui/screens/DayChartTimePicker.kt")
        assertTrue(
            picker.contains("Every hour can be chosen — the handle snaps hour to hour.")
        )
        assertFalse(picker.contains("Drag to pick an hour."))
    }

    // ── RW-59: feedback helper ───────────────────────────────────

    @Test
    fun `feedback reply helper is the settled line`() {
        val feedback = mainSource("com/runwear/app/ui/screens/FeedbackScreen.kt")
        assertTrue(feedback.contains("\"Only used to reply to your feedback\""))
        assertFalse(feedback.contains("goes in anonymously"))
    }

    // ── RW-65: upsell card title is the script instance ──────────

    @Test
    fun `settings upsell card leads with the script title, not the sans eyebrow`() {
        val screens = mainSource("com/runwear/app/makeitme/MakeItMeScreens.kt")
        val card = screens.substringAfter("fun MakeItMeUpsellCard")
            .substringBefore("fun MakeItMePaywallSheet")
        assertTrue(card.contains("GreatVibesFontFamily"))
        assertTrue(card.contains("MakeItMeStrings.U2A_SHEET_TITLE"))
    }

    // ── RW-57: selfie thumbnail in the Your photos row ───────────

    @Test
    fun `settings your-photos row carries the selfie thumbnail`() {
        val screens = mainSource("com/runwear/app/makeitme/MakeItMeScreens.kt")
        assertTrue(screens.contains("thumbnailUrl = referenceThumbnailUrl"))
        assertTrue(screens.contains("loadReferenceUrls"))
    }
}
