# AGENTS.md

KeePass 密码/2FA 令牌管理器（WebDAV 云同步），Jetpack Compose + Miuix UI。注释与提交信息均为中文。

本文件是本仓库对 agent 的常驻指令：只放**每个会话都需要在上下文里的命令**，每条一到三行，并链接它的正家。故事、完整示例、场景化流程不放这里。

## 分层

指令分三层，越具体越靠近代码。

| 层 | 位置 | 放什么 |
|---|---|---|
| 常驻指令 | 本文件与各子树 `AGENTS.md` | 每条会话都必须遵守的命令，一句话一条，链接理由 |
| 技能 | [.agents/skills/](.agents/skills/) | 按场景加载的工作流与判断标准，不常驻上下文 |
| 文档 | [Docs/](Docs/) | 当前状态的可检索事实，按读者与层级拆分 |

一个事实只有一个家。别处只准链接，不准复述。

## 命令

```sh
./gradlew assembleDebug          # 构建 universal debug APK，需密钥库，见「仓库关键事实」
./gradlew ktlintFormat           # 代码格式化，仅本仓库模块，不含 webdav/checkupdates 子模块
./gradlew :app:printVersionName  # 查当前版本名
npm install                      # 安装文档门禁依赖
npm run verify-all               # 文档系统全部门禁
npm run test:docs                # 快速文档门禁
```

新增或修改任何文档前先读[文档标准](Docs/AGENTS.md)与[散文标准](.agents/skills/wdp-prose-standard/SKILL.md)。

## 不可协商的规则

- **文档写当前状态。** 变更历史属于 Git 提交、Agent Note 与事故复盘；其他散文只描述现有机制。
- **每段一个物理行。** 用编辑器软换行；`verify-md-wrap` 拒绝硬折行的段落。
- **散文写完整契约，不写思考记录。** 保留行为、失败、时序、归属、情态、例外与后果；删除过程叙述、测试走查与代码复述。
- **决策留痕。** 影响长期维护的判断写入 [Agent Note](.agents/notes/README.md)；纯机械改动与局部界面调整豁免。
- **规则必须可执行。** 新增一条被评审强制的规则时，同时给出它能变红的门禁；只靠人记的规则会被忘掉。
- **改代码就改它拥有的文档。** 配置、默认值、错误、对外行为在同一变更里更新对应 README 与 JSDoc。
- **注释说明本地契约。** 不解释远处行为，不复述代码已经表达的结论。
- **共享状态必须有归属者。** 模块级缓存、单例、全局注册表都要挂到服务、实例或显式作用域上。
- **失败要结构化。** 定义失败类型、处理归属、遏制方式与失败后仍然成立的保证；只打日志不算恢复。
- **子模块代码在对应仓库修改。** `webdav`、`checkupdates` 是 git 子模块，勿在本仓库直接改。

## 仓库关键事实

- 项目结构与代码位置总览：[Docs/项目简介.md](Docs/项目简介.md)。
- **签名**：debug 与 release 共用同一 release 签名配置（`app/build.gradle.kts`），**没有密钥库则 `assembleDebug` 也会失败**。密钥库在仓库根目录 `PublicHub`（已 gitignore，CI 从 `KEYSTORE_BASE64` 解密生成），本地需自行放置。
- 签名凭据从 `local.properties` 读取：`KEY_ALIAS` / `KEY_PASSWORD` / `STORE_PASSWORD`，缺省回退同名环境变量。切勿提交真实凭据。
- `version.properties` 由 update.yml 在每次 push 到 main 时自动递增回写（并跑 ktlintFormat、创建 `v{版本}` tag），勿手动修改；查当前值用 `:app:printVersionName`。
- **模块结构**：`app` 是主应用（applicationId `xzynine.webdavpass`，autofill、biometric、Room（KSP）、CameraX/ZXing、KeePassJava2 在此或其下）；`webdav`、`checkupdates` 是 git 子模块（SSH URL `git@github.com:xzy-nine-common/...`，clone 需 `--recursive`，云同步与文件浏览 UI 由子模块提供，主应用只调用其组合）；`crypto`（含 JNI aes/argon2）、`database` 源自 Keepass2Android（`com.kunzisoft.keepass`）；`base`、`icon-pack`(+classic/material)、`text-drawable`、`token-images` 是工具与图标模块。
- `RepositoriesMode.FAIL_ON_PROJECT_REPOS`：模块内禁止再声明 repository。
- ABI splits 仅当任务名含 "Release" 时启用：`assembleRelease` 产出 armeabi-v7a/arm64-v8a/x86_64 + universal APK，`assembleDebug` 只产出 universal。
- app 的 compileSdk 使用 AGP 9 新写法 `compileSdk { version = release(37) }`；minSdk 29 / targetSdk 37，源码/目标均为 Java 21（miuix-nav 以 JVM 21 编译，调用方须同版本；CI 均用 JDK 21）。
- **16KB 页面大小**：`crypto` 固定 NDK r29（`ndkVersion "29.0.14206865"`，r28 起默认 16KB 对齐）并在 `CMakeLists.txt` 追加 `-Wl,-z,max-page-size=16384`；app 侧 `packaging.jniLibs.useLegacyPackaging = false`。不要再降回 NDK r27 以下，否则 `libaes.so`/`libargon2.so` 会退回 4KB 对齐。
- **Android 17 本地网络权限**：`webdav` 子模块清单声明 `ACCESS_LOCAL_NETWORK`，目标地址与授权判定见子模块 `LocalNetworkPermission`；宿主侧用 `ui/component/LocalNetworkPermissionGate.kt` 的 `rememberLocalNetworkPermissionGate()` 在访问局域网 WebDAV 前请求权限，同步失败类型为 `SyncFailureKind.LOCAL_NETWORK`。
- **应用列表权限**（条目「应用」字段取包名、读应用名/图标，自动填充按包名匹配均依赖它）：app 清单声明 `QUERY_ALL_PACKAGES`（清单权限、无运行时弹窗，**不用** `<queries>` 方案）+ MIUI/澎湃的 `com.android.permission.GET_INSTALLED_APPS`；判定与授权入口在 `util/InstalledAppsProvider.kt`（`isAppListPermissionGranted` / `miuiQueryAppsPermission`）与引导页 `OnboardingScreen` 最后一页。应用列表获取器与选择器 UI 参考同级目录 `E:\GitHubCode\01Main\NotifyRelay\Android`（`AppListHelper`/`LocalAppsViewModel`/`LocalAppsContent`）。
- 代码格式化：`./gradlew ktlintFormat`（ktlint 插件，仅覆盖本仓库模块，不含 webdav/checkupdates 子模块；.gitattributes 统一行尾：存储 LF、检出 CRLF 不用手动处理）。

## 目录

```
.agents/
  notes/      决策记录：为什么、放弃了什么、怎么验证
  skills/     按场景加载的工作流
Docs/
  AGENTS.md              文档标准（层级、预算、写作规则）
  defensive-patterns/    评审强制的缺陷类别规则
  postmortem/            事故复盘：为什么流程没拦住
scripts/                 文档门禁实现
```

## 校验

提交前按改动面选择最小充分证据，不要反射性地跑全量。CI 拥有穷尽矩阵。

| 改动 | 最小证据 |
|---|---|
| 文档、Agent Note、目录页 | `npm run test:docs` |
| Kotlin 源码 | 对应模块 `compileDebugKotlin` 加相关单元测试 |
| 门禁脚本本身 | 该脚本的单元测试，加上一次故意违规的负向验证 |
| 指令文件（`AGENTS.md`、技能） | `npm run verify-all` |

**新增门禁必须自证**：写一个非法用例，看它变红，再回退。只证明它能通过，等于没证明它有用。
