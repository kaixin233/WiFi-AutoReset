plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kaixin233.wifiautoreset"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kaixin233.wifiautoreset"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "2.0.0"
    }

    signingConfigs {
        // 固定签名密钥随仓库提交，确保覆盖安装时证书不变。
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
            // 使用 V2 签名，避免 MIUI 报「解析软件包时出现问题 (33)」。
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
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

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
