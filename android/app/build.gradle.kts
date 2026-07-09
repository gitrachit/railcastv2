plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.railcast"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.railcast"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // BFF base URL (contracts §0). Env-configurable per build type later.
        buildConfigField("String", "BASE_URL", "\"https://api.railcast.app/\"")
    }

    buildTypes {
        release {
            // NFR-1 size budget: shrinking on from day one.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Ship every language in the base APK so the in-app switch works offline
    // and without Play Core downloads (FR-10.1, FR-9.x).
    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        // No hardcoded user-facing strings (FR-10.1); missing translations fail
        // the build so EN and HI never drift apart. Compose string literals are
        // additionally guarded by StringsParityTest + review (no stock lint yet).
        warningsAsErrors = false
        error += setOf("HardcodedText", "MissingTranslation", "ExtraTranslation")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Networking + SWR (backlog 3.3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
