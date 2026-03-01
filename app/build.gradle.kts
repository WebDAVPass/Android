plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // 添加Compose插件
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

import java.io.File
import java.util.Properties

// 使用 buildSrc 的 JGit 实现计算版本信息

// 主版本号（major）
// - 直接在此处设置主版本号；不再从 gradle.properties 读取。
// - 在发布重大版本时请在这里更新此值（并可同时调整下方的 versionMajorSubtract）。
// 例如：val versionMajor: Int = 1
val versionMajor: Int = 1 // <-- 在此处直接修改主版本号

// 使用 buildSrc 中的 Versioning 实现来计算版本信息
// 支持在此文件内直接设置次版本（minor）减量（不使用 gradle.properties）：
// - 当主版本号（versionMajor）升级后，可以在下面直接把 `versionMajorSubtract` 改为期望的值，
//   这样 main 的提交计数会在计算中减去该值（下限为 0），防止次版本无限递增。
// - 示例：如果希望在 major 升级后把 main 的计数回退 340，则设置为 340。
val versionMajorSubtract: Int = 0 // <-- 在此处直接修改以手动应用减量
val versionInfo = Versioning.compute(rootProject.projectDir, versionMajor, versionMajorSubtract)
val computedVersionName = versionInfo.versionName
val computedVersionCode = versionInfo.versionCode

android {
    namespace = "xzynine.WebDAVPass.Android"
    compileSdk {
        version = release(36)
    }

    // 从 local.properties 读取签名信息
    val localProperties = Properties()
    val localPropertiesFile = File(rootProject.projectDir, "local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    // 签名配置
    signingConfigs {
        create("release") {
            keyAlias = localProperties.getProperty("KEY_ALIAS", System.getenv("KEY_ALIAS"))
            keyPassword = localProperties.getProperty("KEY_PASSWORD", System.getenv("KEY_PASSWORD"))
            storeFile = file("../PublicHub")
            storePassword = localProperties.getProperty("STORE_PASSWORD", System.getenv("STORE_PASSWORD"))
        }
    }

    defaultConfig {
        applicationId = "xzynine.webdavpass"
        minSdk = 29
        targetSdk = 36
        // 使用自动计算的版本号
        versionCode = computedVersionCode
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    // 只在 release 构建时启用 ABI splits，debug 只生成 universal APK
    splits {
        abi {
            // 只在包含 Release 任务时启用分包，否则只生成 universal APK
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release") }
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    
    // 配置资源打包选项，解决 META-INF/DEPENDENCIES 冲突问题
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    // Compose 配置
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    
    // 配置 16KB 页面大小 LOAD 段对齐
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
        implementation(libs.material) // Material 组件库
    implementation(libs.miuix.android) // Miuix UI 库
    implementation(libs.miuix.icons)
    implementation(libs.okhttp) // OkHttp3 网络库
    implementation(libs.jsoup) // Jsoup XML解析库
    implementation(libs.hutool.core) // Hutool工具库
    // 复用 WebDAV 库
    implementation(project(":webdav"))
    implementation(project(":crypto"))
    implementation(project(":database"))
    implementation("com.google.code.gson:gson:2.10.1") // Gson JSON解析库
    // 接入令牌图标系统模块
    implementation(project(":text-drawable"))
    implementation(project(":token-images"))
    // 检查更新模块
    implementation(project(":checkupdates"))
    
    // CameraX 核心库
    implementation("androidx.camera:camera-core:1.3.3")
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")
    // ZXing 二维码解析和生成库
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    
    // Compose 相关依赖
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime.livedata)
    // 导航库依赖
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation(libs.jetbrains.androidx.navigationevent) // Miuix 弹窗组件需要的导航事件库
    
    // Coroutines 相关依赖
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Room 数据库相关依赖
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// 移除强制使用旧版本stdlib的配置，让项目使用与Kotlin兼容的stdlib版本

tasks.register("printVersionName") {
    doLast {
        println(computedVersionName)
    }
}