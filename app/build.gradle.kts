plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.userexec.soneme"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.userexec.soneme"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

	signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SONEME_KEYSTORE"))
            storePassword = System.getenv("SONEME_STORE_PASSWORD")
            keyAlias = "soneme"
            keyPassword = System.getenv("SONEME_KEY_PASSWORD")
        }
    }

    buildTypes {
		getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
