package com.runwear.shared.domain.model

import com.runwear.engine.MR
import dev.icerock.moko.resources.desc.StringDesc

/**
 * LOC-1 WP-3 — the catalog split, DISPLAY representation.
 *
 * Every catalog concept carries three representations that must never be
 * conflated again (audit §5.2, Oct risk #1):
 *
 *  - DISPLAY (this file): user-facing names/descriptions. LOCALIZABLE —
 *    backed by moko resources in :engine (`moko-resources/base/strings.xml`),
 *    values byte-identical to the pre-split enum strings.
 *  - PROMPT: the image-generation wording. Stays ENGLISH, frozen. On the
 *    current architecture it lives server-side in `personal-hero-worker`,
 *    keyed by the item's stable identity ([ClothingItem.key]) — never by
 *    display copy. Client code must not derive prompt text from
 *    [ClothingItem.displayName] (the Oct-era path the split retires).
 *  - SEARCH: retailer search terms ([ClothingItem.amazonSearchTerm]).
 *    Market-dependent, NOT translated 1:1; English for now (see the WP-3
 *    design doc's affiliate-per-market recommendation).
 *
 * Why the enum still carries `displayName`/`description` literals in the
 * pilot: `displayName` is (today) also a *persistence key* — Wear I've Run
 * entries store display names and `clothingItemForDisplayName` reverse-maps
 * them. Rewiring the enum onto these descriptors is therefore a data
 * migration (persist `key`, not the name), scheduled as its own slice in
 * the WP-3 design doc; this accessor is the surface that migration lands
 * on, and new UI should read display copy from here.
 */
val ClothingItem.displayNameDesc: StringDesc
    get() = StringDesc.Resource(
        when (this) {
            ClothingItem.TANK_TOP -> MR.strings.clothing_tank_top_name
            ClothingItem.SHORT_SLEEVE -> MR.strings.clothing_short_sleeve_name
            ClothingItem.LONG_SLEEVE_LIGHT -> MR.strings.clothing_long_sleeve_light_name
            ClothingItem.LONG_SLEEVE_THERMAL -> MR.strings.clothing_long_sleeve_thermal_name
            ClothingItem.QUARTER_ZIP -> MR.strings.clothing_quarter_zip_name
            ClothingItem.FLEECE_PULLOVER -> MR.strings.clothing_fleece_pullover_name
            ClothingItem.HALF_ZIP_THERMAL -> MR.strings.clothing_half_zip_thermal_name
            ClothingItem.LIGHT_VEST -> MR.strings.clothing_light_vest_name
            ClothingItem.WINDBREAKER -> MR.strings.clothing_windbreaker_name
            ClothingItem.LIGHT_JACKET -> MR.strings.clothing_light_jacket_name
            ClothingItem.RAIN_JACKET -> MR.strings.clothing_rain_jacket_name
            ClothingItem.INSULATED_JACKET -> MR.strings.clothing_insulated_jacket_name
            ClothingItem.SHORT_SHORTS -> MR.strings.clothing_short_shorts_name
            ClothingItem.SHORTS -> MR.strings.clothing_shorts_name
            ClothingItem.CAPRIS -> MR.strings.clothing_capris_name
            ClothingItem.LIGHT_TIGHTS -> MR.strings.clothing_light_tights_name
            ClothingItem.THERMAL_TIGHTS -> MR.strings.clothing_thermal_tights_name
            ClothingItem.VISOR -> MR.strings.clothing_visor_name
            ClothingItem.BASEBALL_CAP -> MR.strings.clothing_baseball_cap_name
            ClothingItem.HEADBAND -> MR.strings.clothing_headband_name
            ClothingItem.LIGHT_BEANIE -> MR.strings.clothing_light_beanie_name
            ClothingItem.THERMAL_BEANIE -> MR.strings.clothing_thermal_beanie_name
            ClothingItem.BALACLAVA -> MR.strings.clothing_balaclava_name
            ClothingItem.LIGHT_GLOVES -> MR.strings.clothing_light_gloves_name
            ClothingItem.THERMAL_GLOVES -> MR.strings.clothing_thermal_gloves_name
            ClothingItem.MITTENS -> MR.strings.clothing_mittens_name
            ClothingItem.SUNGLASSES -> MR.strings.clothing_sunglasses_name
            ClothingItem.SUNSCREEN -> MR.strings.clothing_sunscreen_name
            ClothingItem.REFLECTIVE_GEAR -> MR.strings.clothing_reflective_gear_name
            ClothingItem.NECK_GAITER -> MR.strings.clothing_neck_gaiter_name
        }
    )

val ClothingItem.descriptionDesc: StringDesc
    get() = StringDesc.Resource(
        when (this) {
            ClothingItem.TANK_TOP -> MR.strings.clothing_tank_top_desc
            ClothingItem.SHORT_SLEEVE -> MR.strings.clothing_short_sleeve_desc
            ClothingItem.LONG_SLEEVE_LIGHT -> MR.strings.clothing_long_sleeve_light_desc
            ClothingItem.LONG_SLEEVE_THERMAL -> MR.strings.clothing_long_sleeve_thermal_desc
            ClothingItem.QUARTER_ZIP -> MR.strings.clothing_quarter_zip_desc
            ClothingItem.FLEECE_PULLOVER -> MR.strings.clothing_fleece_pullover_desc
            ClothingItem.HALF_ZIP_THERMAL -> MR.strings.clothing_half_zip_thermal_desc
            ClothingItem.LIGHT_VEST -> MR.strings.clothing_light_vest_desc
            ClothingItem.WINDBREAKER -> MR.strings.clothing_windbreaker_desc
            ClothingItem.LIGHT_JACKET -> MR.strings.clothing_light_jacket_desc
            ClothingItem.RAIN_JACKET -> MR.strings.clothing_rain_jacket_desc
            ClothingItem.INSULATED_JACKET -> MR.strings.clothing_insulated_jacket_desc
            ClothingItem.SHORT_SHORTS -> MR.strings.clothing_short_shorts_desc
            ClothingItem.SHORTS -> MR.strings.clothing_shorts_desc
            ClothingItem.CAPRIS -> MR.strings.clothing_capris_desc
            ClothingItem.LIGHT_TIGHTS -> MR.strings.clothing_light_tights_desc
            ClothingItem.THERMAL_TIGHTS -> MR.strings.clothing_thermal_tights_desc
            ClothingItem.VISOR -> MR.strings.clothing_visor_desc
            ClothingItem.BASEBALL_CAP -> MR.strings.clothing_baseball_cap_desc
            ClothingItem.HEADBAND -> MR.strings.clothing_headband_desc
            ClothingItem.LIGHT_BEANIE -> MR.strings.clothing_light_beanie_desc
            ClothingItem.THERMAL_BEANIE -> MR.strings.clothing_thermal_beanie_desc
            ClothingItem.BALACLAVA -> MR.strings.clothing_balaclava_desc
            ClothingItem.LIGHT_GLOVES -> MR.strings.clothing_light_gloves_desc
            ClothingItem.THERMAL_GLOVES -> MR.strings.clothing_thermal_gloves_desc
            ClothingItem.MITTENS -> MR.strings.clothing_mittens_desc
            ClothingItem.SUNGLASSES -> MR.strings.clothing_sunglasses_desc
            ClothingItem.SUNSCREEN -> MR.strings.clothing_sunscreen_desc
            ClothingItem.REFLECTIVE_GEAR -> MR.strings.clothing_reflective_gear_desc
            ClothingItem.NECK_GAITER -> MR.strings.clothing_neck_gaiter_desc
        }
    )

val WeatherCode.descriptionDesc: StringDesc
    get() = StringDesc.Resource(
        when (this) {
            WeatherCode.CLEAR_SKY -> MR.strings.weather_clear_sky_desc
            WeatherCode.MAINLY_CLEAR -> MR.strings.weather_mainly_clear_desc
            WeatherCode.PARTLY_CLOUDY -> MR.strings.weather_partly_cloudy_desc
            WeatherCode.OVERCAST -> MR.strings.weather_overcast_desc
            WeatherCode.FOG -> MR.strings.weather_fog_desc
            WeatherCode.DEPOSITING_RIME_FOG -> MR.strings.weather_depositing_rime_fog_desc
            WeatherCode.DRIZZLE_LIGHT -> MR.strings.weather_drizzle_light_desc
            WeatherCode.DRIZZLE_MODERATE -> MR.strings.weather_drizzle_moderate_desc
            WeatherCode.DRIZZLE_DENSE -> MR.strings.weather_drizzle_dense_desc
            WeatherCode.FREEZING_DRIZZLE_LIGHT -> MR.strings.weather_freezing_drizzle_light_desc
            WeatherCode.FREEZING_DRIZZLE_DENSE -> MR.strings.weather_freezing_drizzle_dense_desc
            WeatherCode.RAIN_SLIGHT -> MR.strings.weather_rain_slight_desc
            WeatherCode.RAIN_MODERATE -> MR.strings.weather_rain_moderate_desc
            WeatherCode.RAIN_HEAVY -> MR.strings.weather_rain_heavy_desc
            WeatherCode.FREEZING_RAIN_LIGHT -> MR.strings.weather_freezing_rain_light_desc
            WeatherCode.FREEZING_RAIN_HEAVY -> MR.strings.weather_freezing_rain_heavy_desc
            WeatherCode.SNOW_SLIGHT -> MR.strings.weather_snow_slight_desc
            WeatherCode.SNOW_MODERATE -> MR.strings.weather_snow_moderate_desc
            WeatherCode.SNOW_HEAVY -> MR.strings.weather_snow_heavy_desc
            WeatherCode.SNOW_GRAINS -> MR.strings.weather_snow_grains_desc
            WeatherCode.RAIN_SHOWERS_SLIGHT -> MR.strings.weather_rain_showers_slight_desc
            WeatherCode.RAIN_SHOWERS_MODERATE -> MR.strings.weather_rain_showers_moderate_desc
            WeatherCode.RAIN_SHOWERS_VIOLENT -> MR.strings.weather_rain_showers_violent_desc
            WeatherCode.SNOW_SHOWERS_SLIGHT -> MR.strings.weather_snow_showers_slight_desc
            WeatherCode.SNOW_SHOWERS_HEAVY -> MR.strings.weather_snow_showers_heavy_desc
            WeatherCode.THUNDERSTORM -> MR.strings.weather_thunderstorm_desc
            WeatherCode.THUNDERSTORM_HAIL_SLIGHT -> MR.strings.weather_thunderstorm_hail_slight_desc
            WeatherCode.THUNDERSTORM_HAIL_HEAVY -> MR.strings.weather_thunderstorm_hail_heavy_desc
            WeatherCode.UNKNOWN -> MR.strings.weather_unknown_desc
        }
    )

val ComfortPreference.labelDesc: StringDesc
    get() = StringDesc.Resource(
        when (this) {
            ComfortPreference.RUNS_VERY_COLD -> MR.strings.comfort_runs_very_cold_label
            ComfortPreference.RUNS_COLD -> MR.strings.comfort_runs_cold_label
            ComfortPreference.NEUTRAL -> MR.strings.comfort_neutral_label
            ComfortPreference.RUNS_WARM -> MR.strings.comfort_runs_warm_label
            ComfortPreference.RUNS_VERY_WARM -> MR.strings.comfort_runs_very_warm_label
        }
    )
