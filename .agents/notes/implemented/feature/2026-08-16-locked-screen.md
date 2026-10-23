# Agent Note: LockedScreen 专页承载锁定态

Status: implemented

## Problem

锁定与后台超时后，应用原先回退到 WelcomeScreen（选择/新建库页），把「已锁定」与「未选择库」两种状态混在同一个页面；最近任务预览也会暴露 WelcomeScreen 的可操作入口。

## Decision

新增 `Route.Locked` 与 `ui/Screen/LockedScreen.kt`：存在已记录库时，锁定回退与冷启动初始路由均为 LockedScreen；不存在库时仍回退 WelcomeScreen。LockedScreen 居中显示应用图标与「{库文件名}已锁定」，复用 `TokenViewModel` 的解锁链路（主密码/生物识别），「解锁」与「切换库」按钮经 `ON_START`/`ON_STOP` 生命周期判定仅在前台显示。改动落在 `Routes.kt`、`LockedScreen.kt`、`MainActivity.kt`。

## Alternatives considered

**继续以 WelcomeScreen 兼作锁定页。** 无需新路由，但两条业务路径共用一个页面后，文案与按钮语义必须按状态分叉，且后台预览会暴露解锁入口，故否决。

**在 LockedScreen 内重写解锁流程。** 复制 WelcomeScreen 的内联解锁与生物识别启动逻辑成本高，且与解锁链路形成两份维护点，故改为复用 `unlockCurrentLibrary` / `unlockWithBiometric`。

## Consequences

锁定态有了独立路由，各页面的安全守卫（`LaunchedEffect(isLibraryUnlocked, lib)`）统一回退到 `Route.Locked`；前台按钮隐藏避免最近任务预览暴露操作入口。前台判定使用 `ON_STOP`/`ON_START` 而非 `ON_PAUSE`/`ON_RESUME`，避免半透明弹窗误判。
