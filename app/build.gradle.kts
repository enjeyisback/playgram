import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Telegram API keys, registered once at my.telegram.org -> API development tools, kept out of the source.
val tgProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.playgram"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.playgram"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "1.0.5"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        buildConfigField("int", "TG_API_ID", (tgProps.getProperty("TG_API_ID") ?: "0").trim() + "")
        buildConfigField("String", "UPDATE_REPO", "\"" + (tgProps.getProperty("UPDATE_REPO") ?: "").trim() + "\"")
        buildConfigField("String", "TG_API_HASH", "\"" + (tgProps.getProperty("TG_API_HASH") ?: "").trim() + "\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)

    // Telegram TDLib 1.8.x: official sources, prebuilt for Android (Maven Central)
    implementation(libs.tdlib.core)

    // Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    // Embedded Local Streaming Server (Ktor CIO)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.cors)

    // Image loading for media thumbnails & posters
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Room SQLite
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
