# WebDAV后端移植计划

## 1. 移植WebDAV核心代码

### 1.1 复制核心文件

将源项目中的WebDAV核心文件复制到目标项目中：

* 源目录：`D:\xzy\nas-Sync\GitHub-code\legado\app\src\main\java\io\legado\app\lib\webdav`

* 目标目录：`D:\xzy\nas-Sync\GitHub-code\2fa-xzy\app\src\main\java\github\xzynine\two_fas\lib\webdav`

复制的文件包括：

* `Authorization.kt`

* `WebDav.kt`

* `WebDavException.kt`

* `WebDavFile.kt`

### 1.2 调整包名和依赖

修改所有文件中的包名，将 `io.legado.app.lib.webdav` 替换为 `github.xzynine.two_fas.lib.webdav`

## 2. 添加必要的依赖

根据源项目WebDAV实现的依赖，需要在目标项目的 `build.gradle.kts` 中添加：

* OkHttp3：用于网络请求

* Jsoup：用于XML解析

* Hutool：用于URL解码

## 3. 构建miuix前端界面

### 3.1 使用miuix构建界面 miuix的快速开始 [https://compose-miuix-ui.github.io/miuix/zh\_CN/guide/getting-started ](https://compose-miuix-ui.github.io/miuix/zh_CN/guide/getting-started)

miuix组件库说明\
<https://compose-miuix-ui.github.io/miuix/zh_CN/components/>

### 3.2 创建WebDAV界面

创建一个简单的WebDAV操作界面，包括：

* 连接配置页面：输入WebDAV服务器地址、用户名、密码

* 文件浏览页面：显示远程文件列表，支持上传、下载、删除操作(注意创建的子目录为'2fas\_xzy'浏览删除等操作也仅在该目录进行以免影响其他webdav中的文件(这点可参考源应用`legado`

### 3.3 实现功能绑定

将前端界面与WebDAV后端功能绑定，实现：

* 连接测试

* 文件列表获取

* 文件上传

* 文件下载

* 文件删除

