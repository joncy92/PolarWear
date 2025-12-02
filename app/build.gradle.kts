plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Apply the Compose Compiler plugin (Required for Kotlin 2.0+)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.spartantech.polarwear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spartantech.polarwear"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Configure Kotlin Compiler Options
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.play.services.location)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation (libs.androidx.core.splashscreen)

    // Compose BOM (Bill of Materials)
    implementation(platform(libs.androidx.compose.bom))

    // UI & Tooling (Versions controlled by BOM)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.core)

    // Activity
    implementation(libs.androidx.activity.compose)

    // Wear OS Specific
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)

    // Debugging
    debugImplementation(libs.androidx.compose.ui.tooling)
}