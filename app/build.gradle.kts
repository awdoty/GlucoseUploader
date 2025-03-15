plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.glucoseuploader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.glucoseuploader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17" // Fixed to match Java version
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        disable += listOf("NotificationPermission", "DefaultLocale")
        abortOnError = false
    }
    buildToolsVersion = "35.0.1"
}

dependencies {
    implementation(libs.core.ktx.v1150)
    implementation(libs.lifecycle.runtime.ktx.v287)
    implementation(libs.activity.compose.v1101)

    // Compose - using only one set of dependencies (the BOM approach)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation(libs.material)
    implementation(libs.material.icons.core)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation 'androidx.activity:activity-compose:1.8.2'
    implementation "androidx.compose.ui:ui:1.6.3"
    implementation "androidx.compose.ui:ui-tooling-preview:1.6.3"

    // Material Design 3
    implementation 'androidx.compose.material3:material3:1.2.0'

    // Material Components (for backward compatibility)
    implementation 'com.google.android.material:material:1.11.0'


    // Health Connect
    implementation(libs.connect.client)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Navigation
    implementation(libs.navigation.compose.v289)

    // CSV parsing
    implementation(libs.commons.csv)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Debug
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
 }