package com.runwear.wear

import com.runwear.wear.presentation.screens.HeroWearMainScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * FOLLOWING-BUILD VQ (Nov 6, 2026) — harness-only Wear renders by the
 * independent Visual QA executor on candidate 63015bc, filling the
 * VQ-6.6 / VQ-2.6 matrix cells the compliance set does not produce:
 * the large face at the DEFAULT font scale, a cold hero on the small
 * face at default scale (the gen-6 graze-state layout), and a
 * long-place hero on the small face at the largest font scale.
 * All captures are round-masked by the compliance harness and land
 * in build/wear-compliance/. NO product code is touched.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w228dp-h228dp-xhdpi")
class FollowingVqWearLargeDefaultHeroTest : WearComplianceHarness() {
    @Test fun large_default_hero() {
        shootCompliance("large-round_font1.0_hero", 456, 1.0f) {
            HeroWearMainScreen(
                uiState = stateFor(mildWeather, "Overland Park, Kansas"),
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class FollowingVqWearSmallDefaultColdHeroTest : WearComplianceHarness() {
    @Test fun small_default_cold_hero() {
        shootCompliance("small-round_font1.0_hero-cold", 384, 1.0f) {
            HeroWearMainScreen(
                uiState = stateFor(coldWeather, "Overland Park, Kansas"),
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class FollowingVqWearSmallMaxLongPlaceHeroTest : WearComplianceHarness() {
    @Test fun small_maxfont_longplace_hero() {
        shootCompliance("small-round_font1.3_hero-longplace", 384, 1.30f) {
            HeroWearMainScreen(
                uiState = stateFor(mildWeather, "Rancho Santa Margarita, California"),
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}
