plugins {
    alias(libs.plugins.android.application)
}

// Emulator-only test tool (docs/FEATURES.md "Wallet payment capture"): it poses as the Google Wallet app so the main app's listener sees
// notifications from the Wallet package. Never install it on a phone that has the real Wallet (the package name is Wallet's own).
android {
    namespace = "dev.yernur.moneytracker.walletstub"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.google.android.apps.walletnfcrel"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    testImplementation(libs.junit)
}
