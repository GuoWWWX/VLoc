import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val updateRepository = providers.gradleProperty("vlocUpdateRepository").orElse("GuoWWWX/VLoc").get()
require(updateRepository.matches(Regex("[A-Za-z0-9-]+/[A-Za-z0-9_.-]+")))

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val licenseAssets = layout.buildDirectory.dir("generated/assets/vlocLicense")
val copyVlocLicense by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE")) { rename { "VLoc-LICENSE.txt" } }
    into(licenseAssets)
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(copyVlocLicense) }

android {
    namespace = "app.locationlab"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.locationlab"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "1.0.1"
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
    sourceSets.getByName("main").assets.srcDir(licenseAssets)

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
    implementation(libs.play.location)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
