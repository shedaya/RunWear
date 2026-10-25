import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.runwear.app"
    compileSdk = 35

    signingConfigs {
        // Pinned debug keystore (~/workspace/.android-debug, survives VM replacements).
        // Without this, AGP auto-generates a debug key under the JVM user.home
        // (/root/.android), which is wiped on VM replacement -> new signing cert ->
        // every subsequent APK fails to update with "App not installed". Falls back
        // to AGP's default when the pinned keystore is absent (e.g. fresh clone).
        getByName("debug") {
            val pinned = rootProject.file("../../.android-debug/runwear-debug.keystore")
            if (pinned.exists()) {
                storeFile = pinned
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.runwear.wear"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric render harness needs the merged resources.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Shared module
    implementation(project(":shared"))
    
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    
    // Charts (Vico)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)

    // Image Loading (Coil) - for hero images with crossfade
    implementation(libs.coil.compose)

    // Google Fonts
    implementation(libs.androidx.ui.text.google.fonts)

    // Glance (Android Widgets)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Widget last-good store (its own DataStore file in the app module)
    implementation(libs.datastore.preferences)

    // Widget refresh backstop (periodic, network-constrained, backoff)
    implementation(libs.androidx.work.runtime.ktx)

    // Play In-App Review (rating moment — no-ops on non-Play installs)
    implementation(libs.play.review)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Unit tests: JUnit plus the real org.json (the mockable android.jar's
    // org.json is stubbed; the standalone jar shadows it on the test
    // runtime classpath so the telemetry payload builders run as-is).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // Widget render harness (WidgetRenderTest): Robolectric native
    // graphics + the WorkManager test initializer the Glance session
    // needs. Matches the versions the JetLagKiller harness uses.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.work:work-testing:2.9.1")
    testImplementation("androidx.test:core:1.6.1")
}
