package com.runwear.shared.domain

import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase

/**
 * Engine identity surface for the Apple platforms. A Kotlin `object`'s
 * members export to Swift through its shared instance, which makes this
 * the reliable way to read the engine version from the shared module
 * (the Swift feedback bundle names the rules that produced an outfit,
 * exactly like Android's). The value itself stays single-sourced on the
 * use case's ENGINE_VERSION.
 */
object RunWearEngineInfo {
    const val ENGINE_VERSION: String = GetOutfitRecommendationUseCase.ENGINE_VERSION
}
