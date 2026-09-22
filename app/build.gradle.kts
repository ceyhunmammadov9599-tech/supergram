import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Telegram API credentials are loaded from local.properties (never hardcoded, never committed).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val telegramApiId: Int = localProps.getProperty("TELEGRAM_API_ID")?.trim()?.toIntOrNull()
    ?: run {
        logger.warn("TELEGRAM_API_ID missing in local.properties — falling back to 0")
        0
    }
val telegramApiHash: String = localProps.getProperty("TELEGRAM_API_HASH")?.trim()
    ?: run {
        logger.warn("TELEGRAM_API_HASH missing in local.properties — falling back to empty string")
        ""
    }

android {
    namespace = "com.supergram.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.supergram.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "1.0.8"

        // Exposed to the app through BuildConfig
        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId.toString())
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")
    }

    // Modular release signing: keystore.properties (git-ignored) or environment
    // variables — never hardcoded, never committed.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val releaseStoreFile = keystoreProps.getProperty("storeFile")
        ?: System.getenv("SUPERGRAM_STORE_FILE")
    val releaseStorePassword = keystoreProps.getProperty("storePassword")
        ?: System.getenv("SUPERGRAM_STORE_PASSWORD")
    val releaseKeyAlias = keystoreProps.getProperty("keyAlias")
        ?: System.getenv("SUPERGRAM_KEY_ALIAS")
    val releaseKeyPassword = keystoreProps.getProperty("keyPassword")
        ?: System.getenv("SUPERGRAM_KEY_PASSWORD")
    val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Null when no keystore is configured -> unsigned release APK.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
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
    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

dependencies {
    // Telegram TDLib — prebuilt JNI/AAR (libtdjni.so for arm64-v8a, armeabi-v7a, x86, x86_64)
    implementation(libs.tdlib.android.core)

    // Jetpack Compose (BOM-managed versions)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle / ViewModel
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
