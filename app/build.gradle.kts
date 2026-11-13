import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Every DB version's schema is committed under app/schemas/: migrations are written and tested against them.
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "ua.echecker"
    compileSdk = 36

    defaultConfig {
        applicationId = "ua.echecker"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }
    // Release signing reads from ~/.gradle/gradle.properties (never committed). Without those
    // properties the release build is left unsigned; debug builds are unaffected.
    val keystorePath = providers.gradleProperty("ECHECKER_KEYSTORE").orNull
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("ECHECKER_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("ECHECKER_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("ECHECKER_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            // R8: shrink, optimize and obfuscate; libraries (AndroidX, OkHttp, DataStore) ship their own keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    // Per-app language (Android 13+ system settings): Ukrainian (default) and English.
    androidResources {
        generateLocaleConfig = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    // MigrationTestHelper reads the exported schemas from the app's assets; Robolectric serves the debug
    // variant's merged assets (not test ones), so they ship in debug builds only — never in release.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
