// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ktlint) apply false
    base
}

// 对除 git 子模块（webdav/checkupdates）外的所有项目应用 ktlint；
// 子模块代码在各自仓库维护，本仓库不得格式化或提交其内容。
apply(plugin = "org.jlleitschuh.gradle.ktlint")

subprojects {
    if (name != "webdav" && name != "checkupdates") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
    }
}

// 文档系统门禁：经 npm 运行 scripts/verify-all.ts。
// 首次运行前需要 `npm install`（Windows 下 Gradle 调用 npm.cmd，其余平台调用 npm）。
val verifyDocs =
    tasks.register<Exec>("verifyDocs") {
        group = "verification"
        description = "运行文档系统全部门禁（npm run verify-all）"
        executable =
            if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
                "npm.cmd"
            } else {
                "npm"
            }
        args("run", "verify-all")
        inputs.files(
            fileTree("Docs"),
            fileTree(".agents"),
            fileTree("scripts"),
            "AGENTS.md",
            "package.json",
            "package-lock.json",
        )
    }

// `./gradlew check` 聚合全部验证：文档门禁与各模块 check 一起执行。
tasks.named("check") {
    dependsOn(verifyDocs)
}
