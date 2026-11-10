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
    const val ONB_BODY_SLIDER_MIN = "Runs Cold"
    const val ONB_BODY_SLIDER_MAX = "Runs Warm"
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
    fun onbBodySliderMin(): String = AppEnglish.ONB_BODY_SLIDER_MIN
    fun onbBodySliderMax(): String = AppEnglish.ONB_BODY_SLIDER_MAX
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
