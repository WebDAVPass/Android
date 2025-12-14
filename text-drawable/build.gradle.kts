plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    // 与 app 模块保持一致，移除对 rootProject.extra 的依赖
    compileSdk = 36

    defaultConfig {
        minSdk = 29

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // 迁移到 Kotlin compilerOptions DSL，避免 jvmTarget 弃用警告
    kotlin {
        compilerOptions {
            // 与 app 模块保持一致的 JVM 目标版本
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    namespace = "com.amulyakhare.textdrawable"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    // 适配 2fa-xzy 的版本库命名
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}