package github.xzynine.two_fas.ui.ViewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import github.xzynine.two_fas.data.AppDatabase
import github.xzynine.two_fas.data.OtpToken
import github.xzynine.two_fas.data.TokenCode
import github.xzynine.two_fas.data.WebDavConfig
import github.xzynine.two_fas.lib.webdav.WebDav
import github.xzynine.two_fas.lib.webdav.Authorization
import github.xzynine.two_fas.util.BackupUtil
import github.xzynine.two_fas.util.UniqueIdGenerator
import github.xzynine.two_fas.util.TokenCodeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 令牌视图模型
 */
class TokenViewModel(private val context: Context) : ViewModel() {

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val MIGRATION_1_2 = object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 创建 webdav_configs 表以兼容从 v1 升级到 v2
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `webdav_configs` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `url` TEXT NOT NULL,
                                `username` TEXT NOT NULL,
                                `password` TEXT NOT NULL,
                                `sort_number` INTEGER NOT NULL
                            )
                        """.trimIndent())
                    }
                }

                val MIGRATION_2_3 = object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // 为旧数据补充 uniqueId 列（非空，默认空串便于后续回填），再建立唯一索引
                        db.execSQL("ALTER TABLE otp_tokens ADD COLUMN uniqueId TEXT NOT NULL DEFAULT ''")

                        val cursor = db.query("select id, secret, algorithm, digits, period from otp_tokens")
                        val latestById = mutableMapOf<String, Long>()
                        val idsToDelete = mutableListOf<Long>()

                        cursor.use {
                            while (it.moveToNext()) {
                                val id = it.getLong(0)
                                val secret = it.getString(1)
                                val algorithm = it.getString(2)
                                val digits = it.getInt(3)
                                val period = it.getInt(4)

                                val uniqueId = UniqueIdGenerator.generate(secret, algorithm, digits, period)
                                val keptId = latestById[uniqueId]

                                if (keptId == null || id > keptId) {
                                    keptId?.let { oldId -> idsToDelete.add(oldId) }
                                    latestById[uniqueId] = id
                                    db.execSQL("update otp_tokens set uniqueId = ? where id = ?", arrayOf(uniqueId, id))
                                } else {
                                    idsToDelete.add(id)
                                }
                            }
                        }

                        if (idsToDelete.isNotEmpty()) {
                            val idList = idsToDelete.joinToString(",")
                            db.execSQL("delete from otp_tokens where id in ($idList)")
                        }

                        db.execSQL("""
                            CREATE UNIQUE INDEX IF NOT EXISTS `index_otp_tokens_uniqueId`
                            ON `otp_tokens` (`uniqueId`)
                        """.trimIndent())
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "otp_token_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }

    private val database: AppDatabase = getDatabase(context)

    private val tokenCodeUtil: TokenCodeUtil = TokenCodeUtil()

    private val _tokens = MutableStateFlow<List<OtpToken>>(emptyList())
    val tokens: StateFlow<List<OtpToken>> = _tokens.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _tokenCodes = mutableMapOf<Long, MutableStateFlow<TokenCode?>>()

    // WebDAV配置相关
    private val _webDavConfigs = MutableStateFlow<List<WebDavConfig>>(emptyList())
    val webDavConfigs: StateFlow<List<WebDavConfig>> = _webDavConfigs.asStateFlow()
    
    // 备份相关状态
    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()
    
    private val _isRestoreInProgress = MutableStateFlow(false)
    val isRestoreInProgress: StateFlow<Boolean> = _isRestoreInProgress.asStateFlow()
    
    private val _backupStatus = MutableStateFlow("")
    val backupStatus: StateFlow<String> = _backupStatus.asStateFlow()
    
    private val _backupProgress = MutableStateFlow(0)
    val backupProgress: StateFlow<Int> = _backupProgress.asStateFlow()
    
    private val _restoreProgress = MutableStateFlow(0)
    val restoreProgress: StateFlow<Int> = _restoreProgress.asStateFlow()
    
    private val _customEncryptionPassword = MutableStateFlow<String?>(null)
    val customEncryptionPassword: StateFlow<String?> = _customEncryptionPassword.asStateFlow()
    
    init {
        loadTokens()
        loadWebDavConfigs()
        startTokenRefreshTimer()
        autoRestoreTokens()
    }

    /**
     * 加载所有WebDAV配置
     */
    private fun loadWebDavConfigs() {
        viewModelScope.launch {
            database.webDavConfigDao().getAll().collect { configs ->
                _webDavConfigs.value = configs
            }
        }
    }

    /**
     * 刷新WebDAV配置列表，确保立即更新UI
     */
    private suspend fun refreshWebDavConfigList() {
        val configList = database.webDavConfigDao().getAllOnce()
        _webDavConfigs.value = configList
    }

    /**
     * 加载所有令牌，并检查和处理重复数据
     */
    private fun loadTokens() {
        viewModelScope.launch {
            refreshTokenList()
        }
    }

    /**
     * 刷新令牌列表，确保立即更新UI
     * 在当前协程中同步执行
     */
    private suspend fun refreshTokenList() {
        _isLoading.value = true
        try {
            // 只获取一次初始数据
            var tokenList = database.otpTokenDao().getAllOnce()

            // 检查并处理重复数据
            tokenList = processDuplicateTokens(tokenList)

            _tokens.value = tokenList
            // 为每个令牌创建代码流
            tokenList.forEach { token ->
                if (!_tokenCodes.containsKey(token.id)) {
                    _tokenCodes[token.id] = MutableStateFlow(tokenCodeUtil.generateTokenCode(token))
                }
            }
        } catch (e: Exception) {
            // 如果出现异常，确保加载状态结束
            _tokens.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 处理重复令牌，删除重复项，保留最新的（id最大的）
     * 重复判断基于：uniqueId字段
     */
    private fun processDuplicateTokens(tokens: List<OtpToken>): List<OtpToken> {
        // 使用uniqueId作为键，值为令牌列表
        val tokenMap = mutableMapOf<String, MutableList<OtpToken>>()

        // 将令牌分组
        tokens.forEach { token ->
            // 使用uniqueId作为分组键
            val key = token.uniqueId
            if (!tokenMap.containsKey(key)) {
                tokenMap[key] = mutableListOf()
            }
            tokenMap[key]?.add(token)
        }

        val uniqueTokens = mutableListOf<OtpToken>()

        // 处理每个分组
        tokenMap.forEach { (_, tokenList) ->
            if (tokenList.size > 1) {
                // 有重复，保留id最大的（最新的）
                val uniqueToken = tokenList.maxByOrNull { it.id }!!
                uniqueTokens.add(uniqueToken)

                // 删除重复项（id不是最大的）
                viewModelScope.launch {
                    val tokensToDelete = tokenList.filter { it.id != uniqueToken.id }
                    tokensToDelete.forEach {
                        database.otpTokenDao().deleteById(it.id)
                    }
                }
            } else {
                // 没有重复，直接添加
                uniqueTokens.add(tokenList[0])
            }
        }

        return uniqueTokens
    }

    /**
     * 获取指定令牌的代码
     */
    fun getTokenCode(tokenId: Long): StateFlow<TokenCode?> {
        if (!_tokenCodes.containsKey(tokenId)) {
            _tokenCodes[tokenId] = MutableStateFlow(null)
        }
        return _tokenCodes[tokenId]!!.asStateFlow()
    }

    /**
     * 启动令牌刷新定时器
     */
    private fun startTokenRefreshTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1000) // 每秒刷新一次
                refreshTokenCodes()
            }
        }
    }

    /**
     * 刷新所有令牌代码
     */
    private fun refreshTokenCodes() {
        _tokens.value.forEach { token ->
            val currentCode = _tokenCodes[token.id]?.value
            val newCode = tokenCodeUtil.generateTokenCode(token)

            // 只有当代码发生变化时才更新
            if (currentCode?.code != newCode.code) {
                _tokenCodes[token.id]?.value = newCode
            }
        }
    }

    /**
     * 添加新令牌
     * @return 是否成功添加（如果密钥+算法+位数+周期已存在则返回false）
     */
    suspend fun addToken(token: OtpToken): Boolean {
        // 检查是否已存在相同密钥+算法+位数+周期的令牌
        val count = database.otpTokenDao().countBySecretAlgorithmDigitsPeriod(
            token.secret,
            token.algorithm,
            token.digits,
            token.period
        )
        if (count > 0) {
            // 密钥+算法+位数+周期已存在，不允许添加
            return false
        }

        // 密钥+算法+位数+周期不存在，可以添加
        // 如果token对象没有uniqueId，则生成一个
        val tokenWithId = if (token.uniqueId.isBlank()) {
            val uniqueId = github.xzynine.two_fas.util.UniqueIdGenerator.generate(
                token.secret,
                token.algorithm,
                token.digits,
                token.period
            )
            token.copy(uniqueId = uniqueId)
        } else {
            token
        }
        
        database.otpTokenDao().insert(tokenWithId)
        // 刷新令牌列表，确保立即更新UI
        refreshTokenList()
        
        // 自动备份
        backupTokens()
        
        return true
    }

    /**
     * 删除令牌
     */
    fun deleteToken(tokenId: Long) {
        viewModelScope.launch {
            database.otpTokenDao().deleteById(tokenId)
            _tokenCodes.remove(tokenId)
            // 刷新令牌列表，确保立即更新UI
            refreshTokenList()
            
            // 自动备份
            backupTokens()
        }
    }

    /**
     * 更新令牌
     */
    fun updateToken(token: OtpToken) {
        viewModelScope.launch {
            database.otpTokenDao().update(token)
            // 刷新代码
            _tokenCodes[token.id]?.value = tokenCodeUtil.generateTokenCode(token)
            // 刷新令牌列表，确保立即更新UI
            refreshTokenList()
            
            // 自动备份
            backupTokens()
        }
    }

    /**
     * 递增HOTP计数器
     */
    fun incrementCounter(tokenId: Long) {
        viewModelScope.launch {
            database.otpTokenDao().incrementCounter(tokenId)
        }
    }

    /**
     * 添加WebDAV配置
     * @param config WebDAV配置对象
     * @return 插入的配置ID
     */
    suspend fun addWebDavConfig(config: WebDavConfig): Long {
        // 设置排序号
        val lastSortNumber = database.webDavConfigDao().getLastSortNumber()
        config.sortNumber = (lastSortNumber ?: 0) + 1

        val id = database.webDavConfigDao().insert(config)
        refreshWebDavConfigList()
        return id
    }

    /**
     * 更新WebDAV配置
     * @param config WebDAV配置对象
     */
    fun updateWebDavConfig(config: WebDavConfig) {
        viewModelScope.launch {
            database.webDavConfigDao().update(config)
            refreshWebDavConfigList()
        }
    }

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
    suspend fun getWebDavConfigById(id: Long): WebDavConfig? {
        return database.webDavConfigDao().getById(id)
    }

    /**
     * 获取第一个WebDAV配置
     */
    suspend fun getFirstWebDavConfig(): WebDavConfig? {
        val configs = database.webDavConfigDao().getAllOnce()
        return configs.firstOrNull()
    }
    
    /**
     * 获取设备ID
     */
    private fun getDeviceId(): String {
        // 使用应用上下文的设备ID，或者生成一个唯一ID并存储
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }
    
    /**
     * 获取加密密码
     */
    private suspend fun getEncryptionPassword(): String {
        return _customEncryptionPassword.value ?: getFirstWebDavConfig()?.password ?: ""
    }
    
    /**
     * 设置自定义加密密码
     */
    fun setCustomEncryptionPassword(password: String?) {
        _customEncryptionPassword.value = password
    }
    
    /**
     * 自动恢复令牌
     */
    private fun autoRestoreTokens() {
        viewModelScope.launch {
            try {
                _isRestoreInProgress.value = true
                _restoreProgress.value = 0
                _backupStatus.value = "正在尝试自动恢复..."
                
                val webDavConfig = getFirstWebDavConfig() ?: return@launch
                val password = webDavConfig.password
                
                val restoreResult = restoreTokensWithPassword(webDavConfig, password)
                if (restoreResult) {
                    _backupStatus.value = "自动恢复成功"
                } else {
                    _backupStatus.value = "自动恢复失败，需要手动输入密码"
                }
            } catch (e: Exception) {
                _backupStatus.value = "自动恢复失败：${e.message}"
            } finally {
                _isRestoreInProgress.value = false
                _restoreProgress.value = 0
            }
        }
    }
    
    /**
     * 手动恢复令牌
     * @param password 恢复密码
     */
    fun manualRestoreTokens(password: String) {
        viewModelScope.launch {
            try {
                _isRestoreInProgress.value = true
                _restoreProgress.value = 0
                _backupStatus.value = "正在手动恢复..."
                
                val webDavConfig = getFirstWebDavConfig() ?: throw Exception("未配置WebDAV")
                
                val restoreResult = restoreTokensWithPassword(webDavConfig, password)
                if (restoreResult) {
                    _backupStatus.value = "手动恢复成功"
                } else {
                    _backupStatus.value = "手动恢复失败：密码错误"
                }
            } catch (e: Exception) {
                _backupStatus.value = "手动恢复失败：${e.message}"
            } finally {
                _isRestoreInProgress.value = false
                _restoreProgress.value = 0
            }
        }
    }
    
    /**
     * 使用指定密码恢复令牌
     * @param webDavConfig WebDAV配置
     * @param password 恢复密码
     * @return 是否恢复成功
     */
    private suspend fun restoreTokensWithPassword(webDavConfig: WebDavConfig, password: String): Boolean {
        return try {
            val webDav = WebDav(webDavConfig.url, Authorization(webDavConfig.username, webDavConfig.password))
            val deviceId = getDeviceId()
            
            val restoredTokens = BackupUtil.restoreTokens(webDav, password, deviceId) { progress ->
                _restoreProgress.value = progress
            }
            
            val tokensToInsert = mutableListOf<OtpToken>()
            for (token in restoredTokens) {
                // 检查本地是否已存在该令牌
                val existingToken = database.otpTokenDao().getByUniqueId(token.uniqueId)
                if (existingToken == null) {
                    // 本地不存在，添加到插入列表
                    tokensToInsert.add(token)
                }
            }
            
            if (tokensToInsert.isNotEmpty()) {
                // 只插入本地不存在的令牌
                database.otpTokenDao().insertAll(tokensToInsert)
                refreshTokenList()
                true
            } else {
                // 所有令牌都已存在本地
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 备份令牌
     */
    fun backupTokens() {
        viewModelScope.launch {
            try {
                _isBackupInProgress.value = true
                _backupStatus.value = "正在备份..."
                _backupProgress.value = 0
                
                val webDavConfig = getFirstWebDavConfig() ?: throw Exception("未配置WebDAV")
                val encryptionPassword = getEncryptionPassword()
                
                val tokens = database.otpTokenDao().getAllOnce()
                val webDav = WebDav(webDavConfig.url, Authorization(webDavConfig.username, webDavConfig.password))
                val deviceId = getDeviceId()
                
                BackupUtil.backupTokens(webDav, tokens, encryptionPassword, deviceId, context) {
                    _backupProgress.value = it
                }
                _backupStatus.value = "备份成功"
            } catch (e: Exception) {
                _backupStatus.value = "备份失败：${e.message}"
            } finally {
                _isBackupInProgress.value = false
                _backupProgress.value = 0
            }
        }
    }
}