plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.bikeride.intercom"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bikeride.intercom"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core modules
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":engine:audio"))
    implementation(project(":engine:session"))
    implementation(project(":transport:api"))
    implementation(project(":transport:local-nearby"))
    implementation(project(":transport:internet-webrtc"))
    implementation(project(":transport:mesh"))
    implementation(project(":feature:ui"))
    implementation(project(":bluetooth"))
    implementation(project(":security"))
    implementation(project(":data"))
    implementation(project(":service"))

    // Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Kotlin & Coroutines
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.bundles.lifecycle)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Core Android
    implementation(libs.core.ktx)
    implementation(libs.appcompat)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit5.engine)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary)
}
