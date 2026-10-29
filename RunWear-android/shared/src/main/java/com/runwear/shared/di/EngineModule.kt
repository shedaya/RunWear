package com.runwear.shared.di

import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the outfit recommendation engine from the shared KMP `:engine`
 * module. The use case carries no DI annotations of its own — it is plain
 * common Kotlin consumed by the Apple platforms too — so its Hilt binding
 * lives here, on the Android side of the seam.
 */
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideGetOutfitRecommendationUseCase(): GetOutfitRecommendationUseCase =
        GetOutfitRecommendationUseCase()
}
