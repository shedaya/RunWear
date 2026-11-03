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
        assertEquals(
            listOf(
                SettingsSectionId.APPEARANCE,
                SettingsSectionId.TEMPERATURE_UNIT,
                SettingsSectionId.BODY_TEMPERATURE,
                SettingsSectionId.FIT_PREFERENCE,
                SettingsSectionId.MAKE_IT_ME,
                SettingsSectionId.DELETE_MY_DATA,
                SettingsSectionId.SEND_FEEDBACK,
                SettingsSectionId.HELP_IMPROVE,
                SettingsSectionId.ABOUT
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
    fun `delete-my-data is its own section, after Make It Me and before Support`() {
        val sections = SettingsInventory.SECTIONS
        assertTrue(sections.indexOf(SettingsSectionId.DELETE_MY_DATA) > sections.indexOf(SettingsSectionId.MAKE_IT_ME))
        assertTrue(sections.indexOf(SettingsSectionId.DELETE_MY_DATA) < sections.indexOf(SettingsSectionId.SEND_FEEDBACK))
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
        assertEquals(
            listOf(
                MakeItMeStrings.S6_PHOTOS_ROW,
                MakeItMeStrings.S8_STATE_B_SUBSCRIBE_ROW,
                MakeItMeStrings.S10_DELETE_ROW
            ),
            MakeItMeSettingsPresentation.rowTitles(true, MakeItMeMode.FREE)
        )
        assertEquals(
            listOf(
                MakeItMeStrings.S6_PHOTOS_ROW,
                MakeItMeStrings.S9_MANAGE_SUBSCRIPTION,
                MakeItMeStrings.S10_DELETE_ROW
            ),
            MakeItMeSettingsPresentation.rowTitles(true, MakeItMeMode.SUBSCRIBED)
        )
    }

    @Test
    fun `the About version label matches the iOS form`() {
        assertEquals("1.2.15 (30)", SettingsInventory.versionLabel("1.2.15", 30))
    }
}
