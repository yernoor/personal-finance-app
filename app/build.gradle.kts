plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.yernur.moneytracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.yernur.moneytracker"
        minSdk = 36
        targetSdk = 36
        versionCode = 9
        versionName = "0.5.0"
    }

    // Release signing comes from the environment (CI secrets, or exported locally); nothing about the key is in git.
    // Without RELEASE_KEYSTORE_FILE the release build is simply unsigned.
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

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Exported Room schemas feed the migration test (Robolectric reads them as assets). Debug builds only; ~30 KB.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
}

// Room schema JSON is committed so migrations can be tested from version 1.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.play.services.auth) // Authorization API: the access token for Sheets (drive.file scope, no refresh token on the device)
    implementation(libs.backdrop) // liquid glass: backdrop blur, lens refraction (Android 13+; we run on 16)
    implementation(libs.work.runtime)
    implementation(libs.biometric) // fingerprint / face unlock for the app lock (BiometricPrompt needs a FragmentActivity)
    implementation(libs.fragment)
    implementation(libs.glance.appwidget) // home-screen widgets
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
