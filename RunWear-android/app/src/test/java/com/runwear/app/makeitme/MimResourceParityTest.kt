package com.runwear.app.makeitme

import java.io.File
import java.lang.reflect.Modifier
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOC-1 WP-3 pilot gate: the moko base resources in :engine are the
 * canonical Make It Me copy; this facade's constants must stay
 * BYTE-IDENTICAL to them, and the parameterized templates staged in
 * the resources must render exactly what the Kotlin functions compose.
 * If this test fails, a copy change landed on one side only — land it
 * in both (resources first), per the note on [MakeItMeStrings].
 */
class MimResourceParityTest {

    private fun stringsXml(): File {
        val candidates = listOf(
            "RunWear-android/engine/src/commonMain/moko-resources/base/strings.xml",
            "engine/src/commonMain/moko-resources/base/strings.xml",
            "../engine/src/commonMain/moko-resources/base/strings.xml"
        )
        return candidates.map(::File).firstOrNull { it.exists() }
            ?: throw AssertionError("engine strings.xml not found from ${File("").absolutePath}")
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

    private fun resources(): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsXml())
        val nodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()
        for (idx in 0 until nodes.length) {
            val node = nodes.item(idx)
            map[node.attributes.getNamedItem("name").nodeValue] = unescape(node.textContent)
        }
        return map
    }

    @Test
    fun everyPureConstantMatchesItsResourceByteForByte() {
        val res = resources()
        var checked = 0
        for (field in MakeItMeStrings::class.java.declaredFields) {
            if (field.type != String::class.java || !Modifier.isStatic(field.modifiers)) continue
            val key = "mim_" + field.name.lowercase()
            val expected = field.get(null) as String
            assertTrue("resource missing for ${field.name} ($key)", res.containsKey(key))
            assertEquals("value drift on ${field.name}", expected, res[key])
            checked++
        }
        assertTrue("expected >=100 constants checked, got $checked", checked >= 100)
    }

    @Test
    fun catalogDisplayValuesMatchTheEnumLiterals() {
        val res = resources()
        var checked = 0
        for (item in com.runwear.shared.domain.model.ClothingItem.entries) {
            val base = "clothing_" + item.name.lowercase()
            assertEquals(item.displayName, res[base + "_name"])
            assertEquals(item.description, res[base + "_desc"])
            checked += 2
        }
        assertEquals(60, checked)
    }

    @Test
    fun weatherAndComfortDisplayValuesMatchTheEnumLiterals() {
        val res = resources()
        for (code in com.runwear.shared.domain.model.WeatherCode.entries) {
            assertEquals(code.description, res["weather_" + code.name.lowercase() + "_desc"])
        }
        for (pref in com.runwear.shared.domain.model.ComfortPreference.entries) {
            assertEquals(pref.label, res["comfort_" + pref.name.lowercase() + "_label"])
        }
    }

    @Test
    fun parameterizedTemplatesRenderWhatTheFunctionsCompose() {
        val res = resources()
        val allowance = MakeItMeUpsellRules.FREE_ALLOWANCE
        assertEquals(MakeItMeStrings.heroPillLookCount(3), String.format(res.getValue("mim_hero_pill_look_count"), 3, allowance))
        assertEquals(MakeItMeStrings.HERO_PILL_EXHAUSTED_COUNT, String.format(res.getValue("mim_hero_pill_exhausted_count"), allowance))
        assertEquals(MakeItMeStrings.s4FreeUsed(3), String.format(res.getValue("mim_s4_free_used"), 3, allowance))
        assertEquals(MakeItMeStrings.s4FreeUsed(allowance), String.format(res.getValue("mim_s4_free_used_all"), allowance))
        assertEquals(MakeItMeStrings.u2MeterLine(3), String.format(res.getValue("mim_u2_meter_line"), 3, allowance))
        assertEquals(MakeItMeStrings.U2D_FREE_BAR, String.format(res.getValue("mim_u2d_free_bar"), allowance))
        assertEquals(MakeItMeStrings.monthlyRowLabel("$0.99"), String.format(res.getValue("mim_monthly_row_label"), "$0.99"))
        assertEquals(MakeItMeStrings.annualRowLabel("$9.99", 9990000, 990000), String.format(res.getValue("mim_annual_row_label"), "$9.99"))
        assertEquals(MakeItMeStrings.perRunLine("$9.99", 9990000), String.format(res.getValue("mim_per_run_line"), MakeItMeStrings.perRunCents(9990000)))
        assertEquals(MakeItMeStrings.perRunBasis("$9.99"), String.format(res.getValue("mim_per_run_basis"), "$9.99"))
        assertEquals(
            MakeItMeStrings.planCtaLabel("Annual", "$9.99/yr"),
            String.format(res.getValue("mim_plan_cta_label"), MakeItMeStrings.S27_GET_PREMIUM, "Annual", "$9.99/yr")
        )
        assertEquals(MakeItMeStrings.firstLookHeading("New York"), String.format(res.getValue("mim_first_look_heading_place"), "New York"))
        assertEquals(MakeItMeStrings.firstLookHeading(null), res.getValue("mim_first_look_heading_today"))
        assertEquals(MakeItMeStrings.firstLookCountLine(1), String.format(res.getValue("mim_first_look_count_line_other"), 1, allowance, allowance - 1))
        assertEquals(MakeItMeStrings.FIRST_LOOK_COUNT_LINE, MakeItMeStrings.firstLookCountLine(1))
    }
}
