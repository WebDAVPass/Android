/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的保存处理桥接接口。
 */

package xzynine.WebDAVPass.Autofill.bridge

import xzynine.WebDAVPass.Autofill.model.AutofillRegisterInfo

/**
 * 将表单数据保存为密码库条目。宿主 app 实现（通常调用自身 ViewModel 的创建条目方法）。
 *
 * @return 保存是否成功。
 */
interface AutofillSaveHandler {
    suspend fun save(registerInfo: AutofillRegisterInfo): Boolean
}
