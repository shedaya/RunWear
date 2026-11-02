package com.runwear.app.makeitme

/**
 * The Make It Me availability gate for THIS build.
 *
 * OFF for the 1.2.12 production build (Sol's ruling, Nov 2, 2026:
 * "off for both submissions, til it actually works"). The
 * feature's code, its backend, and the entitlement plumbing are
 * all untouched — instead, every entry point consults this gate:
 *
 * - the hero resolution never engages the personal layer, so
 *   pool heroes only ([personalLayerEngages]);
 * - the repository serves no personal hero and refuses
 *   generation requests ([generationRequestAllowed]; cellLookFor
 *   is gated on [ENABLED] directly);
 * - no RevenueCat purchase can start ([purchaseAllowed]);
 * - the Settings "Make it me" section does not render
 *   ([settingsSectionVisible]);
 * - the onboarding fork never comes due and the flow / photos /
 *   paywall / personal-flag sheets never open (the hosts consult
 *   [ENABLED] at their render conditions).
 *
 * The feature's policy objects (cache, upsell rules, look
 * status) are NOT gated — they keep their ON semantics and their
 * tests keep asserting them. Re-enabling the feature is flipping
 * [ENABLED], plus revisiting MakeItMeOffBuildPinTest, which pins
 * the gate shut for this build.
 */
object MakeItMeAvailability {

    const val ENABLED: Boolean = false

    /**
     * The personal hero layer may engage only when the feature is
     * available AND the user has opted in (personalization on).
     */
    fun personalLayerEngages(personalizationOn: Boolean): Boolean =
        ENABLED && personalizationOn

    /** RevenueCat purchases may start only when the feature is available. */
    fun purchaseAllowed(): Boolean = ENABLED

    /** Personal-generation requests may fire only when the feature is available. */
    fun generationRequestAllowed(): Boolean = ENABLED

    /** The Settings "Make it me" section renders only when the feature is available. */
    fun settingsSectionVisible(): Boolean = ENABLED
}
