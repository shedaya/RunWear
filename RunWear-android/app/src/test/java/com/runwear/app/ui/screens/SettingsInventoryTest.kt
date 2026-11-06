package com.runwear.app.ui.screens

import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.MakeItMeSettingsPresentation
import com.runwear.app.makeitme.MakeItMeStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-5 (D-14) — Settings completeness as a TEST, not a review
 * finding. The designed inventory (frames 35–39; notes §8H) must
 * render in EVERY shipped configuration: both Make It Me flag
 * states × all three coded modes. If a registered section ever
 * emits nothing in a configuration, one of these tests fails.
 */
class SettingsInventoryTest {

    @Test
    fun `the registry is the designed inventory in designed order`() {
        // GEN-5 (RW-28 items 24/25): preferences in the onboarding
        // order (unit, fit, body temperature); the Danger zone
        // section quarantined last, after About.
        assertEquals(
            listOf(
                SettingsSectionId.APPEARANCE,
                SettingsSectionId.TEMPERATURE_UNIT,
                SettingsSectionId.FIT_PREFERENCE,
                SettingsSectionId.BODY_TEMPERATURE,
                SettingsSectionId.MAKE_IT_ME,
                SettingsSectionId.SEND_FEEDBACK,
                SettingsSectionId.RATE_RUNWEAR,
                SettingsSectionId.HELP_IMPROVE,
                SettingsSectionId.ABOUT,
                SettingsSectionId.DELETE_MY_DATA
            ),
            SettingsInventory.SECTIONS
        )
    }

    @Test
    fun `every configuration renders the full inventory - no section emits nothing`() {
        for (enabled in listOf(false, true)) {
            for (mode in MakeItMeMode.entries) {
                assertEquals(
                    "flag=$enabled mode=$mode must render the full inventory",
                    SettingsInventory.SECTIONS,
                    SettingsInventory.sectionsFor(enabled, mode)
                )
            }
        }
    }

    @Test
    fun `the danger zone section is quarantined last, after About`() {
        // GEN-5 (RW-28 item 25, B-16): the destructive section is
        // its own section still (reachable in every mode), but it
        // now sits at the very end of the sheet under its own
        // heading — never above Send feedback.
        val sections = SettingsInventory.SECTIONS
        assertEquals(SettingsSectionId.DELETE_MY_DATA, sections.last())
        assertTrue(sections.indexOf(SettingsSectionId.DELETE_MY_DATA) > sections.indexOf(SettingsSectionId.ABOUT))
    }

    @Test
    fun `the Make It Me section presents rows in every flag and mode configuration`() {
        for (enabled in listOf(false, true)) {
            for (mode in MakeItMeMode.entries) {
                val rows = MakeItMeSettingsPresentation.rowTitles(enabled, mode)
                assertTrue(
                    "flag=$enabled mode=$mode must present at least one row",
                    rows.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun `flag OFF presents exactly the plain not-started row`() {
        for (mode in MakeItMeMode.entries) {
            assertEquals(
                listOf(MakeItMeStrings.S2_STATE_A_ROW),
                MakeItMeSettingsPresentation.rowTitles(false, mode)
            )
        }
    }

    @Test
    fun `each enabled mode presents its designed rows`() {
        assertEquals(
            listOf(MakeItMeStrings.S2_STATE_A_ROW),
            MakeItMeSettingsPresentation.rowTitles(true, MakeItMeMode.STOCK)
        )
        // Gen-3 WS-D: photos group, then the Premium offer card.
        // GEN-5 (RW-28 item 25): the destructive rows live in the
        // Danger zone presentation now, not in this section.
        assertEquals(
            listOf(
                MakeItMeStrings.S6_PHOTOS_ROW,
                MakeItMeStrings.U2G_CARD_TITLE
            ),
            MakeItMeSettingsPresentation.rowTitles(true, MakeItMeMode.FREE)
        )
        assertEquals(
            listOf(
                MakeItMeStrings.S6_PHOTOS_ROW,
                MakeItMeStrings.U2G_CARD_TITLE
            ),
            MakeItMeSettingsPresentation.rowTitles(true, MakeItMeMode.SUBSCRIBED)
        )
        // The Danger zone: photos deletion only for modes holding
        // photos; data deletion in every mode, last.
        assertEquals(
            listOf(MakeItMeStrings.S20_DELETE_DATA_ROW),
            MakeItMeSettingsPresentation.dangerZoneRowTitles(MakeItMeMode.STOCK)
        )
        assertEquals(
            listOf(
                MakeItMeStrings.S10_DELETE_ROW,
                MakeItMeStrings.S20_DELETE_DATA_ROW
            ),
            MakeItMeSettingsPresentation.dangerZoneRowTitles(MakeItMeMode.FREE)
        )
        assertEquals(
            listOf(
                MakeItMeStrings.S10_DELETE_ROW,
                MakeItMeStrings.S20_DELETE_DATA_ROW
            ),
            MakeItMeSettingsPresentation.dangerZoneRowTitles(MakeItMeMode.SUBSCRIBED)
        )
    }

    @Test
    fun `the About version label matches the iOS form`() {
        assertEquals("1.2.15 (30)", SettingsInventory.versionLabel("1.2.15", 30))
    }
}
