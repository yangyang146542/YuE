plugins {
    id("com.android.application")
}

android {
    namespace = "com.zcode.deepseekbalance"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.neo.deepseekfree"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        ndk {
            // Priority order: arm64-v8a (armv8a), armeabi-v7a (v7a), x86
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86")
        }
    }

    signingConfigs {
        // release 用 debug keystore 签名，保证生成的 release 包能直接安装。
        // 若要上架/长期分发，请换成自己的正式 keystore 并妥善保管。
        create("releaseDebug") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseDebug")
        }
    }

    // release 的 lint 检查需要联网拉 lint-gradle，会破坏离线构建；本地自用关掉即可
    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    // SAF 文件夹遍历（本地 Gradle 缓存已有，仍可离线构建）
    implementation("androidx.documentfile:documentfile:1.0.0")
}
