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

    signingConfigs {
        // 固定签名密钥随仓库提交，确保每次 CI 构建使用同一证书。
        // 覆盖安装的硬性条件：包名不变 + 签名证书不变 + versionCode 递增。
        // 从 v1.0.3 起统一使用本密钥；之后的版本均可直接覆盖安装。
        create("release") {
            storeFile = file("release.p12")
            storePassword = "wifi-auto-reset-2026"
            keyAlias = "wifiautoreset"
            keyPassword = "wifi-auto-reset-2026"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            // 使用 V2 签名块，确保 MIUI 等国产 ROM 正确解析安装，
            // 避免 "解析软件包时出现问题 (33) / packageinfo is null"。
            signingConfig = signingConfigs.getByName("release")
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
