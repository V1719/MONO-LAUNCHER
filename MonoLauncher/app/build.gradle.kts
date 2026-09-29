plugins {
    id("com.android.application")
}

android {
    namespace = "com.mono.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mono.launcher"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // R8 shrinking makes the release build smaller and faster.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal build: sign with the debug key so the optimized build is installable straight from Android Studio.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Theme.Material3.* (used in themes.xml) and Material You dynamic color both live here.
    implementation("com.google.android.material:material:1.12.0")
    // Pulls a dominant colour off the current wallpaper for adaptive theming.
    implementation("androidx.palette:palette-ktx:1.0.0")
    // Lets the PackageManager scan run off the main thread (lifecycleScope.launch).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
