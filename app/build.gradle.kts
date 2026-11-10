import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val updateRepository = providers.gradleProperty("vlocUpdateRepository").orElse("GuoWWWX/VLoc").get()
require(updateRepository.matches(Regex("[A-Za-z0-9-]+/[A-Za-z0-9_.-]+")))

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.locationlab"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.locationlab"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.7.0"
        buildConfigField("String", "UPDATE_REPOSITORY", "\"$updateRepository\"")
    }

    // Deliver each ARM device only the native renderer it can use.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.datastore)
    implementation(libs.maps.osm)
    implementation(libs.maps.maplibre)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
