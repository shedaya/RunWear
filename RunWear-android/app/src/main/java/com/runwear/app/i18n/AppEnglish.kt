package com.runwear.app.i18n

/**
 * LOC-1 WP-R2 — pure-code accessors over :app Android resources.
 *
 * Non-Compose code (state objects, pure formatters, ViewModel state)
 * cannot call `stringResource`; it reads the shipped English values
 * from [AppEnglish], the WP-3 two-step facade: the Android resources
 * in `res/values/strings.xml` are canonical, this table holds the
 * same values, and AndroidResourceParityTest pins them byte-for-byte
 * so the two can never drift. The facade retires when the pure-code
 * layer moves to a resolved-resource seam (same recorded posture as
 * the WP-3 pilot's policy mirrors).
 */
object AppEnglish {
    // Onboarding deck
    const val ONB_HEADLINE = "Run, weather or not."
    const val ONB_PROMISE_1_HEADLINE = "The right outfit for the weather"
    const val ONB_PROMISE_1_PHRASE =
        "One recommendation for the run you're about to take — feels-like, wind, and rain included."
    const val ONB_PROMISE_2_HEADLINE = "See yourself in every outfit"
    const val ONB_PROMISE_2_PHRASE =
        "Picture yourself at the start line with the perfect outfit ready to go."
    const val ONB_PROMISE_3_HEADLINE = "Remember every run"
    const val ONB_PROMISE_3_PHRASE =
        "Turn each day's outfit into a card you can share — a scrapbook of what you wore and where you ran."
    const val ONB_HEADLINE_LINE_TEMPLATE = "%1\$s — %2\$s"
    const val ONB_PROMISE_CTA = "Get Started"
    const val ONB_PROMISE_FOOTER = "Free to start · Set up in under a minute"
    const val ONB_FREE_LOOKS_PROMISE_TEMPLATE = "Your first %1\$d looks are free."
    const val ONB_SETUP_STEP_LABEL = "Step 1 of 2"
    const val ONB_SETUP_TITLE = "Make it yours"
    const val ONB_SETUP_SUB = "Three quick calls. You can change any of them later in Settings."
    const val ONB_TEMP_TITLE = "Temperature"
    const val ONB_TEMP_SUB = "How do you measure the weather?"
    const val ONB_FIT_TITLE = "Fit preference"
    const val ONB_FIT_SUB = "Whose fit should the outfits follow?"
    const val ONB_BODY_TITLE = "Body temperature"
    const val ONB_BODY_SUB = "Do you get cold easily, or overheat quickly?"
    const val ONB_TEMP_OPTION_FAHRENHEIT = "Fahrenheit"
    const val ONB_TEMP_OPTION_CELSIUS = "Celsius"
    const val ONB_FIT_OPTION_MENS = "Men's"
    const val ONB_FIT_OPTION_WOMENS = "Women's"
    const val ONB_BODY_SLIDER_MIN_LABEL = "Runs Cold"
    const val ONB_BODY_SLIDER_MAX_LABEL = "Runs Warm"
    const val ONB_SETUP_CTA = "Continue"
    const val ONB_LOCATION_STEP_LABEL = "Step 2 of 2"
    const val ONB_LOCATION_TITLE = "One more thing — where are you running?"
    const val ONB_LOCATION_BODY = "We need your location to get accurate weather for your run"
    const val ONB_LOCATION_CTA_PRIMARY = "Use My Location"
    const val ONB_LOCATION_CTA_SECONDARY = "Set a City Instead"
    // Looks library
    const val LOOKS_TITLE = "Your looks"
    const val LOOKS_WHY_DOOR = "Why am I seeing a stock photo?"
    const val LOOKS_EXPLANATION =
        "One scenario didn't pass the quality gate after every attempt, so it shows the stock photo rather than a version of you that isn't right. It retries on its own when generation improves."
    const val LOOKS_EMPTY_LINE = "No looks yet — your first look is on its way."
    // Permission
    const val PERMISSION_LOCATION_PRIVACY =
        "Your location fetches your weather and names your place. " +
            "Your most recent location is saved on this phone so RunWear " +
            "reopens where you left off. Weather lookups send your " +
            "coordinates, rounded to a rough area, to Open-Meteo; " +
            "place-name lookups send them to OpenStreetMap (Nominatim). " +
            "Your location is never sold or used for advertising."
    // Feedback categories (display labels; ids stay internal)
    const val FEEDBACK_CAT_SOMETHING_WRONG = "Something's wrong"
    const val FEEDBACK_CAT_RECOMMENDATION_OFF = "The recommendation seems off"
    const val FEEDBACK_CAT_CONFUSING = "Confusing"
    const val FEEDBACK_CAT_IDEA = "Idea"
    const val FEEDBACK_CAT_OTHER = "Other"
    // Copy objects (OutfitHeaderCopy / HomeLoadingCopy / WeatherSheetCopy)
    const val OUTFIT_HEADER_ITEMS_TEMPLATE = "%1\$d items for your run"
    const val LOADING_FIRST_RECOMMENDATION = "Getting your first recommendation…"
    const val LOADING_GETTING_YOUR_LOCATION = "Getting your location…"
    const val LOADING_GETTING_LOCATION_STATE = "Getting location..."
    const val LOADING_FINDING_LOCATION = "Finding your location…"
    const val LOADING_FORECAST = "Loading your forecast…"
    const val WEATHER_TIP_OVERCAST =
        "Overcast and cool — a light layer you can shed beats one you can't."
    const val WEATHER_TIP_RAIN =
        "Rain is likely — a light shell you can shed beats one you can't."
    const val WEATHER_TIP_WARM =
        "Warm out — start lighter than feels right; you'll heat up fast."
    const val WEATHER_TIP_COLD =
        "Cold out — cover ears and hands first; they chill fastest."
    const val WEATHER_TIP_DEFAULT = "A light layer you can shed beats one you can't."
    const val HERO_FEELS_LIKE_LABEL = "Feels like"
    // Sheets + location state
    const val DAY_TODAY = "Today"
    const val DAY_TOMORROW = "Tomorrow"
    const val LOC_GPS_ENABLED = "GPS location enabled"
    const val LOC_REQUIRES_PERMISSION = "Requires location permission"
    const val LOC_FAIL_PERMISSION =
        "Location permission is off. Turn it on to use your current location."
    const val LOC_FAIL_TIMEOUT = "We couldn't get a fix on your location. Try again in a moment."
    const val LOC_FAIL_UNAVAILABLE =
        "Your current location isn't available right now. Try again in a moment."
    const val LOC_FINDING = "Finding your location…"
    const val CAT_BASE_LAYER = "Base Layer"
    const val CAT_MID_LAYER = "Mid Layer"
    const val CAT_OUTER_LAYER = "Outer Layer"
    const val CAT_BOTTOM = "Bottom"
    const val CAT_HEADWEAR = "Headwear"
    const val CAT_GLOVES = "Gloves"
    const val CAT_ACCESSORIES = "Accessories"
    // ViewModel / state strings
    const val VM_YOUR_LOCATION = "Your Location"
    const val VM_WEATHER_ERROR = "Weather error"
    const val VM_CURRENT_LOCATION_TITLE = "Current location"
    const val LAYER_BASE = "Base layer"
    const val LAYER_MID = "Mid layer"
    const val LAYER_OUTER = "Outer layer"
    const val LAYER_BOTTOM = "Bottom"
    const val LAYER_HEAD = "Head"
    const val LAYER_HANDS = "Hands"
    // Wear I've Run narration templates (WP-R2, DESIGN 6)
    const val WIR_PLACE_MOMENT = "%1\$s, %2\$s"
    const val WIR_MOMENT_DATED = "%1\$s %2\$s"
    const val WIR_NOW = "Now"
    const val WIR_SUBTITLE_DETAIL = "%1\$s feels like · %2\$s"
    const val WIR_SUBTITLE_JOIN = " · "
    const val WIR_UPDATED_TO = "Updated to "
    const val WIR_ADDED = "Added "
    const val WIR_ADDED_PAYLOAD = "%1\$s, %2\$s"
    const val WIR_PLANNED_FOR = "Planned for "
    const val WIR_PLAN_SUFFIX_DAY = " · %1\$s"
    const val WIR_PLAN_SUFFIX_MOMENT = " · %1\$s"
    const val WIR_UPDATED_TODAY_PAYLOAD = "%1\$s, %2\$s"
    const val WIR_ENTRY_LINE_HEAD = "%1\$s · %2\$s"
    const val WIR_ENTRY_LINE_MOMENT = " · %1\$s"
    const val WIR_ENTRY_LINE_TAIL = " — %1\$s feels like"
    const val WIR_WORDMARK = "Wear I've Run"
    const val WIR_DAY_TODAY = "Today"
    const val WIR_DAY_TOMORROW = "Tomorrow"
    const val WIR_SUMMARY_MULTI = "%1\$s · %2\$s · Last stay · %3\$s"
    const val WIR_SUMMARY_SINGLE = "%1\$s · %2\$s"
    const val WIR_SUMMARY_RANGE_SUFFIX = " · %1\$s"
    const val WIR_FEELS_RANGE = "%1\$d°–%2\$d°"
    const val WIR_FEELS_SINGLE = "%1\$d°"
    const val WIR_SPAN_SAME_MONTH = "%1\$s–%2\$d"
    const val WIR_SPAN_CROSS = "%1\$s – %2\$s"
    const val WIR_LAST_SAVED_JUST_NOW = "Last saved just now"
    const val WIR_LAST_SAVED_DAY = "Last saved %1\$s"
    const val WIR_ITEMS_COUNT_ONE = "%1\$d item"
    const val WIR_ITEMS_COUNT_OTHER = "%1\$d items"
    const val WIR_DAYS_COUNT_ONE = "%1\$d day"
    const val WIR_DAYS_COUNT_OTHER = "%1\$d days"
    const val WIR_STAYS_COUNT_ONE = "%1\$d stay"
    const val WIR_STAYS_COUNT_OTHER = "%1\$d stays"
    const val WIR_LAST_SAVED_MINUTES_ONE = "Last saved %1\$d min ago"
    const val WIR_LAST_SAVED_MINUTES_OTHER = "Last saved %1\$d min ago"
    const val WIR_LAST_SAVED_HOURS_ONE = "Last saved %1\$d hr ago"
    const val WIR_LAST_SAVED_HOURS_OTHER = "Last saved %1\$d hr ago"
    const val WIR_MONTH_COUNT_SUFFIX = " · %1\$s"
    const val COLLECTION_DAY_TITLE_TODAY = "Today · %1\$s"
    const val WIR_YEAR_COUNT = "%1\$d days"

    /**
     * English CLDR plural selection (one/other) over the narration
     * count templates — the pure-code mirror of the Android plurals
     * in `res/values/plurals.xml`. A future locale's forms arrive as
     * its own plurals file; this selector covers the shipped English.
     */
    fun plural(count: Int, one: String, other: String): String =
        (if (count == 1) one else other).format(count)
    // Share studio (WP-R2)
    const val SHARE_FORMAT_FEED = "Feed 4:5"
    const val SHARE_FORMAT_STORIES = "Stories 9:16"
    const val SHARE_FORMAT_SQUARE = "Square"
    const val SHARE_MERIDIEM_AM = "AM"
    const val SHARE_MERIDIEM_PM = "PM"
    const val SHARE_CLOCK_CAPTION = "today, %1\$s"
    const val SHARE_CLOCK_POSTER = "Today, %1\$s"
    const val SHARE_CLOCK_TIME = "%1\$d:00 %2\$s"
    const val SHARE_TOMORROW_CAPTION = "tomorrow %1\$s"
    const val SHARE_TOMORROW_POSTER = "Tomorrow %1\$s"
    const val SHARE_DAYPART_CAPTION = "%1\$s %2\$s"
    const val SHARE_DAYPART_POSTER = "%1\$s %2\$s"
    const val SHARE_MORNING_CAPTION = "this morning"
    const val SHARE_MORNING_POSTER = "This morning"
    const val SHARE_AFTERNOON_CAPTION = "this afternoon"
    const val SHARE_AFTERNOON_POSTER = "This afternoon"
    const val SHARE_TONIGHT_CAPTION = "tonight"
    const val SHARE_TONIGHT_POSTER = "Tonight"
    const val SHARE_TITLE_FEELS_STORY = "Feels-Like Story"
    const val SHARE_TITLE_SWING = "Swing"
    const val SHARE_TITLE_SCRAPBOOK = "Scrapbook"
    const val SHARE_TITLE_SHORT_CALL = "Short Call"
    const val SHARE_KIT_BULLET = "• %1\$s"
    const val SHARE_WHY_SWING = "One outfit for a %1\$d-degree swing."
    const val SHARE_WHY_COLD = "No guesswork at %1\$d°."
    const val SHARE_WHY_ANCHOR = "Dressed for the feels-like, not the thermometer."
    const val SHARE_CAPTION_FEELS_STORY =
        "%1\$s, %2\$s: %3\$d° on the thermometer, %4\$d° on the run. RunWear's call:\n%5\$s\n%6\$s #RunAnyWear · runwear.ai"
    const val SHARE_CAPTION_SWING =
        "%1\$s today: %2\$d° to %3\$d°. Dressed for %4\$s's %5\$d°:\n%6\$s\nOne outfit, no mid-run regret. #RunAnyWear · runwear.ai"
    const val SHARE_CAPTION_SCRAPBOOK =
        "One for the scrapbook: %1\$s, %2\$s, %3\$d° feels like, %4\$s. The kit:\n%5\$s\nEvery run, remembered — run, weather or not. #RunAnyWear · @runwear.ai"
    const val SHARE_CAPTION_SHORT_CALL =
        "%1\$s, %2\$s: %3\$d° feels like, %4\$s. The call:\n%5\$s\n#RunAnyWear · @runwear.ai"
    const val SHARE_SUBJECT = "RunWear — %1\$s, %2\$d° feels like"
    const val SHARE_LINK_TITLE = "RunWear — %1\$s · %2\$d° feels like"
    const val SHARE_WEATHER_LINE = "%1\$d° feels like · %2\$s · %3\$s · %4\$s."
    const val SHARE_DISCLOSURE = "As an Amazon Associate, RunWear earns from qualifying purchases."
    // Widget copy (WP-R2)
    const val WIDGET_LOADING = "Loading…"
    const val WIDGET_LOADING_OUTFIT = "Loading your outfit…"
    const val WIDGET_LOADING_STRIP = "RunWear · Loading…"
    const val WIDGET_LOCATION_NEEDED = "Location needed"
    const val WIDGET_TAP_TO_ENABLE = "Tap to enable"
    const val WIDGET_LOCATION_STRIP = "Location needed · Tap to enable"
    const val WIDGET_TAP_ENABLE_LOCATION = "Tap to enable location"
    const val WIDGET_FEELS_LIKE_CAPS = "FEELS LIKE"
    const val WIDGET_ACTUAL_TEMPLATE = "Actual %1\$s"
    const val WIDGET_FEELS_LIKE_TEMPLATE = "Feels like %1\$s"
    const val WIDGET_WIND_TEMPLATE = "Wind %1\$s"
    const val WIDGET_COULDNT_LOAD = "Couldn't load weather"
    const val WIDGET_COULDNT_REACH = "Couldn't reach the weather service"
    const val WIDGET_LOCATION_UNAVAILABLE = "Location isn't available to the widget"
    const val WIDGET_REFRESH = "Refresh"
    const val WIDGET_RETRY = "Retry"
    const val WIDGET_LATER_TODAY = "Later today"
    const val WIDGET_LATER_TODAY_RANGE = "Later today: %1\$s"
    const val WIDGET_BY_LINE = "By %1\$s: %2\$s"
    const val WIDGET_FOR_NOW = "FOR NOW"
    const val WIDGET_FOR_NOW_AGE = "FOR NOW · %1\$s"
    const val WIDGET_JUST_NOW = "JUST NOW"
    const val WIDGET_UPDATED = "Updated %1\$s"
    const val WIDGET_AGE_MIN_UPPER = "%1\$dM AGO"
    const val WIDGET_AGE_HR_UPPER = "%1\$dH AGO"
    const val WIDGET_AGE_DAY_UPPER = "%1\$dD AGO"
    const val WIDGET_AGE_JUST_NOW_LOWER = "just now"
    const val WIDGET_AGE_MIN_LOWER = "%1\$d min ago"
    const val WIDGET_AGE_HR_LOWER = "%1\$dh ago"
    const val WIDGET_AGE_DAY_LOWER = "%1\$dd ago"
    const val WIDGET_BRAND_LOADING = "RunWear · %1\$s"
    // Hero alt text
    const val HERO_ALT_DEFAULT = "Runner in running gear"
    const val HERO_ALT_TEMPLATE = "Runner dressed for %1\$s %2\$s conditions with %3\$d items of gear"
    const val HERO_ALT_TEMP_COLD = "cold"
    const val HERO_ALT_TEMP_MILD = "mild"
    const val HERO_ALT_TEMP_WARM = "warm"
}

/** Thin call-site accessors over [AppEnglish] (keeps read sites terse). */
object AppStringsPure {
    fun onbTempOptionFahrenheit(): String = AppEnglish.ONB_TEMP_OPTION_FAHRENHEIT
    fun onbTempOptionCelsius(): String = AppEnglish.ONB_TEMP_OPTION_CELSIUS
    fun onbBodySliderMin(): String = AppEnglish.ONB_BODY_SLIDER_MIN_LABEL
    fun onbBodySliderMax(): String = AppEnglish.ONB_BODY_SLIDER_MAX_LABEL
    fun dayToday(): String = AppEnglish.DAY_TODAY
    fun dayTomorrow(): String = AppEnglish.DAY_TOMORROW
    fun locGpsEnabled(): String = AppEnglish.LOC_GPS_ENABLED
    fun locRequiresPermission(): String = AppEnglish.LOC_REQUIRES_PERMISSION
    fun locFailPermission(): String = AppEnglish.LOC_FAIL_PERMISSION
    fun locFailTimeout(): String = AppEnglish.LOC_FAIL_TIMEOUT
    fun locFailUnavailable(): String = AppEnglish.LOC_FAIL_UNAVAILABLE
    fun locFinding(): String = AppEnglish.LOC_FINDING
    fun catBaseLayer(): String = AppEnglish.CAT_BASE_LAYER
    fun catMidLayer(): String = AppEnglish.CAT_MID_LAYER
    fun catOuterLayer(): String = AppEnglish.CAT_OUTER_LAYER
    fun catBottom(): String = AppEnglish.CAT_BOTTOM
    fun catHeadwear(): String = AppEnglish.CAT_HEADWEAR
    fun catGloves(): String = AppEnglish.CAT_GLOVES
    fun catAccessories(): String = AppEnglish.CAT_ACCESSORIES
}
