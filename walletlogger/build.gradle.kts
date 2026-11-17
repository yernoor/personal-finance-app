plugins {
    alias(libs.plugins.android.application)
}

// Throwaway research tool (docs/FEATURES.md "Wallet payment capture"): its own app, no INTERNET permission, framework views only.
android {
    namespace = "dev.yernur.moneytracker.walletlogger"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.yernur.moneytracker.walletlogger"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.jvmArgs("--enable-native-access=ALL-UNNAMED") } // Robolectric loads its native runtime: allowed, so JDK 25 does not warn
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
