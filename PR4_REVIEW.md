# PR 4 评审意见清单与核对（来源：CodeRabbit 完整评审）

> 来源：https://github.com/WebDAVPass/Android/pull/4 的 CodeRabbit 自动评审
> 通过 `gh api repos/WebDAVPass/Android/pulls/4/reviews` 与 `/comments` 获取完整 review body + 12 条 inline actionable + 8 条 nitpick。
> ⚠️ 注意：此前版本的本文件为误读/虚构内容，已作废。本版为完整真实评审。

---

## 一、Actionable 意见（12 条 inline，附路径与行号）

### A1. `.gitmodules` — CI 无法初始化 SSH 子模块
- 路径/行：`.gitmodules:4-6`
- 严重度：🟠 Major | ⚡ Quick win
- 内容：`actions/checkout@v4` 未设置 `submodules: true`，且 webdav 子模块为 SSH URL，CI 无法提供 SSH key，子模块初始化失败导致 `:app:printVersionName` 或 Gradle 子模块检查失败。建议改为 HTTPS URL，或在 checkout 时配置 SSH key。
- 核对：属实（CI 配置问题，与本地构建无关）。

### A2. `AppDatabaseHolder.kt` — Room 破坏性迁移
- 路径/行：`AppDatabaseHolder.kt:22-29`
- 严重度：🟠 Major
- 内容：`fallbackToDestructiveMigration(dropAllTables = true)` 在缺少迁移路径或版本升级时会销毁所有表（含历史库、当前选中库、加密凭据）。建议升级路径要求显式 Migration，降级才用 `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`。
- 核对：✅ 属实，已修复（`fallbackToDestructiveMigrationOnDowngrade`）。

### A3. `LibraryContextStore.kt` — 双重检查锁缺 `@Volatile` + 锁外读写（**Critical**）
- 路径/行：`LibraryContextStore.kt:31-56`
- 严重度：🔴 Critical | ⚡ Quick win
- 内容：`loaded/history/currentId` 为普通 `var`，`ensureLoaded()` 在锁外读 `loaded`，JMM 不保证锁内写入对锁外线程可见；`selectById`/`clearCurrentSelection` 锁外写 `currentId`；`removeHistoryByIds` 锁外遍历 `history` 与锁内 `history.add` 并发可能 `ConcurrentModificationException`。建议给字段加 `@Volatile` 并把所有读写放入 `synchronized(lock)`。
- 核对：✅ 属实，待修复。

### A4. `LibraryContextStore.kt` — 首次访问在调用线程阻塞（ANR 风险）
- 路径/行：`LibraryContextStore.kt:50-53`
- 严重度：🟠 Major | 🏗️ Heavy lift
- 内容：`ensureLoaded()` 由 `getHistory()`/`getCurrentLibrary()` 等同步 API 触发，常在 UI 线程；`runBlocking` 阻塞 UI 线程直到迁移+加载完成。迁移涉及逐条 KeyStore 加解密，低端设备耗时明显。建议应用启动时 IO 协程预热，或改为 `suspend`。
- 核对：⚠️ 属实但属较大重构（Heavy lift），需评估。

### A5. `LibraryContextStore.kt` — 异步落库缺异常处理
- 路径/行：`LibraryContextStore.kt:222-225`（含 236-238、259-261、300-302、306-308、355-357）
- 严重度：🟠 Major | ⚡ Quick win
- 内容：`ioScope` 为 `SupervisorJob()` + `Dispatchers.IO`，DAO 抛异常会传到未捕获异常处理器导致崩溃。`SupervisorJob` 不吞异常。建议安装 `CoroutineExceptionHandler`。
- 核对：✅ 属实，待修复。

### A6. `CloudLibraryDialog.kt` — 导入模式按钮误导
- 路径/行：`CloudLibraryDialog.kt:419-520`
- 严重度：🟡 Minor | ⚡ Quick win
- 内容：`enabled = isCreateMode || (isBindMode && !isBindReadOnly)`，导入模式按钮始终禁用，文案却落到"新建并进入"，误导。`importRemote` 分支在导入模式不可达。建议导入模式隐藏按钮或改为"按路径导入"。
- 核对：⚠️ 需结合 UI 逻辑确认。

### A7. `CloudSyncViewModel.kt` — 自动恢复失败消息类型处理
- 路径/行：`CloudSyncViewModel.kt:105`
- 严重度：🟠 Major
- 内容：当前用 `syncEngine.classifyFailure(ex)` 得到 `SyncOutcome` 传给 `resolveSyncFailureMessage`（第三参数类型 `SyncOutcome`），类型匹配。但评审建议改用 `SyncFailureKind.of(ex)` 以与声明一致（实为代码风格建议，当前类型已一致）。
- 核对：类型已一致，非必须修复；属建议性。

### A8. `CloudSyncViewModel.kt` — `!!` 强制解包密码解密失败 NPE
- 路径/行：`CloudSyncViewModel.kt:244-249`（及 `uploadCurrentCloudLibrary:296-298`）
- 严重度：🟠 Major | ⚡ Quick win
- 内容：`cloudLibrary.remoteFilePath!!`、`username!!`、`password!!`（`password` 解密失败时 `toLibraryContext` 置 null）会 NPE，被外层 catch 归类为 UNKNOWN，用户看不到正确提示。建议显式校验三字段，凭据缺失时返回专用提示。
- 核对：✅ 属实，待修复。

### A9. `WebDavConfigViewModel.kt` — `autoSaveAccount` 更新分支不等待落库
- 路径/行：`WebDavConfigViewModel.kt:124-130`
- 严重度：🟠 Major | ⚡ Quick win
- 内容：`autoSaveAccount` 是 suspend；新增分支调 `addWebDavConfig`（真正等待），更新分支调 `updateWebDavConfig`（内部 `viewModelScope.launch` 立即返回），返回时更新可能未落库。建议抽出 suspend 更新实现由 `autoSaveAccount` 直接等待。
- 核对：✅ 属实，待修复。

### A10. `build.gradle.kts` — Parcelize 插件版本未纳入版本目录
- 路径/行：`build.gradle.kts:5`
- 严重度：🟡 Minor | ⚡ Quick win
- 内容：`id("org.jetbrains.kotlin.plugin.parcelize") version "2.4.10"` 硬编码，与 `libs.versions.toml` 的 `kotlin = "2.4.10"` 重复。建议改 `alias(libs.plugins.kotlin.parcelize) apply false` 并在 toml 加 `kotlin-parcelize` 别名。
- 核对：⚠️ 可选优化（编译行为不变），优先级低。

### A11. `gradle/libs.versions.toml` — 移除废弃 `kotlinCompilerExtensionVersion`
- 路径/行：`gradle/libs.versions.toml:2-3`（及 `app/build.gradle.kts` composeOptions）
- 严重度：🟡 Minor | ⚡ Quick win
- 内容：已应用 `kotlin-compose` plugin，旧的 `composeOptions { kotlinCompilerExtensionVersion = ... }` 废弃，应删除；并删除 toml 中未使用的 `composeCompiler` 版本项。
- 核对：✅ 属实（需确认 app/build.gradle.kts 确实有该 composeOptions 块），待修复。

### A12. `gradle/wrapper/gradle-wrapper.properties` — 缺 `distributionSha256Sum`
- 路径/行：`gradle/wrapper/gradle-wrapper.properties:3`
- 严重度：🔒 Major
- 内容：更新到 `gradle-9.6.1-bin.zip` 但未设置 `distributionSha256Sum`，Wrapper 不校验分发包完整性。官方 SHA-256：`9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14`。
- 核对：✅ 属实，待修复。

---

## 二、Nitpick 意见（8 条）

| # | 文件 | 行 | 内容 | 核对 |
|---|------|----|------|------|
| N1 | LibraryContextDao.kt | 38-46 | 用 `@Upsert` 替代 `@Insert(onConflict=REPLACE)`（防御性，当前无外键） | 可选 |
| N2 | WebDavConfigViewModel.kt | 78-86 | `addWebDavConfig` 不应就地改 `config.sortNumber`，应并入 `config.copy()` | 属实，待修复 |
| N3 | WebDavPasswordCipher.kt | 92-97 | `getKey()` 每次重新加载 KeyStore，建议缓存 `SecretKey` | 可选优化 |
| N4 | CloudLibraryDialog.kt | 489-502 | 捕获异常未记日志，建议加 `Logger.e` | 属实，待修复 |
| N5 | CloudSyncViewModel.kt | 351-360 | 比较中文错误文案分支脆弱，建议用结构化枚举（涉及子模块） | 后续提交 |
| N6 | LibraryContextStore.kt | 388-389 | `normalizedAutoUnlockInvalidated` 中间变量无转换，与注释不符，应删 | 属实，待修复 |
| N7 | DateTimeFormatter.kt | 15 | 重命名为 `LocalTimeFormatter` 并改用 `java.time`（minSdk 29） | 可选重构 |
| N8 | gradle-wrapper.properties | 5-6 | `retries=0` 时 `retryBackOffMs=500` 无效，建议设正数或删 | 属实，待修复 |

---

## 三、修复状态汇总

| 编号 | 文件 | 严重度 | 状态 |
|------|------|--------|------|
| A1 | .gitmodules / CI | Major | ⬜ 待处理（需改 CI 或子模块 URL，非本地代码）|
| A2 | AppDatabaseHolder.kt | Major | ✅ 已修复（fallbackToDestructiveMigrationOnDowngrade）|
| A3 | LibraryContextStore.kt | **Critical** | ✅ 已修复（@Volatile + 锁内读写 currentId/history）|
| A4 | LibraryContextStore.kt | Major | ⬜ 待评估（Heavy lift，需改 suspend API）|
| A5 | LibraryContextStore.kt | Major | ✅ 已修复（ioScope 加 CoroutineExceptionHandler）|
| A6 | CloudLibraryDialog.kt | Minor | ⬜ 待确认 UI 行为 |
| A7 | CloudSyncViewModel.kt | Major | ⬜ 类型已一致，不强制 |
| A8 | CloudSyncViewModel.kt | Major | ✅ 已修复（download/upload 前显式校验凭据，去掉 !!）|
| A9 | WebDavConfigViewModel.kt | Major | ✅ 已修复（抽出 updateWebDavConfigInternal 由 autoSaveAccount 等待）|
| A10 | build.gradle.kts | Minor | ⬜ 可选（编译行为不变）|
| A11 | libs.versions.toml | Minor | ✅ 已修复（删除废弃 composeOptions 块 + composeCompiler 项，含 webdav 子模块）|
| A12 | gradle-wrapper.properties | Major | ✅ 已修复（加 distributionSha256Sum + retries=3）|
| N2 | WebDavConfigViewModel.kt | Trivial | ✅ 已修复（addWebDavConfig 用 copy 不就地改 sortNumber）|
| N4 | CloudLibraryDialog.kt | Trivial | ✅ 已修复（catch 块补 Logger.e）|
| N6 | LibraryContextStore.kt | Trivial | ⬜ 当前代码已无该中间变量，不适用 |
| N8 | gradle-wrapper.properties | Trivial | ✅ 已修复（retries=3）|
| N1/N3/N7 | 各文件 | Trivial | ⬜ 可选（@Upsert / KeyStore 缓存 / 重命名）|

> 注：base 模块 `consumer-rules.pro` 缺失的构建阻断问题已在提交 fdab590d 修复并 cherry-pick 入本分支，整体 `assembleDebug` 通过。
