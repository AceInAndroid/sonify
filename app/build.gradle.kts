import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
val updateBaseUrl = (localProps.getProperty("sonify.updateBaseUrl") ?: "https://sonify.zhangbing.tech")
    .trim()
    .trimEnd('/')

val releaseStoreFile = (System.getenv("SONIFY_STORE_FILE") ?: localProps.getProperty("sonify.release.storeFile") ?: "").trim()
val releaseStorePassword = (System.getenv("SONIFY_STORE_PASSWORD") ?: localProps.getProperty("sonify.release.storePassword") ?: "").trim()
val releaseKeyAlias = (System.getenv("SONIFY_KEY_ALIAS") ?: localProps.getProperty("sonify.release.keyAlias") ?: "").trim()
val releaseKeyPassword = (System.getenv("SONIFY_KEY_PASSWORD") ?: localProps.getProperty("sonify.release.keyPassword") ?: "").trim()
val hasReleaseSigning = releaseStoreFile.isNotEmpty() && file(releaseStoreFile).isFile &&
    releaseStorePassword.isNotEmpty() && releaseKeyAlias.isNotEmpty() && releaseKeyPassword.isNotEmpty()

android {
    namespace = "com.sonify.player"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.sonify.player"
        minSdk = 24
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"

        buildConfigField("String", "UPDATE_BASE_URL", "\"$updateBaseUrl\"")

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // 静态链接 STL：只有一个 .so，避免运行时依赖 libc++_shared.so
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Media3 (ExoPlayer + MediaSessionService + OkHttp DataSource for Subsonic streaming)
    val media3Version = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // Networking (Subsonic / OpenSubsonic REST API & streaming)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coil + Palette (Cover art & Spotify-style ambient color extraction)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Lightweight QR matrix generator for WiFi transfer pairing
    implementation("com.google.zxing:core:3.5.3")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
