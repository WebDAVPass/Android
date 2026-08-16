import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
}

// 版本号不再由构建系统从 git 历史推导（已移除 buildSrc 的 JGit 逻辑），
// 注入优先级：-PversionName/-PversionCode（gradle property）→ VERSION_NAME/VERSION_CODE（环境变量）
// → version.properties 固定值（本地构建使用，不递增）。
// version.properties 由发版流程（release.yml）维护写回，递增步长为
// 上次修改版本号 commit 到当前 HEAD 的提交数。
fun loadFixedVersion(): Pair<String, Int> {
    val props = Properties()
    val file = rootProject.file("version.properties")
    require(file.exists()) {
        "Missing version.properties. Provide -PversionName/-PversionCode (or env VERSION_NAME/VERSION_CODE) when building in CI."
    }
    file.inputStream().use { props.load(it) }

    val name =
        props.getProperty("versionName")
            ?: error("versionName is missing in version.properties")
    val code =
        props.getProperty("versionCode")?.toIntOrNull()
            ?: error("versionCode is missing or not an Int in version.properties")
    return name to code
}

val (fixedVersionName, fixedVersionCode) = loadFixedVersion()
val injectedVersionName =
    providers.gradleProperty("versionName").orNull
        ?: System.getenv("VERSION_NAME")
        ?: fixedVersionName
val injectedVersionCode =
    (
        providers.gradleProperty("versionCode").orNull
            ?: System.getenv("VERSION_CODE")
            ?: fixedVersionCode.toString()
    ).toIntOrNull() ?: fixedVersionCode

android {
    namespace = "xzynine.WebDAVPass.Android"
    compileSdk {
        version = release(37)
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
        // 版本号由 CI 注入或读取 version.properties 固定值
        versionCode = injectedVersionCode
        versionName = injectedVersionName

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
                "proguard-rules.pro",
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
        // miuix-nav（0.9.4-rc01）以 JVM 21 编译并大量使用 inline 函数，调用方必须同版本
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    // Compose 配置
    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.biometric)
    implementation(libs.material) // Material 组件库
    implementation(libs.miuix.android) // Miuix UI 库
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    // 日期选择器等系统组件（Material3 DatePicker/TimePicker）
    implementation(libs.compose.material3)
    implementation(libs.okhttp) // OkHttp3 网络库
    implementation(libs.jsoup) // Jsoup XML解析库
    implementation(libs.hutool.core) // Hutool工具库
    // 复用 WebDAV 库
    implementation(project(":webdav"))
    // content:// Uri 定位支持（WebDAV 文件浏览）
    implementation("androidx.documentfile:documentfile:1.0.1")
    // WebDAV 文件浏览图标支持
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation(project(":crypto"))
    implementation(project(":database"))
    implementation(project(":icon-pack"))
    // 导入base模块
    implementation(project(":base"))
    implementation("com.google.code.gson:gson:2.10.1") // Gson JSON解析库
    // 接入令牌图标系统模块
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
    // 导航库依赖（miuix-nav，navigationevent 由其传递引入）
    implementation(libs.miuix.nav)

    // Coroutines 相关依赖
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Room 数据库相关依赖
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// 移除强制使用旧版本stdlib的配置，让项目使用与Kotlin兼容的stdlib版本

tasks.register("printVersionName") {
    doLast {
        println(injectedVersionName)
    }
}
