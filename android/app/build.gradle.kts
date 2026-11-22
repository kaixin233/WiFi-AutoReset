plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.kaixin233.wifiautoreset"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.kaixin233.wifiautoreset"
        minSdk = 26
        targetSdk = 36
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // CI 无正式 keystore，使用 AGP 内置 debug 签名，确保 APK 可被系统解析安装。
            // 显式开启 V1+V2 签名：部分国产 ROM（如 MIUI）对仅 V2 签名或签名块异常的包
            // 会报 "解析软件包时出现问题 (33) / packageinfo is null"。
            signingConfig = signingConfigs.getByName("debug")
            v1SigningEnabled = true
            v2SigningEnabled = true
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

flutter {
    source = "../.."
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
