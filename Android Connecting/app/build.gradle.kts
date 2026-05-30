plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.sks.vibetype"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.sks.vibetype"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
        // 안드로이드 기본 코어 라이브러리
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.material)

        // 🚀 코루틴 (Coroutine) : 자판이 멈추지 않게 백그라운드에서 AI를 호출하는 도구
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

        // 🌐 레트로핏 (Retrofit2) : 백엔드 서버와 통신할 네트워크 대리인
        implementation("com.squareup.retrofit2:retrofit:2.9.0")
        implementation("com.squareup.retrofit2:converter-gson:2.9.0") // JSON 변환기
}