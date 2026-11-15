plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.notification.notification_test"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.notification.notification_test"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        resValues = true
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IDs AdMob — declared once here, exposed as R.string.* via resValue.
    //   ./gradlew assembleDebug   → Google's official test IDs (no invalid clicks)
    //   ./gradlew assembleRelease → production IDs (Play Store bundle)
    // Test IDs: https://developers.google.com/admob/android/test-ads
    // ═══════════════════════════════════════════════════════════════════════════
    buildTypes {
        debug {
            resValue("string", "appId",    "ca-app-pub-3940256099942544~3347511713")
            resValue("string", "bannerAd", "ca-app-pub-3940256099942544/9214589741")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "appId",    "ca-app-pub-5592679724436623~1956411584")
            resValue("string", "bannerAd", "ca-app-pub-5592679724436623/7017166570")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.play.services.ads)
    // play-services-ads tire work-runtime 2.7.0 (Room 2.2.5) qui instancie WorkDatabase_Impl
    // par réflexion → supprimé par R8 en release → crash au lancement. On force une version récente.
    implementation("androidx.work:work-runtime:2.10.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}