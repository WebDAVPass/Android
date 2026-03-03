pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WebDAVPass"

include(":app")
include(":crypto")
include(":database")
include(":icon-pack", ":icon-pack:classic", ":icon-pack:material")
// 注册令牌图标相关模块（从 FreeOTPPlus 复制）
include(":text-drawable")
include(":token-images")
include(":webdav")
// 检查更新模块
include(":checkupdates")
