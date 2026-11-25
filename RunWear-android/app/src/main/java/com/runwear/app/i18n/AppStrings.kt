package com.runwear.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.runwear.app.R

/**
 * LOC-1 WP-R2 — the app-module Android resources facade.
 *
 * Platform-edge copy (Compose UI in :app) lives in `res/values/strings.xml`
 * per the WP-3 mechanism split (moko in :engine for shared copy; Android
 * resources at platform edges). These thin composable accessors exist so
 * call sites never spell a raw R reference inline and the parity tests
 * have one seam to pin. Values are byte-identical to the pre-extraction
 * literals; AndroidResourceParityTest proves it against the shipped
 * sources' recorded values.
 */
object AppStrings {
    @Composable fun selectDate(): String = stringResource(R.string.a11y_select_date)
    @Composable fun selectTime(): String = stringResource(R.string.a11y_select_time)
    @Composable fun jumpToNow(): String = stringResource(R.string.a11y_jump_to_now)
    @Composable fun returnToNow(): String = stringResource(R.string.a11y_return_to_now)
    @Composable fun settings(): String = stringResource(R.string.a11y_settings)
    @Composable fun flagPhoto(): String = stringResource(R.string.a11y_flag_photo)
    @Composable fun anotherPhoto(): String = stringResource(R.string.a11y_another_photo)
    @Composable fun outfitPhotoFull(): String = stringResource(R.string.a11y_outfit_photo_full)
    @Composable fun shopItem(): String = stringResource(R.string.a11y_shop_item)
    @Composable fun showMore(): String = stringResource(R.string.a11y_show_more)
    @Composable fun showLess(): String = stringResource(R.string.a11y_show_less)
    @Composable fun cancel(): String = stringResource(R.string.action_cancel)
    @Composable fun confirm(): String = stringResource(R.string.action_confirm)
    @Composable fun done(): String = stringResource(R.string.action_done)
    @Composable fun close(): String = stringResource(R.string.action_close)
    @Composable fun tryAgain(): String = stringResource(R.string.action_try_again)
    @Composable fun edit(): String = stringResource(R.string.action_edit)
    @Composable fun share(): String = stringResource(R.string.action_share)
    @Composable fun notNow(): String = stringResource(R.string.action_not_now)
    @Composable fun removeFromCard(): String = stringResource(R.string.action_remove_from_card)
    @Composable fun shopArrow(): String = stringResource(R.string.action_shop_arrow)
    @Composable fun searchPlace(): String = stringResource(R.string.search_place_placeholder)
    @Composable fun feedbackIntro(): String = stringResource(R.string.feedback_intro)
    @Composable fun feedbackBodyPlaceholder(): String = stringResource(R.string.feedback_body_placeholder)
    @Composable fun feedbackEmailLabel(): String = stringResource(R.string.feedback_email_label)
    @Composable fun feedbackEmailPlaceholder(): String = stringResource(R.string.feedback_email_placeholder)
    @Composable fun sendFeedback(): String = stringResource(R.string.feedback_send)
    @Composable fun rateRunWear(): String = stringResource(R.string.feedback_rate)
    @Composable fun gpsHelpTitle(): String = stringResource(R.string.permission_gps_help_title)
    @Composable fun expandWir(): String = stringResource(R.string.wir_expand)
    @Composable fun collapseWir(): String = stringResource(R.string.wir_collapse)
    @Composable fun removeWir(): String = stringResource(R.string.wir_remove)
    @Composable fun openCollection(): String = stringResource(R.string.wir_open_collection)
    @Composable fun viewCollection(): String = stringResource(R.string.wir_view_collection)
    @Composable fun viewCollectionFull(): String = stringResource(R.string.wir_view_collection_full)

    @Composable
    fun viewChapter(place: String): String =
        stringResource(R.string.wir_view_chapter, place)

    // ── Settings sheet (WP-R2) ─────────────────────────────────
    @Composable fun settingsTitle(): String = stringResource(R.string.settings_title)
    @Composable fun settingsAppearance(): String = stringResource(R.string.settings_appearance)
    @Composable fun settingsAppearanceSub(): String = stringResource(R.string.settings_appearance_sub)
    @Composable fun settingsTempUnitLabel(): String = stringResource(R.string.settings_temp_unit_label)
    @Composable fun settingsTempUnitSub(): String = stringResource(R.string.settings_temp_unit_sub)
    @Composable fun settingsTempUnitNote(): String = stringResource(R.string.settings_temp_unit_note)
    @Composable fun settingsBodyTemp(): String = stringResource(R.string.settings_body_temp)
    @Composable fun settingsBodyTempSub(): String = stringResource(R.string.settings_body_temp_sub)
    @Composable fun settingsFit(): String = stringResource(R.string.settings_fit)
    @Composable fun settingsFitSub(): String = stringResource(R.string.settings_fit_sub)
    @Composable fun settingsFeedbackSub(): String = stringResource(R.string.settings_feedback_sub)
    @Composable fun settingsRateSub(): String = stringResource(R.string.settings_rate_sub)
    @Composable fun settingsHelpLabel(): String = stringResource(R.string.settings_help_label)
    @Composable fun settingsHelpSub(): String = stringResource(R.string.settings_help_sub)
    @Composable fun settingsHelpBody(): String = stringResource(R.string.settings_help_body)
    @Composable fun settingsHelpOff(): String = stringResource(R.string.settings_help_off)
    @Composable fun settingsCrashBody(): String = stringResource(R.string.settings_crash_body)
    @Composable fun settingsAbout(): String = stringResource(R.string.settings_about)
    @Composable fun settingsVersion(): String = stringResource(R.string.settings_version)

    // ── Date + location sheets (WP-R2) ─────────────────────────
    @Composable fun sheetSelectDate(): String = stringResource(R.string.sheet_select_date)
    @Composable fun sheetSelectDateNote(): String = stringResource(R.string.sheet_select_date_note)
    @Composable fun sheetSetLocation(): String = stringResource(R.string.sheet_set_location)
    @Composable fun locHintTyping(): String = stringResource(R.string.loc_hint_typing)
    @Composable fun locOr(): String = stringResource(R.string.loc_or)
    @Composable fun locCurrentLocation(): String = stringResource(R.string.loc_current_location)
    @Composable fun locCurrentBadge(): String = stringResource(R.string.loc_current_badge)
    @Composable fun locRecentsHint(): String = stringResource(R.string.loc_recents_hint)
    @Composable fun locRecentsTitle(): String = stringResource(R.string.loc_recents_title)
    @Composable fun locNoResults(): String = stringResource(R.string.loc_no_results)
    @Composable fun locCurrentlyUsing(): String = stringResource(R.string.loc_currently_using)
    @Composable fun locSearching(query: String): String =
        stringResource(R.string.loc_searching, query)
    @Composable fun locRemoveRecent(name: String): String =
        stringResource(R.string.loc_remove_recent, name)

    // ── Shop + weather sheets (WP-R2) ──────────────────────────
    @Composable fun shopSheetTitle(): String = stringResource(R.string.shop_sheet_title)
    @Composable fun shopAllArrow(): String = stringResource(R.string.shop_all_arrow)
    @Composable fun shopRowDoor(): String = stringResource(R.string.shop_row_door)
    @Composable fun weatherRunningTip(): String = stringResource(R.string.weather_running_tip)
    @Composable fun weatherLabelFeelsLike(): String = stringResource(R.string.weather_label_feels_like)
    @Composable fun weatherLabelActual(): String = stringResource(R.string.weather_label_actual)
    @Composable fun weatherLabelCondition(): String = stringResource(R.string.weather_label_condition)
    @Composable fun weatherLabelWind(): String = stringResource(R.string.weather_label_wind)
    @Composable fun weatherLabelHumidity(): String = stringResource(R.string.weather_label_humidity)
    @Composable fun weatherLabelUv(): String = stringResource(R.string.weather_label_uv)
    @Composable fun weatherLabelPrecip(): String = stringResource(R.string.weather_label_precip)
    @Composable fun weatherPrecipValue(percent: Int): String =
        stringResource(R.string.weather_precip_value, percent)
}
