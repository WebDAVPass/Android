import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 自动填充（Autofill）独立库模块。
// 源码移植自 KeePassDX（GPLv3，Copyright Jeremy Jamet / Kunzisoft），
// 通过 bridge 包下的接口与宿主 app 解耦，宿主 app 注入实现后调用。
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "xzynine.WebDAVPass.Autofill"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    // 键盘内联建议（Inline Suggestions）的 UI 模板，版本与 KeePassDX 保持一致
    implementation(libs.androidx.autofill)
    implementation(libs.bundles.kotlinx.coroutines)

    testImplementation(libs.junit)
}
