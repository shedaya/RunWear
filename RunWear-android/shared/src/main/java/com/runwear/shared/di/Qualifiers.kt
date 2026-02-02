package com.runwear.shared.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WeatherRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeocodingRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NominatimRetrofit
