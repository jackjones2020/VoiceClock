plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.voiceclock"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        applicationId = "com.example.voiceclock"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.1.3"
    }
}

dependencies { testImplementation("junit:junit:4.13.2") }
