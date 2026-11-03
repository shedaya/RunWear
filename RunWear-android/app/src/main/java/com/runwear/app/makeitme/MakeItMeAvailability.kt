package com.runwear.app.makeitme

/**
 * The Make It Me availability gate for THIS build.
 *
 * ON in this flag-on TEST build only (branch
 * test/rw-mim-flag-on-android-2026-11-03, 1.2.16 / vc31, Play
 * internal testing): the server pipeline went live Nov 3, 2026
 * and Sol commissioned flag-on test builds to exercise it. The
 * phase-2 train and every store build keep the gate OFF (Sol's
 * ruling, Nov 2, 2026: "off for both submissions, til it
 * actually works"). The feature's code, its backend, and the
 * entitlement plumbing are all untouched — instead, every entry
 * point consults this gate:
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
 * [ENABLED], plus revisiting the build pin test: the train pins
 * the gate shut with MakeItMeOffBuildPinTest; this test branch
 * replaces it with MakeItMeOnBuildPinTest, pinning the gate open
 * so the flip is a deliberate, tested act here too.
 */
object MakeItMeAvailability {

    const val ENABLED: Boolean = true

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
