package com.runwear.app.ui.screens

import com.runwear.app.makeitme.UploadBackAction
import com.runwear.app.makeitme.UploadBackPolicy
import com.runwear.app.makeitme.UploadStep
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * WS-5 (RC-5: D-13 + D-14 + F-2) — source pins for the sheet
 * discipline and the back behavior the rendered tests cannot see
 * directly:
 *
 *  - EVERY ModalBottomSheet in the app rests on ONE anchor
 *    (skipPartiallyExpanded = true) — rest at full or hidden;
 *  - the Settings sheet's content has exactly ONE scroll consumer
 *    (its LazyColumn) — no nested verticalScroll, the D-13 ghosting
 *    mechanism;
 *  - every sheet dismisses through its own onDismissRequest (swipe
 *    or scrim — and system Back, which M3 routes there);
 *  - F-2: Your Photos is hosted in a full-screen Dialog (Back →
 *    onDismissRequest → home), the upload flow and the feedback
 *    overlay carry BackHandlers with the step-back policy, and the
 *    manifest opts into predictive back.
 */
class SheetDisciplinePinTest {

    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        val candidates = listOf(
            androidRootRelative,
            "RunWear-android/$androidRootRelative",
            "../$androidRootRelative"
        )
        while (dir != null) {
            for (candidate in candidates) {
                val file = File(dir, candidate)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        fail("source file not found: $androidRootRelative (user.dir=${System.getProperty("user.dir")})")
        throw AssertionError("unreachable")
    }

    private fun bottomSheets() =
        sourceFile("app/src/main/java/com/runwear/app/ui/screens/BottomSheets.kt").readText()

    private fun heroMainScreen() =
        sourceFile("app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt").readText()

    private fun makeItMeScreens() =
        sourceFile("app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt").readText()

    private fun occurrences(source: String, needle: String): Int =
        source.split(needle).size - 1

    @Test
    fun `every sheet state in BottomSheets is single-anchor`() {
        val sheets = bottomSheets()
        val states = occurrences(sheets, "rememberModalBottomSheetState(")
        assertEquals(
            "every rememberModalBottomSheetState must pass skipPartiallyExpanded = true",
            states,
            occurrences(sheets, "rememberModalBottomSheetState(skipPartiallyExpanded = true)")
        )
        // GEN-4: the time sheet moved out of BottomSheets into
        // DayChartTimePicker.kt (the chart sheet) — BottomSheets now
        // holds 5 sheets; the moved sheet is pinned by the next test.
        assertTrue("BottomSheets must actually contain sheets", states >= 5)
    }

    @Test
    fun `the GEN-4 chart sheet state is single-anchor`() {
        val sheet = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/DayChartTimePicker.kt"
        ).readText()
        val states = occurrences(sheet, "rememberModalBottomSheetState(")
        assertEquals(1, states)
        assertEquals(
            "the chart sheet must pass skipPartiallyExpanded = true",
            states,
            occurrences(sheet, "rememberModalBottomSheetState(skipPartiallyExpanded = true)")
        )
    }

    @Test
    fun `every sheet state in HeroMainScreen is single-anchor`() {
        val host = heroMainScreen()
        val states = occurrences(host, "rememberModalBottomSheetState(")
        assertEquals(2, states)
        assertEquals(
            states,
            occurrences(host, "rememberModalBottomSheetState(skipPartiallyExpanded = true)")
        )
    }

    @Test
    fun `the paywall sheet is single-anchor`() {
        val mim = makeItMeScreens()
        assertTrue(
            mim.contains("rememberModalBottomSheetState(\n        skipPartiallyExpanded = true\n    )")
        )
        assertTrue(mim.contains("sheetState = sheetState"))
    }

    @Test
    fun `no sheet state anywhere in the app is left at the M3 default`() {
        val mainRoot = sourceFile("app/src/main/java/com/runwear/app/ui/screens/BottomSheets.kt")
            .parentFile?.parentFile // java/com/runwear/app -> walk via known files below
        val files = listOf(
            "app/src/main/java/com/runwear/app/ui/screens/BottomSheets.kt",
            "app/src/main/java/com/runwear/app/ui/screens/DayChartTimePicker.kt",
            "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt",
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt"
        )
        for (path in files) {
            val text = sourceFile(path).readText()
            assertFalse(
                "$path must not create a sheet state at the default (partially-expanded) anchors",
                text.contains("rememberModalBottomSheetState()")
            )
        }
        assertTrue(mainRoot != null)
    }

    @Test
    fun `settings content has exactly one scroll consumer - its LazyColumn`() {
        val sheets = bottomSheets()
        // RW-73 DETENT RULING amendment (Sol, Nov 6, 2026): the
        // file now carries exactly ONE .verticalScroll( — the
        // Date picker content's gated consumer, pinned in
        // datePickerContent_carriesTheOneRuledScrollConsumer
        // below. The D-13 rule this test guards is unchanged in
        // intent: the SETTINGS content nests no scroll consumer
        // inside its LazyColumn.
        assertEquals(
            "BottomSheets carries exactly one verticalScroll (the Date picker's ruled one)",
            1, occurrences(sheets, ".verticalScroll(")
        )
        val contentStart = sheets.indexOf("fun SettingsSheetContent(")
        assertTrue(contentStart >= 0)
        val content = sheets.substring(contentStart)
        assertFalse(
            "no verticalScroll may nest in the Settings content (the D-13 nested consumer)",
            content.contains(".verticalScroll(")
        )
        assertTrue(content.contains("LazyColumn("))
        assertTrue(
            "the content composes the registry, in order",
            content.contains("SettingsInventory.sectionsFor(")
        )
        assertTrue(
            "one LazyColumn item per registered section, keyed by section id",
            content.contains("item(key = section.name)")
        )
    }

    @Test
    fun `settings composes Make It Me and Delete-my-data unconditionally, plus About`() {
        val sheets = bottomSheets()
        val contentStart = sheets.indexOf("fun SettingsSheetContent(")
        val content = sheets.substring(contentStart)
        assertTrue(content.contains("MakeItMeSettingsSection("))
        // GEN-5 (RW-28 item 25): the Delete-my-data row is composed
        // through the Danger zone section, which hosts it (with the
        // photos row) as its own registered section — still
        // unconditional, one level down.
        assertTrue(content.contains("DangerZoneSettingsSection("))
        assertFalse(
            "the Make It Me section may not be gated on a non-null view model (D-14)",
            content.contains("if (makeItMeViewModel != null)")
        )
        assertTrue(content.contains("SettingsInventory.versionLabel("))
        assertTrue(content.contains("com.runwear.app.BuildConfig.VERSION_NAME"))
    }

    @Test
    fun `the Make It Me section renders the not-started row with the flag OFF`() {
        val mim = makeItMeScreens()
        assertTrue(mim.contains("if (!MakeItMeAvailability.ENABLED)"))
        assertFalse(
            "the section may not early-return on the availability gate (D-14)",
            mim.contains("if (!MakeItMeAvailability.settingsSectionVisible()) return")
        )
    }

    @Test
    fun `every sheet dismisses through onDismissRequest`() {
        for ((path, min) in listOf(
            // GEN-4: BottomSheets drops to 5 — the time sheet moved
            // to DayChartTimePicker.kt, pinned in its own row here so
            // the total sheet census is unchanged.
            "app/src/main/java/com/runwear/app/ui/screens/BottomSheets.kt" to 5,
            "app/src/main/java/com/runwear/app/ui/screens/DayChartTimePicker.kt" to 1,
            "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt" to 2,
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt" to 1
        )) {
            val text = sourceFile(path).readText()
            assertTrue(
                "$path: every ModalBottomSheet carries onDismissRequest",
                occurrences(text, "ModalBottomSheet(") <= occurrences(text, "onDismissRequest = onDismiss")
            )
            assertTrue(occurrences(text, "ModalBottomSheet(") >= min)
        }
    }

    // ── F-2 ─────────────────────────────────────────────────────────

    @Test
    fun `your photos is hosted in a full-screen Dialog whose dismiss returns home`() {
        val host = heroMainScreen()
        val photosAt = host.indexOf("MakeItMePhotosScreen(")
        assertTrue(photosAt >= 0)
        val before = host.substring(maxOf(0, photosAt - 1200), photosAt)
        assertTrue(
            "MakeItMePhotosScreen must be hosted inside a Dialog (F-2 conversion)",
            before.contains("androidx.compose.ui.window.Dialog(")
        )
        assertTrue(before.contains("onDismissRequest = { showMakeItMePhotos = false }"))
        assertTrue(before.contains("usePlatformDefaultWidth = false"))
    }

    @Test
    fun `the upload flow carries a BackHandler driven by the step-back policy`() {
        val mim = makeItMeScreens()
        val flowAt = mim.indexOf("fun MakeItMeUploadFlow(")
        assertTrue(flowAt >= 0)
        // GEN-2 A1: the window grew — the flow now opens with the
        // view-model delegating overload and the seam-carrying core
        // overload before the handler; the pin's intent is unchanged
        // (the handler and its policy sit at the flow's head).
        val flowHead = mim.substring(flowAt, flowAt + 6000)
        assertTrue(flowHead.contains("androidx.activity.compose.BackHandler"))
        assertTrue(flowHead.contains("UploadBackPolicy.actionFor(step)"))
        assertTrue(flowHead.contains("UploadBackAction.STEP_BACK_TO_CONSENT"))
    }

    @Test
    fun `upload back policy - back steps back, then exits, never falls through`() {
        assertEquals(UploadBackAction.EXIT_FLOW, UploadBackPolicy.actionFor(UploadStep.CONSENT))
        assertEquals(
            UploadBackAction.STEP_BACK_TO_CONSENT,
            UploadBackPolicy.actionFor(UploadStep.PHOTOS)
        )
        assertEquals(UploadBackAction.STAY, UploadBackPolicy.actionFor(UploadStep.SUBMITTING))
        assertEquals(UploadBackAction.STAY, UploadBackPolicy.actionFor(UploadStep.SAVED))
    }

    // ── GEN-2 A1 (W-2 + W-8) ─────────────────────────────────────

    @Test
    fun `each upload-flow step emits exactly one root - a Column (the W-2 overlap fix)`() {
        val mim = makeItMeScreens().replace(Regex("\\s+"), " ")
        assertTrue(
            "the CONSENT branch must wrap its layout in a single Column root inside AnimatedContent",
            mim.contains("UploadStep.CONSENT -> { Column {")
        )
        assertTrue(
            "the PHOTOS branch must wrap its layout in a single Column root inside AnimatedContent",
            mim.contains("UploadStep.SUBMITTING -> { Column {")
        )
    }

    @Test
    fun `settings content is weight-bounded inside the sheet column (the W-8 fix)`() {
        val sheets = bottomSheets()
        assertTrue(
            "SettingsSheet must pass Modifier.weight(1f) so the list viewport ends at the sheet's bottom edge",
            sheets.contains("modifier = Modifier.weight(1f)")
        )
        val contentStart = sheets.indexOf("fun SettingsSheetContent(")
        assertTrue(contentStart >= 0)
        val content = sheets.substring(contentStart)
        assertTrue(
            "the passed modifier must reach the LazyColumn (the sheet's one scroll consumer)",
            content.contains("modifier = modifier.fillMaxWidth(),")
        )
    }

    @Test
    fun `the feedback overlay carries a BackHandler too`() {
        val feedback = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/FeedbackScreen.kt"
        ).readText()
        assertTrue(feedback.contains("androidx.activity.compose.BackHandler"))
        assertTrue(feedback.contains("BackHandler(enabled = !sending) { onDismiss() }"))
    }

    @Test
    fun `the collection Dialog dismisses through onDismissRequest`() {
        val collection = sourceFile(
            "app/src/main/java/com/runwear/app/weariverun/WearIveRunCollectionScreen.kt"
        ).readText()
        assertTrue(collection.contains("Dialog(onDismissRequest = onDismiss"))
    }

    @Test
    fun `predictive back is registered in the manifest`() {
        val manifest = sourceFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:enableOnBackInvokedCallback=\"true\""))
    }

    @Test
    fun `back handlers exist in the app - the F-2 absence cannot return silently`() {
        val mim = makeItMeScreens()
        val feedback = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/FeedbackScreen.kt"
        ).readText()
        assertTrue(
            occurrences(mim, "BackHandler") + occurrences(feedback, "BackHandler") >= 2
        )
    }

    // -- RW-73 DETENT RULING (Sol, Nov 6, 2026 — delegated to
    // Tank's suggestion; BACKLOG RW-73) --------------------------
    // The picker sheets' contents were the no-scroll-consumers
    // exception to the rule above (their roots carried none).
    // The ruling amends the exception at the LARGEST font scale
    // only: there the untruncated content outgrows the
    // ModalBottomSheet window-height cap, so each picker content
    // carries exactly one vertical scroll consumer, gated on the
    // shared PickerSheetScrollPolicy; at default and moderate
    // scales the fixed, scroll-free form stands. The hosts'
    // skipPartiallyExpanded discipline (pinned above) is
    // unchanged.

    @Test
    fun pickerScrollPolicy_permitsScrollOnlyAtTheLargestScale() {
        assertFalse(PickerSheetScrollPolicy.scrollable(0.85f))
        assertFalse(PickerSheetScrollPolicy.scrollable(1.0f))
        assertFalse(PickerSheetScrollPolicy.scrollable(1.15f))
        assertFalse(PickerSheetScrollPolicy.scrollable(1.29f))
        assertTrue(PickerSheetScrollPolicy.scrollable(1.3f))
        assertTrue(PickerSheetScrollPolicy.scrollable(1.5f))
        assertEquals(1.3f, PickerSheetScrollPolicy.SCROLL_FONT_SCALE, 0.0f)
    }

    @Test
    fun datePickerContent_carriesTheOneRuledScrollConsumer() {
        val src = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/BottomSheets.kt"
        ).readText()
        assertEquals(
            "the Date content carries exactly one vertical scroll consumer",
            1, occurrences(src, "Modifier.verticalScroll(scrollState)")
        )
        assertTrue(
            "the Date scroll must be applied through the ruling's gate",
            src.contains(".then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)")
        )
        assertTrue(
            "the Date gate must read the shared policy",
            src.contains("PickerSheetScrollPolicy.scrollable(LocalDensity.current.fontScale)")
        )
    }

    @Test
    fun timePickerContent_carriesTheOneRuledScrollConsumer() {
        val src = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/DayChartTimePicker.kt"
        ).readText()
        assertEquals(
            "the Time content carries exactly one vertical scroll consumer",
            1, occurrences(src, "Modifier.verticalScroll(scrollState)")
        )
        assertTrue(
            "the Time scroll must be applied through the ruling's gate",
            src.contains(".then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)")
        )
        assertTrue(
            "the Time gate must read the shared policy",
            src.contains("PickerSheetScrollPolicy.scrollable(LocalDensity.current.fontScale)")
        )
    }
}
