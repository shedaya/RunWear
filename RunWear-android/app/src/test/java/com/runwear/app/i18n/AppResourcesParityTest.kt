package com.runwear.app.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.runwear.app.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LOC-1 WP-R2 gate: the :app Android resources are canonical for the
 * platform-edge surface; the [AppEnglish] pure-code mirror must stay
 * BYTE-IDENTICAL to them (rendered values — XML escapes resolved),
 * and the plurals must carry the CLDR en forms the narration uses.
 *
 * Two layers, like the WP-3 pilot's gate: disk reads of the resource
 * files (the authority, run green in plain JUnit) plus runtime reads
 * through the actual inflater (Robolectric) for a sample of keys,
 * proving the shipped artifact resolves the same text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppResourcesParityTest {

    private fun resFile(name: String): File {
        val candidates = listOf(
            "RunWear-android/app/src/main/res/values/$name",
            "app/src/main/res/values/$name",
            "../app/src/main/res/values/$name",
            "src/main/res/values/$name"
        )
        return candidates.map(::File).firstOrNull { it.exists() }
            ?: throw AssertionError("app $name not found from ${File("").absolutePath}")
    }

    private fun unescape(raw: String): String {
        // AAPT quote-layer: a value wrapped in double quotes keeps
        // its significant edge whitespace; strip one quote layer
        // before resolving backslash escapes (mirrors the runtime).
        val v =
            if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') {
                raw.substring(1, raw.length - 1)
            } else {
                raw
            }
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
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resFile("strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()
        for (idx in 0 until nodes.length) {
            val node = nodes.item(idx)
            map[node.attributes.getNamedItem("name").nodeValue] = unescape(node.textContent)
        }
        return map
    }

    private fun plurals(): Map<String, Map<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resFile("plurals.xml"))
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
    fun mirrorCoversEveryFacadeValue() {
        val res = strings()
        val byResKey = listOf(
            "onb_headline" to AppEnglish.ONB_HEADLINE,
            "onb_promise_1_headline" to AppEnglish.ONB_PROMISE_1_HEADLINE,
            "onb_promise_1_phrase" to AppEnglish.ONB_PROMISE_1_PHRASE,
            "onb_promise_2_headline" to AppEnglish.ONB_PROMISE_2_HEADLINE,
            "onb_promise_2_phrase" to AppEnglish.ONB_PROMISE_2_PHRASE,
            "onb_promise_3_headline" to AppEnglish.ONB_PROMISE_3_HEADLINE,
            "onb_promise_3_phrase" to AppEnglish.ONB_PROMISE_3_PHRASE,
            "onb_headline_line" to AppEnglish.ONB_HEADLINE_LINE_TEMPLATE,
            "onb_promise_cta" to AppEnglish.ONB_PROMISE_CTA,
            "onb_promise_footer" to AppEnglish.ONB_PROMISE_FOOTER,
            "onb_free_looks_promise" to AppEnglish.ONB_FREE_LOOKS_PROMISE_TEMPLATE,
            "onb_setup_step_label" to AppEnglish.ONB_SETUP_STEP_LABEL,
            "onb_setup_title" to AppEnglish.ONB_SETUP_TITLE,
            "onb_setup_sub" to AppEnglish.ONB_SETUP_SUB,
            "onb_temp_title" to AppEnglish.ONB_TEMP_TITLE,
            "onb_temp_sub" to AppEnglish.ONB_TEMP_SUB,
            "onb_fit_title" to AppEnglish.ONB_FIT_TITLE,
            "onb_fit_sub" to AppEnglish.ONB_FIT_SUB,
            "onb_body_title" to AppEnglish.ONB_BODY_TITLE,
            "onb_body_sub" to AppEnglish.ONB_BODY_SUB,
            "onb_temp_option_fahrenheit" to AppEnglish.ONB_TEMP_OPTION_FAHRENHEIT,
            "onb_temp_option_celsius" to AppEnglish.ONB_TEMP_OPTION_CELSIUS,
            "onb_fit_option_mens" to AppEnglish.ONB_FIT_OPTION_MENS,
            "onb_fit_option_womens" to AppEnglish.ONB_FIT_OPTION_WOMENS,
            "onb_body_slider_min" to AppEnglish.ONB_BODY_SLIDER_MIN_LABEL,
            "onb_body_slider_max" to AppEnglish.ONB_BODY_SLIDER_MAX_LABEL,
            "onb_setup_cta" to AppEnglish.ONB_SETUP_CTA,
            "onb_location_step_label" to AppEnglish.ONB_LOCATION_STEP_LABEL,
            "onb_location_title" to AppEnglish.ONB_LOCATION_TITLE,
            "onb_location_body" to AppEnglish.ONB_LOCATION_BODY,
            "onb_location_cta_primary" to AppEnglish.ONB_LOCATION_CTA_PRIMARY,
            "onb_location_cta_secondary" to AppEnglish.ONB_LOCATION_CTA_SECONDARY,
            "looks_title" to AppEnglish.LOOKS_TITLE,
            "looks_why_door" to AppEnglish.LOOKS_WHY_DOOR,
            "looks_explanation" to AppEnglish.LOOKS_EXPLANATION,
            "looks_empty_line" to AppEnglish.LOOKS_EMPTY_LINE,
            "permission_location_privacy" to AppEnglish.PERMISSION_LOCATION_PRIVACY,
            "feedback_cat_something_wrong" to AppEnglish.FEEDBACK_CAT_SOMETHING_WRONG,
            "feedback_cat_recommendation_off" to AppEnglish.FEEDBACK_CAT_RECOMMENDATION_OFF,
            "feedback_cat_confusing" to AppEnglish.FEEDBACK_CAT_CONFUSING,
            "feedback_cat_idea" to AppEnglish.FEEDBACK_CAT_IDEA,
            "feedback_cat_other" to AppEnglish.FEEDBACK_CAT_OTHER,
            "outfit_header_items" to AppEnglish.OUTFIT_HEADER_ITEMS_TEMPLATE,
            "loading_first_recommendation" to AppEnglish.LOADING_FIRST_RECOMMENDATION,
            "loading_getting_your_location" to AppEnglish.LOADING_GETTING_YOUR_LOCATION,
            "loading_getting_location_state" to AppEnglish.LOADING_GETTING_LOCATION_STATE,
            "loading_finding_location" to AppEnglish.LOADING_FINDING_LOCATION,
            "loading_forecast" to AppEnglish.LOADING_FORECAST,
            "weather_tip_overcast" to AppEnglish.WEATHER_TIP_OVERCAST,
            "weather_tip_rain" to AppEnglish.WEATHER_TIP_RAIN,
            "weather_tip_warm" to AppEnglish.WEATHER_TIP_WARM,
            "weather_tip_cold" to AppEnglish.WEATHER_TIP_COLD,
            "weather_tip_default" to AppEnglish.WEATHER_TIP_DEFAULT,
            "hero_feels_like_label" to AppEnglish.HERO_FEELS_LIKE_LABEL,
            "day_today" to AppEnglish.DAY_TODAY,
            "day_tomorrow" to AppEnglish.DAY_TOMORROW,
            "loc_gps_enabled" to AppEnglish.LOC_GPS_ENABLED,
            "loc_requires_permission" to AppEnglish.LOC_REQUIRES_PERMISSION,
            "loc_fail_permission" to AppEnglish.LOC_FAIL_PERMISSION,
            "loc_fail_timeout" to AppEnglish.LOC_FAIL_TIMEOUT,
            "loc_fail_unavailable" to AppEnglish.LOC_FAIL_UNAVAILABLE,
            "loc_finding" to AppEnglish.LOC_FINDING,
            "cat_base_layer" to AppEnglish.CAT_BASE_LAYER,
            "cat_mid_layer" to AppEnglish.CAT_MID_LAYER,
            "cat_outer_layer" to AppEnglish.CAT_OUTER_LAYER,
            "cat_bottom" to AppEnglish.CAT_BOTTOM,
            "cat_headwear" to AppEnglish.CAT_HEADWEAR,
            "cat_gloves" to AppEnglish.CAT_GLOVES,
            "cat_accessories" to AppEnglish.CAT_ACCESSORIES,
            "vm_your_location" to AppEnglish.VM_YOUR_LOCATION,
            "vm_weather_error" to AppEnglish.VM_WEATHER_ERROR,
            "vm_current_location_title" to AppEnglish.VM_CURRENT_LOCATION_TITLE,
            "layer_base" to AppEnglish.LAYER_BASE,
            "layer_mid" to AppEnglish.LAYER_MID,
            "layer_outer" to AppEnglish.LAYER_OUTER,
            "layer_bottom" to AppEnglish.LAYER_BOTTOM,
            "layer_head" to AppEnglish.LAYER_HEAD,
            "layer_hands" to AppEnglish.LAYER_HANDS,
            "wir_place_moment" to AppEnglish.WIR_PLACE_MOMENT,
            "wir_moment_dated" to AppEnglish.WIR_MOMENT_DATED,
            "wir_now" to AppEnglish.WIR_NOW,
            "wir_subtitle_detail" to AppEnglish.WIR_SUBTITLE_DETAIL,
            "wir_subtitle_join" to AppEnglish.WIR_SUBTITLE_JOIN,
            "wir_updated_to" to AppEnglish.WIR_UPDATED_TO,
            "wir_added" to AppEnglish.WIR_ADDED,
            "wir_added_payload" to AppEnglish.WIR_ADDED_PAYLOAD,
            "wir_planned_for" to AppEnglish.WIR_PLANNED_FOR,
            "wir_plan_suffix_day" to AppEnglish.WIR_PLAN_SUFFIX_DAY,
            "wir_plan_suffix_moment" to AppEnglish.WIR_PLAN_SUFFIX_MOMENT,
            "wir_updated_today_payload" to AppEnglish.WIR_UPDATED_TODAY_PAYLOAD,
            "wir_entry_line_head" to AppEnglish.WIR_ENTRY_LINE_HEAD,
            "wir_entry_line_moment" to AppEnglish.WIR_ENTRY_LINE_MOMENT,
            "wir_entry_line_tail" to AppEnglish.WIR_ENTRY_LINE_TAIL,
            "wir_wordmark" to AppEnglish.WIR_WORDMARK,
            "wir_day_today" to AppEnglish.WIR_DAY_TODAY,
            "wir_day_tomorrow" to AppEnglish.WIR_DAY_TOMORROW,
            "wir_summary_multi" to AppEnglish.WIR_SUMMARY_MULTI,
            "wir_summary_single" to AppEnglish.WIR_SUMMARY_SINGLE,
            "wir_summary_range_suffix" to AppEnglish.WIR_SUMMARY_RANGE_SUFFIX,
            "wir_feels_range" to AppEnglish.WIR_FEELS_RANGE,
            "wir_feels_single" to AppEnglish.WIR_FEELS_SINGLE,
            "wir_span_same_month" to AppEnglish.WIR_SPAN_SAME_MONTH,
            "wir_span_cross" to AppEnglish.WIR_SPAN_CROSS,
            "wir_last_saved_just_now" to AppEnglish.WIR_LAST_SAVED_JUST_NOW,
            "wir_last_saved_day" to AppEnglish.WIR_LAST_SAVED_DAY,
            "wir_month_count_suffix" to AppEnglish.WIR_MONTH_COUNT_SUFFIX,
            "collection_day_title_today" to AppEnglish.COLLECTION_DAY_TITLE_TODAY,
            "wir_year_count" to AppEnglish.WIR_YEAR_COUNT,
            "share_format_feed" to AppEnglish.SHARE_FORMAT_FEED,
            "share_format_stories" to AppEnglish.SHARE_FORMAT_STORIES,
            "share_format_square" to AppEnglish.SHARE_FORMAT_SQUARE,
            "share_meridiem_am" to AppEnglish.SHARE_MERIDIEM_AM,
            "share_meridiem_pm" to AppEnglish.SHARE_MERIDIEM_PM,
            "share_clock_caption" to AppEnglish.SHARE_CLOCK_CAPTION,
            "share_clock_poster" to AppEnglish.SHARE_CLOCK_POSTER,
            "share_clock_time" to AppEnglish.SHARE_CLOCK_TIME,
            "share_tomorrow_caption" to AppEnglish.SHARE_TOMORROW_CAPTION,
            "share_tomorrow_poster" to AppEnglish.SHARE_TOMORROW_POSTER,
            "share_daypart_caption" to AppEnglish.SHARE_DAYPART_CAPTION,
            "share_daypart_poster" to AppEnglish.SHARE_DAYPART_POSTER,
            "share_morning_caption" to AppEnglish.SHARE_MORNING_CAPTION,
            "share_morning_poster" to AppEnglish.SHARE_MORNING_POSTER,
            "share_afternoon_caption" to AppEnglish.SHARE_AFTERNOON_CAPTION,
            "share_afternoon_poster" to AppEnglish.SHARE_AFTERNOON_POSTER,
            "share_tonight_caption" to AppEnglish.SHARE_TONIGHT_CAPTION,
            "share_tonight_poster" to AppEnglish.SHARE_TONIGHT_POSTER,
            "share_title_feels_story" to AppEnglish.SHARE_TITLE_FEELS_STORY,
            "share_title_swing" to AppEnglish.SHARE_TITLE_SWING,
            "share_title_scrapbook" to AppEnglish.SHARE_TITLE_SCRAPBOOK,
            "share_title_short_call" to AppEnglish.SHARE_TITLE_SHORT_CALL,
            "share_kit_bullet" to AppEnglish.SHARE_KIT_BULLET,
            "share_why_swing" to AppEnglish.SHARE_WHY_SWING,
            "share_why_cold" to AppEnglish.SHARE_WHY_COLD,
            "share_why_anchor" to AppEnglish.SHARE_WHY_ANCHOR,
            "share_caption_feels_story" to AppEnglish.SHARE_CAPTION_FEELS_STORY,
            "share_caption_swing" to AppEnglish.SHARE_CAPTION_SWING,
            "share_caption_scrapbook" to AppEnglish.SHARE_CAPTION_SCRAPBOOK,
            "share_caption_short_call" to AppEnglish.SHARE_CAPTION_SHORT_CALL,
            "share_subject" to AppEnglish.SHARE_SUBJECT,
            "share_link_title" to AppEnglish.SHARE_LINK_TITLE,
            "share_weather_line" to AppEnglish.SHARE_WEATHER_LINE,
            "share_disclosure" to AppEnglish.SHARE_DISCLOSURE,
            "widget_loading" to AppEnglish.WIDGET_LOADING,
            "widget_loading_outfit" to AppEnglish.WIDGET_LOADING_OUTFIT,
            "widget_loading_strip" to AppEnglish.WIDGET_LOADING_STRIP,
            "widget_location_needed" to AppEnglish.WIDGET_LOCATION_NEEDED,
            "widget_tap_to_enable" to AppEnglish.WIDGET_TAP_TO_ENABLE,
            "widget_location_strip" to AppEnglish.WIDGET_LOCATION_STRIP,
            "widget_tap_enable_location" to AppEnglish.WIDGET_TAP_ENABLE_LOCATION,
            "widget_feels_like_caps" to AppEnglish.WIDGET_FEELS_LIKE_CAPS,
            "widget_actual" to AppEnglish.WIDGET_ACTUAL_TEMPLATE,
            "widget_feels_like" to AppEnglish.WIDGET_FEELS_LIKE_TEMPLATE,
            "widget_wind" to AppEnglish.WIDGET_WIND_TEMPLATE,
            "widget_couldnt_load" to AppEnglish.WIDGET_COULDNT_LOAD,
            "widget_couldnt_reach" to AppEnglish.WIDGET_COULDNT_REACH,
            "widget_location_unavailable" to AppEnglish.WIDGET_LOCATION_UNAVAILABLE,
            "widget_refresh" to AppEnglish.WIDGET_REFRESH,
            "widget_retry" to AppEnglish.WIDGET_RETRY,
            "widget_later_today" to AppEnglish.WIDGET_LATER_TODAY,
            "widget_later_today_range" to AppEnglish.WIDGET_LATER_TODAY_RANGE,
            "widget_by_line" to AppEnglish.WIDGET_BY_LINE,
            "widget_for_now" to AppEnglish.WIDGET_FOR_NOW,
            "widget_for_now_age" to AppEnglish.WIDGET_FOR_NOW_AGE,
            "widget_just_now" to AppEnglish.WIDGET_JUST_NOW,
            "widget_updated" to AppEnglish.WIDGET_UPDATED,
            "widget_age_min_upper" to AppEnglish.WIDGET_AGE_MIN_UPPER,
            "widget_age_hr_upper" to AppEnglish.WIDGET_AGE_HR_UPPER,
            "widget_age_day_upper" to AppEnglish.WIDGET_AGE_DAY_UPPER
        )
        assertEquals("parity table must cover the whole mirror", 168, byResKey.size)
        for ((key, mirror) in byResKey) {
            assertEquals("resource $key", res[key], mirror)
        }
    }

    @Test
    fun appPluralsMatchTheMirrorForms() {
        val all = plurals()
        assertEquals(AppEnglish.WIR_ITEMS_COUNT_ONE, all.getValue("wir_items_count")["one"])
        assertEquals(AppEnglish.WIR_ITEMS_COUNT_OTHER, all.getValue("wir_items_count")["other"])
        assertEquals(AppEnglish.WIR_DAYS_COUNT_ONE, all.getValue("wir_days_count")["one"])
        assertEquals(AppEnglish.WIR_DAYS_COUNT_OTHER, all.getValue("wir_days_count")["other"])
        assertEquals(AppEnglish.WIR_STAYS_COUNT_ONE, all.getValue("wir_stays_count")["one"])
        assertEquals(AppEnglish.WIR_STAYS_COUNT_OTHER, all.getValue("wir_stays_count")["other"])
        assertEquals(AppEnglish.WIR_LAST_SAVED_MINUTES_ONE, all.getValue("wir_last_saved_minutes")["one"])
        assertEquals(AppEnglish.WIR_LAST_SAVED_MINUTES_OTHER, all.getValue("wir_last_saved_minutes")["other"])
        assertEquals(AppEnglish.WIR_LAST_SAVED_HOURS_ONE, all.getValue("wir_last_saved_hours")["one"])
        assertEquals(AppEnglish.WIR_LAST_SAVED_HOURS_OTHER, all.getValue("wir_last_saved_hours")["other"])
        // The selector renders the shipped English for both counts.
        assertEquals("1 item", AppEnglish.plural(1, AppEnglish.WIR_ITEMS_COUNT_ONE, AppEnglish.WIR_ITEMS_COUNT_OTHER))
        assertEquals("3 items", AppEnglish.plural(3, AppEnglish.WIR_ITEMS_COUNT_ONE, AppEnglish.WIR_ITEMS_COUNT_OTHER))
        assertEquals("1 day", AppEnglish.plural(1, AppEnglish.WIR_DAYS_COUNT_ONE, AppEnglish.WIR_DAYS_COUNT_OTHER))
        assertEquals("2 days", AppEnglish.plural(2, AppEnglish.WIR_DAYS_COUNT_ONE, AppEnglish.WIR_DAYS_COUNT_OTHER))
    }

    @Test
    fun runtimeInflationResolvesTheSameEnglish() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(AppEnglish.ONB_HEADLINE, context.getString(R.string.onb_headline))
        assertEquals(AppEnglish.WIR_WORDMARK, context.getString(R.string.wir_wordmark))
        assertEquals(AppEnglish.SHARE_DISCLOSURE, context.getString(R.string.share_disclosure))
        assertEquals(
            "RunWear — New York, 52° feels like",
            context.getString(R.string.share_subject, "New York", 52)
        )
        assertEquals(
            "1 item", context.resources.getQuantityString(R.plurals.wir_items_count, 1, 1)
        )
        assertEquals(
            "4 items", context.resources.getQuantityString(R.plurals.wir_items_count, 4, 4)
        )
        assertEquals(
            "Last saved 1 hr ago",
            context.resources.getQuantityString(R.plurals.wir_last_saved_hours, 1, 1)
        )
    }
}
