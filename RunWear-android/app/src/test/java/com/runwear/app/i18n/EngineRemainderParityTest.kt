package com.runwear.app.i18n

import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.service.RunWearTips
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOC-1 WP-R2 gate: the engine display remainder (comfort short
 * labels, gender labels, RW-144 bracket words, and the tips) is
 * canonical in :engine moko resources; the runtime facades must
 * stay BYTE-IDENTICAL to them. Same disk-read pattern as the WP-3
 * pilot's MimResourceParityTest.
 */
class EngineRemainderParityTest {

    private fun xmlFile(name: String): File {
        val candidates = listOf(
            "RunWear-android/engine/src/commonMain/moko-resources/base/$name",
            "engine/src/commonMain/moko-resources/base/$name",
            "../engine/src/commonMain/moko-resources/base/$name"
        )
        return candidates.map(::File).firstOrNull { it.exists() }
            ?: throw AssertionError("engine $name not found from ${File("").absolutePath}")
    }

    private fun unescape(v: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < v.length) {
            if (v[i] == '\\' && i + 1 < v.length) {
                out.append(
                    when (v[i + 1]) {
                        'n' -> '\n'
                        't' -> '\t'
                        else -> v[i + 1]
                    }
                )
                i += 2
            } else {
                out.append(v[i])
                i++
            }
        }
        return out.toString()
    }

    private fun strings(): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile("strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()
        for (idx in 0 until nodes.length) {
            val node = nodes.item(idx)
            map[node.attributes.getNamedItem("name").nodeValue] = unescape(node.textContent)
        }
        return map
    }

    private fun plurals(): Map<String, Map<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile("plurals.xml"))
        val nodes = doc.getElementsByTagName("plurals")
        val map = mutableMapOf<String, Map<String, String>>()
        for (idx in 0 until nodes.length) {
            val node = nodes.item(idx) as org.w3c.dom.Element
            val items = node.getElementsByTagName("item")
            val forms = mutableMapOf<String, String>()
            for (j in 0 until items.length) {
                val item = items.item(j) as org.w3c.dom.Element
                forms[item.getAttribute("quantity")] = unescape(item.textContent)
            }
            map[node.getAttribute("name")] = forms
        }
        return map
    }

    @Test
    fun comfortShortLabelsMatchResources() {
        val res = strings()
        for (pref in ComfortPreference.entries) {
            assertEquals(
                pref.shortLabel,
                res["comfort_" + pref.name.lowercase() + "_short"]
            )
        }
    }

    @Test
    fun genderLabelsMatchResourcesAndUnisexStaysNull() {
        val res = strings()
        assertEquals(GenderPreference.MALE.label, res["gender_male_label"])
        assertEquals(GenderPreference.FEMALE.label, res["gender_female_label"])
        assertEquals("", GenderPreference.UNISEX.label)
        assertTrue("UNISEX must not have a resource", !res.containsKey("gender_unisex_label"))
    }

    @Test
    fun bracketWordsMatchResources() {
        val res = strings()
        for (bracket in TempBracket.entries) {
            assertEquals(
                "the shipped band word is the enum name",
                bracket.name,
                res["bracket_" + bracket.name.lowercase() + "_word"]
            )
        }
    }

    @Test
    fun tipsMatchResourcesByteForByte() {
        val res = strings()
        assertEquals(RunWearTips.FEEL_COOL, res["tip_feel_cool"])
        assertEquals(RunWearTips.LAYERED, res["tip_layered"])
        assertEquals(RunWearTips.WIND_SHELL, res["tip_wind_shell"])
        assertEquals(RunWearTips.HEAT_RISK, res["tip_heat_risk"])
        assertEquals(RunWearTips.HYDRATE_FULL, res["tip_hydrate_full"])
        assertEquals(RunWearTips.HYDRATE_CARRY, res["tip_hydrate_carry"])
        assertEquals(RunWearTips.RAIN_CAP, res["tip_rain_cap"])
        assertEquals(RunWearTips.HUMIDITY, res["tip_humidity"])
        assertEquals(RunWearTips.UV, res["tip_uv"])
        assertEquals(RunWearTips.SHORTS_COLD, res["tip_shorts_cold"])
        assertEquals(RunWearTips.FROSTBITE_TEMPLATE, res["tip_frostbite"])
    }

    @Test
    fun frostbiteUnitSlotRendersBothFrames() {
        // The shipped English sentence under Fahrenheit, byte for byte.
        assertEquals(
            "🥶 Cover all exposed skin — frostbite risk increases below 10°F with wind. " +
                "Vaseline on cheeks and nose helps.",
            RunWearTips.frostbite(TemperatureUnit.FAHRENHEIT)
        )
        // The same decision value through the Celsius frame.
        assertEquals(
            "🥶 Cover all exposed skin — frostbite risk increases below -12°C with wind. " +
                "Vaseline on cheeks and nose helps.",
            RunWearTips.frostbite(TemperatureUnit.CELSIUS)
        )
        assertEquals(10.0, RunWearTips.FROSTBITE_THRESHOLD_F, 0.0)
    }

    @Test
    fun enginePluralsCarryTheEnForms() {
        val all = plurals()
        assertEquals("%1\$d item", all.getValue("wir_items_count")["one"])
        assertEquals("%1\$d items", all.getValue("wir_items_count")["other"])
        assertEquals("%1\$d day", all.getValue("wir_days_count")["one"])
        assertEquals("%1\$d days", all.getValue("wir_days_count")["other"])
        assertEquals("%1\$d stay", all.getValue("wir_stays_count")["one"])
        assertEquals("%1\$d stays", all.getValue("wir_stays_count")["other"])
        assertEquals("Last saved %1\$d min ago", all.getValue("wir_last_saved_minutes")["one"])
        assertEquals("Last saved %1\$d min ago", all.getValue("wir_last_saved_minutes")["other"])
        assertEquals("Last saved %1\$d hr ago", all.getValue("wir_last_saved_hours")["one"])
        assertEquals("Last saved %1\$d hr ago", all.getValue("wir_last_saved_hours")["other"])
        assertEquals("%1\$d items for your run", all.getValue("outfit_items_for_run")["other"])
        assertNull(all["wear_swipe_items"]?.get("zero"))
    }
}
