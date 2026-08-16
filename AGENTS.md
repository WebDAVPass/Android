# AGENTS.md

KeePass 密码/2FA 令牌管理器（WebDAV 云同步），Jetpack Compose + Miuix UI。注释与提交信息均为中文。

## 关键信息

Docs\项目简介.md

## 签名（易踩坑）

- debug 与 release 共用同一 release 签名配置（`app/build.gradle.kts`），**没有密钥库则 `assembleDebug` 也会失败**。
- 密钥库文件在仓库根目录 `PublicHub`（已 gitignore，CI 从 `KEYSTORE_BASE64` 解密生成）。本地需要自行放置。
- 凭据从 `local.properties` 读取：`KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD`，缺省回退同名环境变量。切勿提交真实凭据。


- `version.properties` 由 update.yml 在每次 push 到 main 时自动递增回写（并跑 ktlintFormat、创建 `v{版本}` tag），勿手动修改。
- 查当前值：`:app:printVersionName`

## 模块结构

- `app`：主应用（applicationId `xzynine.webdavpass`）。autofill、biometric、Room（KSP）、CameraX/ZXing、KeePassJava2 均在此或下。
- `webdav`、`checkupdates`：**git 子模块**（SSH URL `git@github.com:xzy-nine-common/...`），clone 需 `--recursive`。云同步与文件浏览 UI 由 `webdav` 子模块提供，主应用只调用其组合；子模块代码应在对应仓库修改，勿在本仓库直接改。
- `crypto`（含 JNI aes/argon2）、`database`：源自 Keepass2Android 的加密与 KDBX 读写层（`com.kunzisoft.keepass`）。
- `base`、`icon-pack`(+classic/material)、`text-drawable`、`token-images`：工具与图标模块。

## 构建配置注意

- `RepositoriesMode.FAIL_ON_PROJECT_REPOS`：模块内禁止再声明 repository。
- ABI splits 仅当任务名含 "Release" 时启用：`assembleRelease` 产出 armeabi-v7a/arm64-v8a/x86_64 + universal APK，`assembleDebug` 只产出 universal。
- app 的 compileSdk 使用 AGP 9 新写法 `compileSdk { version = release(37) }`；minSdk 29 / targetSdk 36，源码/目标均为 Java 21（miuix-nav 以 JVM 21 编译，调用方须同版本；CI 均用 JDK 21）。
- 代码格式化：`./gradlew ktlintFormat`（ktlint 插件，仅覆盖本仓库模块，不含 webdav/checkupdates 子模块；.gitattributes 统一行尾：存储 LF、检出 CRLF）。
