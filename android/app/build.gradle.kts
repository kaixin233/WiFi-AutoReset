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
        versionCode = 10
        versionName = "2.1.1"
        // 只保留中文资源，进一步减小 resources.arsc。
        resourceConfigurations += listOf("zh-rCN")
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

// 纯原生实现：无 AppCompat / Material / Fragment / WorkManager 依赖。
dependencies {
}
