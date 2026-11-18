plugins {
    alias(libs.plugins.android.application)
}

// Emulator-only test tool (docs/FEATURES.md "Wallet payment capture"): it poses as the Google Wallet app (flavor `wallet`) or the Kaspi.kz
// app (flavor `kaspi`) so the main app's listener sees notifications from that package. Never install a flavor on a phone that has the real
// app (the package name is the real app's own).
android {
    namespace = "dev.yernur.moneytracker.walletstub"
    compileSdk = 37

    defaultConfig {
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "poses"
    productFlavors {
        create("wallet") { dimension = "poses"; applicationId = "com.google.android.apps.walletnfcrel" }
        create("kaspi") { dimension = "poses"; applicationId = "kz.kaspi.mobile" }
    }
}

dependencies {
    testImplementation(libs.junit)
}
