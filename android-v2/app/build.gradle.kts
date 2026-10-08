plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val uptodownAppUrl = providers.environmentVariable("UPTODOWN_APP_URL")
    .orElse("")
    .get()
    .trim()
    .ifBlank { "https://com-faisal-freshdownloader.en.uptodown.com/android/download" }
android {
    namespace = "com.faisal.freshdownloader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.faisal.freshdownloader"
        minSdk = 24
        targetSdk = 35
        versionCode = 45
        versionName = "1.6.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Uptodown currently distributes this app as arm64-v8a. Shipping only the
        // architecture users actually receive removes ~140 MB of duplicate native runtimes.
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
        buildConfigField("String", "UPTODOWN_APP_URL", buildConfigString(uptodownAppUrl))

        // Keep only the locales actually used by this app. This also strips
        // unused transitive-library translations from the release APK.
        resourceConfigurations += listOf("en", "zh-rCN")

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
        create("uptodownUpdate") {
            storeFile = rootProject.file("signing/universal-test.jks")
            storePassword = "android"
            keyAlias = "universaltest"
            keyPassword = "android"
        }
        create("uptodownRelease") {
            storeFile = rootProject.file("signing/universal-test.jks")
            storePassword = "android"
            keyAlias = "universaltest"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("huaweiRelease")
        }
        create("uptodownRelease") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("uptodownUpdate")
            matchingFallbacks += listOf("release")
        }
        create("uptodown") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("uptodownRelease")
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
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val youtubedlAndroid = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroid")
}
