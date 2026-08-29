// Tower — floating video control panel
//
// APK собирается в GitHub Actions (см. .github/workflows/apk.yml).
// Для локальной сборки: ./gradlew assembleDebug  (Gradle 8.7 + JDK 17)

val ksPath = System.getenv("TOWER_KEYSTORE") ?: "${rootDir}/keystore/tower.jks"
val ksStorePass = System.getenv("TOWER_STORE_PASSWORD") ?: "tower2024"
val ksAlias = System.getenv("TOWER_KEY_ALIAS") ?: "tower"
val ksKeyPass = System.getenv("TOWER_KEY_PASSWORD") ?: "tower2024"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tower.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tower.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(ksPath)
            storePassword = ksStorePass
            keyAlias = ksAlias
            keyPassword = ksKeyPass
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig =
                if (file(ksPath).exists()) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
