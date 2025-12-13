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
     * 加载所有令牌
     */
    private fun loadTokens() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 只获取一次初始数据
                val tokenList = database.otpTokenDao().getAllOnce()
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
     */
    fun addToken(token: OtpToken) {
        viewModelScope.launch {
            database.otpTokenDao().insert(token)
        }
    }

    /**
     * 删除令牌
     */
    fun deleteToken(tokenId: Long) {
        viewModelScope.launch {
            database.otpTokenDao().deleteById(tokenId)
            _tokenCodes.remove(tokenId)
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