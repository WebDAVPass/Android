# Agent Note: 重复与包装清理的范围与推迟项

Status: implemented

## Problem

`重复与包装审计报告`（提交 7c31b89）在仓库内识别出重复实现、死代码与浅包装数十项。不收敛这些实现，同一逻辑的多份副本会在后续改动中各自漂移。

## Decision

审计报告 §七「本仓库内可完成」清单已在本仓库执行完毕：A 类（app 侧重复实现，如 OTP 校验与生成改调 `database` 的 `TokenCalculator`、删除 app 侧 Base32）、B 类（附件选择器、有界读取、基础地址规范化、只读卡片等收敛为公共件）、C/D/E 类（死代码删除与浅包装内联）。逐项处置与落地位置记录在当时的执行记录中，见 Git 历史。

## Alternatives considered

**全部一次性收敛。** `database`、`crypto` 的上游移植代码与 `webdav`、`checkupdates` 子模块不在本仓库改动范围；强行收敛需在上游仓库协调，收益低、风险高，故推迟。

**连语义风险项一起改。** B15（域名与包名匹配三套语义）须经 `AppPackageFieldTest`、`UriHelperTest` 回归后再动；B7、B12、B16–B18、B27、B28 涉及横竖屏参数块与较大重构，均保留为推迟项。

## Consequences

app 侧每类逻辑只剩一份实现，公共件集中在 `ui/component/` 与 `util/`。验证证据：`:app`、`:autofill`、`:base`、`:database`、`:crypto` 的 `compileDebugKotlin` 与 `ktlintFormat` 均 BUILD SUCCESSFUL。执行过程中 `crypto` 的 `Base64Helper.b64Decode` 与 `argon2/src/CMakeLists.txt` 一度被误删后还原——该 CMakeLists 是 `libargon2.so` 的构建入口，删除会破坏 release 构建。推迟项清单继续约束后续清理，重启前须先读执行记录。
