package com.runwear.app.ui.screens

/**
 * RW-146 proof frames: the Set Location sheet rendered directly
 * (it is ungated — no reveal animation on this surface) under the
 * two location states the recording exercised. One class per
 * frame, per the harness rule.
 */
abstract class Rw146SheetRenderTestBase : Rw145RenderTestBase() {

    protected val recents = listOf(
        RecentLocation(name = "Phoenix, AZ", latitude = 33.45, longitude = -112.07),
        RecentLocation(name = "New York, NY", latitude = 40.71, longitude = -74.0),
        RecentLocation(name = "Seattle, WA", latitude = 47.6, longitude = -122.33),
        RecentLocation(name = "Miami, FL", latitude = 25.76, longitude = -80.19)
    )

    protected fun sheetHost(currentLocation: String, usingGps: Boolean): Host {
        return compose(dark = false) {
            LocationPickerSheet(
                currentLocation = currentLocation,
                onSearch = {},
                onSelectLocation = { _, _, _ -> },
                onDismiss = {},
                isUsingGPS = usingGps,
                hasLocationPermission = true,
                recents = recents
            )
        }
    }
}
