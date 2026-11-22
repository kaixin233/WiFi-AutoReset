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
            // CI 无正式 keystore，使用 AGP 内置 debug 签名（默认含 V1+V2 签名块），
            // 确保产出的 APK 可被 MIUI 等国产 ROM 正确解析安装，
            // 否则会报 "解析软件包时出现问题 (33) / packageinfo is null"。
            //
            // 覆盖安装依赖：包名不变 + 签名证书不变 + versionCode 递增。
            // 请勿在版本迭代中途更换签名方式，否则系统会拒绝覆盖安装。
            signingConfig = signingConfigs.getByName("debug")
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
