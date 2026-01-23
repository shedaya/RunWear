package com.runwear.shared.domain.model

enum class ClothingCategory {
    TOP_BASE, TOP_OUTER, BOTTOM, HEAD, HANDS, ACCESSORIES
}

enum class ClothingItem(
    val category: ClothingCategory,
    val displayName: String,
    val description: String,
    val icon: String,
    val amazonSearchTerm: String
) {
    // TOP BASE LAYERS
    TANK_TOP(ClothingCategory.TOP_BASE, "Tank Top / Singlet", "Lightweight, breathable, maximum airflow", "👕", "running tank top moisture wicking"),
    SHORT_SLEEVE(ClothingCategory.TOP_BASE, "Short Sleeve Shirt", "Moisture-wicking technical fabric", "👕", "running short sleeve shirt dri-fit"),
    LONG_SLEEVE_LIGHT(ClothingCategory.TOP_BASE, "Light Long Sleeve", "Thin, breathable long sleeve", "👕", "running long sleeve lightweight"),
    LONG_SLEEVE_THERMAL(ClothingCategory.TOP_BASE, "Thermal Long Sleeve", "Insulated base layer for cold", "🧥", "running thermal base layer"),
    
    // TOP OUTER LAYERS
    LIGHT_VEST(ClothingCategory.TOP_OUTER, "Light Vest", "Wind protection without overheating", "🦺", "running vest lightweight"),
    WINDBREAKER(ClothingCategory.TOP_OUTER, "Windbreaker", "Lightweight wind and light rain protection", "🧥", "running windbreaker jacket"),
    LIGHT_JACKET(ClothingCategory.TOP_OUTER, "Light Running Jacket", "Breathable jacket for cool temps", "🧥", "running jacket lightweight"),
    RAIN_JACKET(ClothingCategory.TOP_OUTER, "Rain Jacket", "Waterproof, breathable shell", "🧥", "running rain jacket waterproof"),
    INSULATED_JACKET(ClothingCategory.TOP_OUTER, "Insulated Jacket", "Warm jacket for cold conditions", "🧥", "running winter jacket insulated"),
    
    // BOTTOMS
    SHORT_SHORTS(ClothingCategory.BOTTOM, "Short Shorts (3\")", "Maximum breathability", "🩳", "running shorts 3 inch"),
    SHORTS(ClothingCategory.BOTTOM, "Running Shorts (5-7\")", "Standard running shorts", "🩳", "running shorts 5 inch"),
    CAPRIS(ClothingCategory.BOTTOM, "Capris / 3/4 Tights", "Mid-length coverage", "👖", "running capris tights"),
    LIGHT_TIGHTS(ClothingCategory.BOTTOM, "Light Tights", "Full leg coverage, breathable", "👖", "running tights lightweight"),
    THERMAL_TIGHTS(ClothingCategory.BOTTOM, "Thermal Tights", "Insulated for cold weather", "👖", "running tights thermal winter"),
    
    // HEAD
    VISOR(ClothingCategory.HEAD, "Visor", "Sun protection, max ventilation", "🧢", "running visor"),
    BASEBALL_CAP(ClothingCategory.HEAD, "Running Cap", "Sun and light rain protection", "🧢", "running cap lightweight"),
    HEADBAND(ClothingCategory.HEAD, "Ear Warmer / Headband", "Keeps ears warm", "🎧", "running ear warmer headband"),
    LIGHT_BEANIE(ClothingCategory.HEAD, "Light Beanie", "Thin beanie for moderate cold", "🧢", "running beanie lightweight"),
    THERMAL_BEANIE(ClothingCategory.HEAD, "Thermal Beanie", "Warm hat for very cold weather", "🧢", "running beanie thermal winter"),
    BALACLAVA(ClothingCategory.HEAD, "Balaclava / Face Mask", "Full face protection", "🎭", "running balaclava face mask"),
    
    // HANDS
    LIGHT_GLOVES(ClothingCategory.HANDS, "Light Gloves", "Thin running gloves", "🧤", "running gloves lightweight touchscreen"),
    THERMAL_GLOVES(ClothingCategory.HANDS, "Thermal Gloves", "Insulated gloves for cold", "🧤", "running gloves thermal winter"),
    MITTENS(ClothingCategory.HANDS, "Mittens", "Maximum warmth for extreme cold", "🧤", "running mittens warm"),
    
    // ACCESSORIES
    SUNGLASSES(ClothingCategory.ACCESSORIES, "Sunglasses", "Eye protection from sun and wind", "🕶️", "running sunglasses sport"),
    SUNSCREEN(ClothingCategory.ACCESSORIES, "Sunscreen (SPF 30+)", "Protect exposed skin", "🧴", "sport sunscreen spf 50"),
    REFLECTIVE_GEAR(ClothingCategory.ACCESSORIES, "Reflective Gear", "Visibility for low-light", "🦺", "running reflective vest"),
    NECK_GAITER(ClothingCategory.ACCESSORIES, "Neck Gaiter / Buff", "Versatile neck and face protection", "🧣", "running neck gaiter buff")
}

data class OutfitRecommendation(
    val weather: WeatherConditions,
    val topBase: ClothingItem,
    val topOuter: ClothingItem?,
    val bottom: ClothingItem,
    val head: ClothingItem?,
    val hands: ClothingItem?,
    val accessories: List<ClothingItem>,
    val tips: List<String>
) {
    val allItems: List<ClothingItem>
        get() = listOfNotNull(topBase, topOuter, bottom, head, hands) + accessories
    
    val summary: String
        get() {
            val tempStr = if (weather.isCelsius) "${weather.effectiveTemperature.toInt()}°C"
                          else "${weather.effectiveTemperature.toInt()}°F"
            return "Feels like $tempStr — ${weather.weatherCode.description}"
        }
}
