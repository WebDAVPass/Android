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

    defaultConfig {
        applicationId = "github.xzynine.two_fas"
        minSdk = 29
        targetSdk = 36
        // 使用自动计算的版本号
        versionCode = computedVersionCode
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val keystorePath = System.getenv("KEYSTORE_PATH") ?: project.findProperty("KEYSTORE_PATH") as? String
        val signingStorePassword = System.getenv("STORE_PASSWORD") ?: project.findProperty("STORE_PASSWORD") as? String
        val signingKeyPassword = System.getenv("KEY_PASSWORD") ?: project.findProperty("KEY_PASSWORD") as? String
        val signingKeyAlias = System.getenv("KEY_ALIAS") ?: project.findProperty("KEY_ALIAS") as? String

        // Local-only fallback (not committed): read optional properties from two locations, otherwise pick first .jks in PublicHub
        val publicHubPath = "D:/xzy/nas-Sync/androidKey/2fa-xzy/PublicHub"
        val publicHubFile = File(publicHubPath)
        val localPropFiles = listOf(
            File("D:/xzy/nas-Sync/androidKey/2fa-xzy/signing.local.properties")
        )
        val localProps = Properties().apply {
            localPropFiles.filter { it.isFile }.forEach { file ->
                file.inputStream().use { load(it) }
            }
        }
        val localKeystore = if (publicHubFile.exists()) {
            publicHubFile
        } else {
            null
        }

        val resolvedKeystore = keystorePath
            ?: localProps.getProperty("KEYSTORE_PATH")
            ?: localKeystore?.absolutePath
        val resolvedStorePassword = signingStorePassword ?: localProps.getProperty("STORE_PASSWORD")
        val resolvedKeyPassword = signingKeyPassword ?: localProps.getProperty("KEY_PASSWORD")
        val resolvedKeyAlias = signingKeyAlias ?: localProps.getProperty("KEY_ALIAS")

        if (!resolvedKeystore.isNullOrBlank() && !resolvedStorePassword.isNullOrBlank() && !resolvedKeyPassword.isNullOrBlank() && !resolvedKeyAlias.isNullOrBlank()) {
            create("release") {
                storeFile = file(resolvedKeystore)
                storePassword = resolvedStorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
            }
        }
    }

    val releaseSigning = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")

    buildTypes {
        getByName("debug") {
            signingConfig = releaseSigning
        }
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = releaseSigning
        }
    }
    
    // 只在 release 构建时启用 ABI splits，debug 只生成 universal APK
    splits {
        abi {
            // 只在包含 Release 任务时启用分包，否则只生成 universal APK
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release") }
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    
    // 配置资源打包选项，解决 META-INF/DEPENDENCIES 冲突问题
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
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
    implementation(libs.okhttp) // OkHttp3 网络库
    implementation(libs.jsoup) // Jsoup XML解析库
    implementation(libs.hutool.core) // Hutool工具库
    // 复用 WebDAV 库
    implementation(project(":webdav"))
    implementation("com.google.code.gson:gson:2.10.1") // Gson JSON解析库
    // 接入令牌图标系统模块
    implementation(project(":text-drawable"))
    implementation(project(":token-images"))
    
    // CameraX 核心库
    implementation("androidx.camera:camera-core:1.3.3")
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")
    // ZXing 二维码解析库
    implementation("com.google.zxing:core:3.5.3")
    
    // Compose 相关依赖
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime.livedata)
    
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