package xzynine.WebDAVPass.Android.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xzynine.WebDAVPass.Android.data.AppDatabase
import xzynine.WebDAVPass.Android.data.AppDatabaseHolder
import xzynine.WebDAVPass.Android.data.WebDavConfig
import xzynine.WebDAVPass.Android.data.WebDavPasswordCipher

/**
 * WebDAV 配置视图模型
 *
 * 负责管理 WebDAV 服务器配置的增删改查。
 * 说明：password 落库前统一加密，对外暴露的列表为解密后的明文。
 */
class WebDavConfigViewModel(
    private val context: Context,
) : ViewModel() {
    companion object {
        /**
         * 获取应用数据库单例
         */
        fun getDatabase(context: Context): AppDatabase = AppDatabaseHolder.getInstance(context)
    }

    private val database: AppDatabase = getDatabase(context)

    private val _webDavConfigs = MutableStateFlow<List<WebDavConfig>>(emptyList())
    val webDavConfigs: StateFlow<List<WebDavConfig>> = _webDavConfigs.asStateFlow()

    init {
        loadWebDavConfigs()
    }

    /**
     * 加载所有WebDAV配置（password 解密为明文后暴露给 UI）
     */
    private fun loadWebDavConfigs() {
        viewModelScope.launch {
            database.webDavConfigDao().getAll().collect {
                _webDavConfigs.value =
                    it.map { config ->
                        config.copy(password = decryptPassword(config.password))
                    }
            }
        }
    }

    /**
     * 解密存储的密码。
     *
     * 说明：正常使用中库内均为密文（一次性迁移已转换存量明文）；
     * 解密失败视为密码不可用，返回空串由上层提示重新输入。
     */
    private fun decryptPassword(stored: String): String = WebDavPasswordCipher.decrypt(stored) ?: ""

    /**
     * 刷新WebDAV配置列表，确保立即更新UI
     */
    private suspend fun refreshWebDavConfigList() {
        val configList =
            database
                .webDavConfigDao()
                .getAllOnce()
                .map { it.copy(password = decryptPassword(it.password)) }
        _webDavConfigs.value = configList
    }

    /**
     * 添加WebDAV配置（password 加密后落库）
     * @param config WebDAV配置对象（password 为明文）
     * @return 插入的配置ID
     */
    suspend fun addWebDavConfig(config: WebDavConfig): Long {
        val lastSortNumber = database.webDavConfigDao().getLastSortNumber()
        val nextSortNumber = (lastSortNumber ?: 0) + 1

        val encrypted =
            config.copy(
                sortNumber = nextSortNumber,
                password = WebDavPasswordCipher.encrypt(config.password),
            )
        val id = database.webDavConfigDao().insert(encrypted)
        refreshWebDavConfigList()
        return id
    }

    /**
     * 更新WebDAV配置（password 加密后落库）
     * @param config WebDAV配置对象（password 为明文）
     */
    fun updateWebDavConfig(config: WebDavConfig) {
        viewModelScope.launch {
            updateWebDavConfigInternal(config)
        }
    }

    /**
     * 同步落库更新实现（供 [updateWebDavConfig] 与 [autoSaveAccount] 直接等待）。
     */
    private suspend fun updateWebDavConfigInternal(config: WebDavConfig) {
        database.webDavConfigDao().update(config.copy(password = WebDavPasswordCipher.encrypt(config.password)))
        refreshWebDavConfigList()
    }

    /**
     * 自动保存云端账号。
     *
     * 说明：云端导入/新建/绑定时调用；按 (根地址, 目录, 用户名) 匹配已有配置，
     * 存在则仅更新名称/密码，不存在则新增，无需用户手动保存。
     *
     * @param baseUrl 服务器根地址（以 "/" 结尾）
     * @param directory 远端相对目录（可空）
     * @param username 用户名
     * @param password 明文密码
     * @param name 配置名称
     */
    suspend fun autoSaveAccount(
        baseUrl: String,
        directory: String?,
        username: String,
        password: String,
        name: String,
    ) {
        val normalizedBase = normalizeBaseUrl(baseUrl)
        val normalizedDir = directory?.trim()?.trim('/')
        val existing =
            database.webDavConfigDao().getAllOnce().firstOrNull {
                normalizeBaseUrl(it.url) == normalizedBase &&
                    it.username == username &&
                    (it.directory?.trim()?.trim('/') ?: "") == (normalizedDir ?: "")
            }
        if (existing != null) {
            val currentPlain = WebDavPasswordCipher.decrypt(existing.password)
            if (currentPlain == null || currentPlain != password || existing.name != name) {
                updateWebDavConfigInternal(existing.copy(name = name, password = password))
            }
            return
        }
        addWebDavConfig(
            WebDavConfig(
                id = 0,
                name = name,
                url = normalizedBase,
                directory = normalizedDir,
                username = username,
                password = password,
            ),
        )
    }

    /**
     * 保证服务器根地址以 "/" 结尾
     */
    private fun normalizeBaseUrl(raw: String): String = if (raw.endsWith("/")) raw else "$raw/"

    /**
     * 删除WebDAV配置
     * @param config WebDAV配置对象
     */
    fun deleteWebDavConfig(config: WebDavConfig) {
        viewModelScope.launch {
            database.webDavConfigDao().delete(config)
            refreshWebDavConfigList()
        }
    }

    /**
     * 根据ID删除WebDAV配置
     * @param id 配置ID
     */
    fun deleteWebDavConfigById(id: Long) {
        viewModelScope.launch {
            database.webDavConfigDao().deleteById(id)
            refreshWebDavConfigList()
        }
    }

    /**
     * 根据ID获取WebDAV配置
     * @param id 配置ID
     */
    suspend fun getWebDavConfigById(id: Long): WebDavConfig? = database.webDavConfigDao().getById(id)

    /**
     * 获取第一个WebDAV配置
     */
    suspend fun getFirstWebDavConfig(): WebDavConfig? {
        val configs = database.webDavConfigDao().getAllOnce()
        return configs.firstOrNull()
    }
}
