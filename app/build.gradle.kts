import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

// ── Auto-incrementing version ────────────────────────────────────────────────
val verFile = file("version.properties")
val verProps = Properties()
if (verFile.exists()) verFile.inputStream().use { verProps.load(it) }
val buildNumber: Int = (verProps["buildNumber"]?.toString()?.toIntOrNull() ?: 0) + 1
val buildDate: String = providers.exec { commandLine("date", "+%Y%m%d") }.standardOutput.asText.get().trim()

android {
    namespace = "com.zongting.zongting"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zongting.zongting"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    flavorDimensions += "channel"

    productFlavors {
        create("beta") {
            dimension = "channel"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            buildConfigField("String", "VERSION_JSON_URL", "\"http://172.16.1.93:8080/ZongTing/test/version.json\"")
            buildConfigField("String", "UPDATE_CHANNEL", "\"test\"")
            resValue("string", "app_name", "纵听测试版")
        }
        create("prod") {
            dimension = "channel"
            buildConfigField("String", "VERSION_JSON_URL", "\"http://172.16.1.93:8080/ZongTing/release/version.json\"")
            buildConfigField("String", "UPDATE_CHANNEL", "\"release\"")
            resValue("string", "app_name", "纵听")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    // Material Components (for XML theme)
    implementation("com.google.android.material:material:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-brotli:4.12.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Media3 for audio playback
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DataStore for persistence
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}

kapt {
    correctErrorTypes = true
}

// ── Auto-increment build number after each assemble ──────────────────────────
val incrBuildNum by tasks.registering {
    doLast {
        val pf = file("version.properties")
        val p = Properties()
        if (pf.exists()) pf.inputStream().use { p.load(it) }
        p["buildNumber"] = buildNumber.toString()
        pf.outputStream().use { p.store(it, "Auto-incrementing build number") }
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    dependsOn(incrBuildNum)
}
