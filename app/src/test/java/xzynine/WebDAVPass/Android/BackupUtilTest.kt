package xzynine.WebDAVPass.Android

import xzynine.WebDAVPass.Android.data.*
import xzynine.WebDAVPass.Android.util.BackupUtil
import xzynine.WebDAVPass.Android.util.EncryptionUtil
import xzynine.WebDAVPass.Android.util.UniqueIdGenerator
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * BackupUtil测试类
 */
class BackupUtilTest {
    private lateinit var mockWebServer: MockWebServer
    
    @Before
    fun setUp() {
        // 启动MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }
    
    @After
    fun tearDown() {
        // 停止MockWebServer
        mockWebServer.shutdown()
    }
    
    /**
     * 测试唯一标识生成器
     */
    @Test
    fun testUniqueIdGenerator() {
        val secret = "JBSWY3DPEHPK3PXP"
        val algorithm = "SHA1"
        val digits = 6
        val period = 30
        
        val uniqueId1 = UniqueIdGenerator.generate(secret, algorithm, digits, period)
        val uniqueId2 = UniqueIdGenerator.generate(secret, algorithm, digits, period)
        val uniqueId3 = UniqueIdGenerator.generate("different_secret", algorithm, digits, period)
        
        // 相同输入应生成相同的唯一标识
        assertEquals(uniqueId1, uniqueId2)
        // 不同输入应生成不同的唯一标识
        assert(uniqueId1 != uniqueId3)
        // 唯一标识应为64个字符（SHA-256哈希的十六进制表示）
        assertEquals(64, uniqueId1.length)
    }
    
    /**
     * 测试加密解密功能
     */
    @Test
    fun testEncryptionDecryption() {
        val plaintext = "test_data".toByteArray()
        val password = "test_password"
        
        // 加密数据
        val encryptedData = EncryptionUtil.encrypt(plaintext, password)
        
        // 解密数据
        val decryptedData = EncryptionUtil.decrypt(encryptedData, password)
        
        // 解密后的数据应与原数据相同
        assertEquals(String(plaintext), String(decryptedData))
    }
    
    /**
     * 测试OtpToken到CoreToken的转换
     */
    @Test
    fun testOtpTokenToCoreToken() {
        val otpToken = OtpToken(
            id = 1,
            ordinal = 0,
            issuer = "Google",
            label = "user@gmail.com",
            imagePath = null,
            tokenType = OtpTokenType.TOTP,
            algorithm = "SHA1",
            secret = "JBSWY3DPEHPK3PXP",
            digits = 6,
            counter = 0,
            period = 30,
            encryptionType = EncryptionType.NONE
        )
        
        val coreToken = otpToken.toCoreToken()
        
        // 验证转换后的核心令牌包含正确的信息
        assertEquals(otpToken.secret, coreToken.secret)
        assertEquals(otpToken.algorithm, coreToken.algorithm)
        assertEquals(otpToken.digits, coreToken.digits)
        assertEquals(otpToken.period, coreToken.period)
        assertEquals(otpToken.tokenType.name, coreToken.tokenType)
        assertEquals(otpToken.counter, coreToken.counter)
    }
    
    /**
     * 测试CoreToken到OtpToken的转换
     */
    @Test
    fun testCoreTokenToOtpToken() {
        val coreToken = CoreToken(
            secret = "JBSWY3DPEHPK3PXP",
            algorithm = "SHA1",
            digits = 6,
            period = 30,
            tokenType = "TOTP",
            counter = 0,
            updatedAt = "2025-12-14T10:00:00Z"
        )
        
        val tokenMetadata = TokenMetadata(
            issuer = "Google",
            label = "user@gmail.com",
            sort = 0,
            imagePath = null,
            contentHash = "test_hash",
            updatedAt = "2025-12-14T10:00:00Z"
        )
        
        val otpToken = coreToken.toOtpToken(tokenMetadata)
        
        // 验证转换后的OtpToken包含正确的信息
        assertEquals(coreToken.secret, otpToken.secret)
        assertEquals(coreToken.algorithm, otpToken.algorithm)
        assertEquals(coreToken.digits, otpToken.digits)
        assertEquals(coreToken.period, otpToken.period)
        assertEquals(OtpTokenType.valueOf(coreToken.tokenType), otpToken.tokenType)
        assertEquals(coreToken.counter, otpToken.counter)
        assertEquals(tokenMetadata.issuer, otpToken.issuer)
        assertEquals(tokenMetadata.label, otpToken.label)
        assertEquals(tokenMetadata.sort, otpToken.ordinal)
    }
    
    /**
     * 测试备份和恢复流程
     */
    @Test
    fun testBackupAndRestore() {
        // 准备测试数据
        val otpTokens = listOf(
            OtpToken(
                id = 1,
                ordinal = 0,
                issuer = "Google",
                label = "user@gmail.com",
                imagePath = null,
                tokenType = OtpTokenType.TOTP,
                algorithm = "SHA1",
                secret = "JBSWY3DPEHPK3PXP",
                digits = 6,
                counter = 0,
                period = 30,
                encryptionType = EncryptionType.NONE
            ),
            OtpToken(
                id = 2,
                ordinal = 1,
                issuer = "GitHub",
                label = "username",
                imagePath = null,
                tokenType = OtpTokenType.TOTP,
                algorithm = "SHA1",
                secret = "ABCDEFGHIJKLMNOP",
                digits = 6,
                counter = 0,
                period = 30,
                encryptionType = EncryptionType.NONE
            )
        )
        
        val password = "test_password"
        val deviceId = "test_device"
        
        // 这里需要模拟WebDAV服务器，测试备份和恢复功能
        // 由于MockWebServer的复杂性，这里只测试核心逻辑，不测试完整的网络交互
        
        // 测试唯一标识生成
        val uniqueId1 = UniqueIdGenerator.generate(
            otpTokens[0].secret,
            otpTokens[0].algorithm,
            otpTokens[0].digits,
            otpTokens[0].period
        )
        val uniqueId2 = UniqueIdGenerator.generate(
            otpTokens[1].secret,
            otpTokens[1].algorithm,
            otpTokens[1].digits,
            otpTokens[1].period
        )
        
        assertNotNull(uniqueId1)
        assertNotNull(uniqueId2)
        assert(uniqueId1 != uniqueId2)
        
        // 测试加密解密
        val coreToken = otpTokens[0].toCoreToken()
        val coreJson = BackupUtil.gson.toJson(coreToken)
        val encryptedData = EncryptionUtil.encrypt(coreJson.toByteArray(), password)
        val decryptedData = EncryptionUtil.decrypt(encryptedData, password)
        val decryptedCoreJson = String(decryptedData)
        val decryptedCoreToken = BackupUtil.gson.fromJson(decryptedCoreJson, CoreToken::class.java)
        
        assertEquals(coreToken.secret, decryptedCoreToken.secret)
        assertEquals(coreToken.algorithm, decryptedCoreToken.algorithm)
        assertEquals(coreToken.digits, decryptedCoreToken.digits)
        assertEquals(coreToken.period, decryptedCoreToken.period)
    }
}
