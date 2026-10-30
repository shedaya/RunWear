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
    namespace = "com.runwear.wear"
    compileSdk = 35

    signingConfigs {
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
        minSdk = 30
        targetSdk = 35
        // One applicationId = one versionCode space shared with the phone
        // app (see ~/workspace/release-prep/wear-os-distribution-playbook.md):
        // phone bundles use the plain series (1, 2, 3, …); standalone wear
        // bundles use 10000 + the aligned phone versionCode.
        // This build: 10018 ↔ phone 1.2.4 / vc18 (internal track
        // 2026-10-29). The wear versionName tracks the phone versionName.
        versionCode = 10018
        versionName = "1.2.4"
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
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // WearScreenshotTest is a screenshot-generation harness
                // (Robolectric native-graphics renders of the production
                // pages), and its shots leak window/focus state into each
                // other inside one fork: solo, every shot renders; in one
                // shared fork the ScalingLazyColumn pages degrade to
                // fully transparent captures from the second activity on
                // (bisected 2026-10-29). Fork per test class so each
                // render gets the fresh sandbox it demonstrably needs.
                it.forkEvery = 1
            }
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
    
    // Wear OS Compose
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.navigation)

    // Regular Compose Foundation (for HorizontalPager)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation("androidx.compose.foundation:foundation")
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Image Loading (Coil) - for hero images
    implementation(libs.coil.compose)

    // Wear OS Tiles
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.tiles.material)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.horologist.tiles)

    // Wear OS Complications
    implementation(libs.androidx.wear.watchface.complications.data.source)
    implementation(libs.androidx.wear.watchface.complications.data.source.ktx)

    // Coroutines (for tiles)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ── Local JVM screenshot harness (test-only; ships nothing) ──
    // Robolectric native-graphics renders of the production wear
    // composables for the Play Wear OS listing screenshots — see
    // src/test/java/com/runwear/wear/WearScreenshotTest.kt and the
    // wear-os-distribution-playbook's no-emulator recipe.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit-ktx:1.2.1")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(libs.androidx.ui.test.manifest)
}
