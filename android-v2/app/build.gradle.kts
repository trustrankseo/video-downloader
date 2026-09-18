plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val appLovinSdkKey = providers.environmentVariable("APPLOVIN_SDK_KEY").orElse("").get()
val appLovinBannerAdUnitId = providers.environmentVariable("APPLOVIN_BANNER_AD_UNIT_ID").orElse("").get()
val appLovinInterstitialAdUnitId = providers.environmentVariable("APPLOVIN_INTERSTITIAL_AD_UNIT_ID").orElse("").get()

android {
    namespace = "com.faisal.freshdownloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.faisal.freshdownloader"
        minSdk = 24
        targetSdk = 35
        versionCode = 35
        versionName = "1.5.0"

        buildConfigField("String", "APPLOVIN_SDK_KEY", buildConfigString(appLovinSdkKey))
        buildConfigField("String", "APPLOVIN_BANNER_AD_UNIT_ID", buildConfigString(appLovinBannerAdUnitId))
        buildConfigField("String", "APPLOVIN_INTERSTITIAL_AD_UNIT_ID", buildConfigString(appLovinInterstitialAdUnitId))

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/universal-test.jks")
            storePassword = "android"
            keyAlias = "universaltest"
            keyPassword = "android"
        }
        create("huaweiRelease") {
            storeFile = rootProject.file("signing/huawei-release.jks")
            storePassword = System.getenv("HUAWEI_KEYSTORE_PASSWORD")
            keyAlias = "universalrelease"
            keyPassword = System.getenv("HUAWEI_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("huaweiRelease")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-viewbinding")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation("com.applovin:applovin-sdk:13.6.4")
    implementation("com.google.android.gms:play-services-ads-identifier:18.3.0")

    val youtubedlAndroid = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")
}
