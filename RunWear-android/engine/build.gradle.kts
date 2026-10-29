import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

/**
 * :engine — the RunWear decision layer, shared by Android and Apple.
 *
 * Stage 2 of the iOS parity plan (Route C): the pure decision core of the
 * recommendation engine — selection tables, clothing/weather models, hero
 * combination mappings, thumbnail key map — lives here ONCE, in common
 * Kotlin, so the platforms cannot silently drift. Platform concerns
 * (weather fetching, Supabase, UI, storage) stay in :shared / the apps.
 *
 * Consumed by:
 * - Android: :shared exposes it as `api`, so :app and :wear compile and
 *   run against these exact classes (packages are unchanged from their
 *   pre-extraction home in :shared).
 * - Apple: packaged as the dynamic XCFramework `RunWearEngine`
 *   (iOS + watchOS, device + simulator) via
 *   `./gradlew :engine:assembleRunWearEngineXCFramework`, built on macOS
 *   CI and dropped into RunWear-iOS/Frameworks/ (gitignored).
 */
kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    val xcFramework = XCFramework("RunWearEngine")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
        watchosArm64(),
        // watchosDeviceArm64 is NOT optional either: Kotlin's
        // watchosArm64 emits the arm64_32 device slice only, while
        // Xcode archives the watch app for arm64 + arm64_32, and a
        // missing arm64 slice fails the watch link with undefined
        // RunWearEngine symbols. Simulator-only CI cannot see this —
        // it first fired on the TestFlight archive of 2026-10-29.
        watchosDeviceArm64(),
        watchosSimulatorArm64(),
        // watchosX64 is NOT optional: Xcode builds the watch app for
        // arm64 + x86_64 on the watchOS simulator, and a missing x86_64
        // slice fails the watch link with undefined RunWearEngine
        // symbols (found the hard way in Stage 2 CI, 2026-10-29).
        watchosX64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "RunWearEngine"
            isStatic = false
            xcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // LocalDateTime for the weather models (replaces java.time so
            // the models are platform-neutral). `api`: :shared and the app
            // modules convert at their edges against it.
            api(libs.kotlinx.datetime)
        }
    }
}

android {
    namespace = "com.runwear.engine"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
