package github.xzynine.two_fas.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import github.xzynine.two_fas.data.OtpToken
import github.xzynine.two_fas.data.OtpTokenDatabase
import github.xzynine.two_fas.data.TokenCode
import github.xzynine.two_fas.util.TokenCodeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 令牌视图模型
 */
class TokenViewModel(private val context: Context) : ViewModel() {
    
    companion object {
        @Volatile
        private var INSTANCE: OtpTokenDatabase? = null
        
        fun getDatabase(context: Context): OtpTokenDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OtpTokenDatabase::class.java,
                    "otp_token_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
    
    private val database: OtpTokenDatabase = getDatabase(context)
    
    private val tokenCodeUtil: TokenCodeUtil = TokenCodeUtil()

    private val _tokens = MutableStateFlow<List<OtpToken>>(emptyList())
    val tokens: StateFlow<List<OtpToken>> = _tokens.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _tokenCodes = mutableMapOf<Long, MutableStateFlow<TokenCode?>>()

    init {
        loadTokens()
        startTokenRefreshTimer()
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
     * 重复判断基于：secret + algorithm + digits + period
     */
    private fun processDuplicateTokens(tokens: List<OtpToken>): List<OtpToken> {
        // 使用密钥+算法+位数+周期作为键，值为令牌列表
        val tokenMap = mutableMapOf<String, MutableList<OtpToken>>()
        
        // 将令牌分组
        tokens.forEach { token ->
            // 创建分组键：密钥+算法+位数+周期
            val key = "${token.secret}_${token.algorithm}_${token.digits}_${token.period}"
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
        database.otpTokenDao().insert(token)
        // 刷新令牌列表，确保立即更新UI
        refreshTokenList()
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
}