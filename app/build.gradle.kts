import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing: a private key from GitHub Secrets if present (needed for Google Play),
// otherwise the bundled *debug* key so every build installs as an update over the previous one.
val ksB64: String? = System.getenv("MIZAN_KEYSTORE_B64")?.takeIf { it.isNotBlank() }
val ciKeystore = layout.buildDirectory.file("ci-release.jks").get().asFile

android {
    namespace = "com.mizan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mizan.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        create("mizan") {
            if (ksB64 != null) {
                ciKeystore.parentFile.mkdirs()
                ciKeystore.writeBytes(Base64.getDecoder().decode(ksB64))
                storeFile = ciKeystore
                storePassword = System.getenv("MIZAN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MIZAN_KEY_ALIAS")
                keyPassword = System.getenv("MIZAN_KEY_PASSWORD")
            } else {
                // Debug-only key committed on purpose (not a secret, not for Play Store).
                storeFile = file("mizan-debug.jks")
                storePassword = "mizan-debug"
                keyAlias = "mizandebug"
                keyPassword = "mizan-debug"
            }
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("mizan") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("mizan")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    androidResources { noCompress += "traineddata" }
    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES") }
        jniLibs { pickFirsts += "**/libc++_shared.so" }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material:material-ripple")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Offline OCR (Arabic + English), works without Google services
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")
    // PDF text layer extraction
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
}
