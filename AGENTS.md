# AGENTS.md

KeePass 密码/2FA 令牌管理器（WebDAV 云同步），Jetpack Compose + Miuix UI。注释与提交信息均为中文。

## 构建与验证

- Windows 用 `gradlew.bat`，JDK 17。Gradle 9.6.1（wrapper）、AGP 9.3.1、Kotlin 2.4.10，版本统一在 `gradle/libs.versions.toml` 管理。
- 常用命令：`gradlew.bat assembleDebug`、`gradlew.bat assembleRelease`、`gradlew.bat :app:printVersionName`。
- CI（`.github/workflows/android-build.yml`）仅 `workflow_dispatch` 手动触发，无 push/PR 自动构建。
- 无有效单元测试（仅示例 InstrumentedTest），`test` 任务基本无覆盖。

## 签名（易踩坑）

- debug 与 release 共用同一 release 签名配置（`app/build.gradle.kts`），**没有密钥库则 `assembleDebug` 也会失败**。
- 密钥库文件在仓库根目录 `PublicHub`（已 gitignore，CI 从 `KEYSTORE_BASE64` 解密生成）。本地需要自行放置。
- 凭据从 `local.properties` 读取：`KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD`，缺省回退同名环境变量。切勿提交真实凭据。

## 版本号自动计算，勿硬编码

- `buildSrc/src/main/kotlin/Versioning.kt` 用 JGit 从 git 历史计算：versionName = `major.main提交数.MMddHHmm`（main 分支加 `-release` 后缀），versionCode = `major*1_000_000 + mainCount*1_000 + MMddHHmm`。
- 改版本只动 `app/build.gradle.kts` 顶部的 `versionMajor` / `versionMajorSubtract`，不要动 gradle.properties。
- 依赖本地存在 main 分支引用（`refs/heads/main`、`origin/main` 等），找不到时回退到 HEAD 计数——浅克隆会导致版本号异常。
- 每次构建版本都会随提交数/时间变化；查当前值用 `:app:printVersionName`。

## 模块结构

- `app`：主应用（applicationId `xzynine.webdavpass`）。autofill、biometric、Room（KSP）、CameraX/ZXing、KeePassJava2 均在此或下。
- `webdav`、`checkupdates`：**git 子模块**（SSH URL `git@github.com:xzy-nine-common/...`），clone 需 `--recursive`。云同步与文件浏览 UI 由 `webdav` 子模块提供，主应用只调用其组合；子模块代码应在对应仓库修改，勿在本仓库直接改。
- `crypto`（含 JNI aes/argon2）、`database`：源自 Keepass2Android 的加密与 KDBX 读写层（`com.kunzisoft.keepass`）。
- `base`、`icon-pack`(+classic/material)、`text-drawable`、`token-images`：工具与图标模块。
- `buildSrc`：仅版本计算（JGit）。

## 构建配置注意

- `RepositoriesMode.FAIL_ON_PROJECT_REPOS`：模块内禁止再声明 repository。
- ABI splits 仅当任务名含 "Release" 时启用：`assembleRelease` 产出 armeabi-v7a/arm64-v8a/x86_64 + universal APK，`assembleDebug` 只产出 universal。
- app 的 compileSdk 使用 AGP 9 新写法 `compileSdk { version = release(37) }`；minSdk 29 / targetSdk 36，源码/目标均为 Java 17。
