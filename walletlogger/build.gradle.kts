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

    // Same signing setup as :app (environment variables, nothing in git).
    signingConfigs {
        System.getenv("RELEASE_KEYSTORE_FILE")?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
