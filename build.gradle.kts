// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ktlint) apply false
}

// 对除 git 子模块（webdav/checkupdates）外的所有项目应用 ktlint；
// 子模块代码在各自仓库维护，本仓库不得格式化或提交其内容。
apply(plugin = "org.jlleitschuh.gradle.ktlint")

subprojects {
    if (name != "webdav" && name != "checkupdates") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
    }
}
