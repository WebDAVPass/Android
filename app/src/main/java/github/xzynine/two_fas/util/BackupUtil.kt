package github.xzynine.two_fas.util

import com.google.gson.Gson
import github.xzynine.two_fas.data.*
import github.xzynine.two_fas.lib.webdav.WebDav
import github.xzynine.two_fas.lib.webdav.WebDavException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * 备份工具类，提供TOTP令牌的备份和恢复功能
 */
object BackupUtil {
    private val gson = Gson()
    
    /**
     * 计算文件内容的SHA-256哈希值
     * @param content 文件内容
     * @return SHA-256哈希值的十六进制表示
     */
    fun calculateContentHash(content: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 构建正确的路径，避免双斜杠问题
     * @param basePath 基础路径
     * @param segments 路径段
     * @return 构建后的路径
     */
    private fun buildPath(basePath: String, vararg segments: String): String {
        val pathBuilder = StringBuilder(basePath.trimEnd('/'))
        for (segment in segments) {
            if (segment.isNotBlank()) {
                pathBuilder.append('/').append(segment.trim('/'))
            }
        }
        return pathBuilder.toString()
    }
    
    /**
     * 将OtpToken转换为CoreToken
     * @param otpToken OtpToken对象
     * @return CoreToken对象
     */
    fun OtpToken.toCoreToken(): CoreToken {
        return CoreToken(
            secret = this.secret,
            algorithm = this.algorithm,
            digits = this.digits,
            period = this.period,
            tokenType = this.tokenType.name,
            counter = this.counter,
            updatedAt = Instant.now().toString()
        )
    }
    
    /**
     * 将CoreToken转换为OtpToken
     * @param coreToken CoreToken对象
     * @param metadata TokenMetadata对象
     * @return OtpToken对象
     */
    fun CoreToken.toOtpToken(metadata: TokenMetadata): OtpToken {
        // 生成唯一标识符
        val uniqueId = UniqueIdGenerator.generate(this.secret, this.algorithm, this.digits, this.period)
        
        return OtpToken(
            id = 0, // 新插入的令牌ID会自动生成
            ordinal = metadata.sort,
            issuer = metadata.issuer,
            label = metadata.label,
            imagePath = metadata.imagePath,
            tokenType = OtpTokenType.valueOf(this.tokenType),
            algorithm = this.algorithm,
            secret = this.secret,
            digits = this.digits,
            counter = this.counter,
            period = this.period,
            encryptionType = EncryptionType.NONE,
            uniqueId = uniqueId
        )
    }
    
    /**
     * 下载元数据
     * @param webDav WebDav客户端
     * @return 元数据对象
     */
    suspend fun downloadMetadata(webDav: WebDav): Metadata {
        return withContext(Dispatchers.IO) {
            val metadataPath = buildPath(webDav.path, BackupConstants.METADATA_FILE)
            val metadataWebDav = WebDav(metadataPath, webDav.authorization)
            
            return@withContext if (metadataWebDav.exists()) {
                val metadataJson = String(metadataWebDav.download(), Charsets.UTF_8)
                gson.fromJson(metadataJson, Metadata::class.java)
            } else {
                // 创建默认元数据
                Metadata(
                    lastUpdated = Instant.now().toString(),
                    devices = mutableMapOf(),
                    tokens = mutableMapOf()
                )
            }
        }
    }
    
    /**
     * 上传元数据
     * @param webDav WebDav客户端
     * @param metadata 元数据对象
     */
    suspend fun uploadMetadata(webDav: WebDav, metadata: Metadata) {
        withContext(Dispatchers.IO) {
            val metadataPath = buildPath(webDav.path, BackupConstants.METADATA_FILE)
            val metadataWebDav = WebDav(metadataPath, webDav.authorization)
            val metadataJson = gson.toJson(metadata)
            metadataWebDav.upload(metadataJson.toByteArray(), "application/json")
        }
    }
    
    /**
     * 加密并上传核心文件
     * @param webDav WebDav客户端
     * @param uniqueId 唯一标识符
     * @param coreToken CoreToken对象
     * @param encryptionPassword 加密密码
     * @return 加密后核心文件的SHA-256哈希值
     */
    suspend fun uploadCoreFile(webDav: WebDav, uniqueId: String, coreToken: CoreToken, encryptionPassword: String): String {
        return withContext(Dispatchers.IO) {
            // 序列化CoreToken
            val coreJson = gson.toJson(coreToken)
            
            // 加密数据
            val encryptedData = EncryptionUtil.encrypt(coreJson.toByteArray(), encryptionPassword)
            
            // 计算内容哈希
            val contentHash = calculateContentHash(encryptedData)
            
            // 上传文件
            val tokenDir = buildPath(webDav.path, BackupConstants.TOKEN_DIR)
            val tokenWebDavDir = WebDav(tokenDir, webDav.authorization)
            tokenWebDavDir.makeAsDir()
            
            val tokenPath = buildPath(tokenDir, "$uniqueId.token")
            val tokenWebDav = WebDav(tokenPath, webDav.authorization)
            tokenWebDav.upload(encryptedData, "application/octet-stream")
            
            return@withContext contentHash
        }
    }
    
    /**
     * 下载并解密核心文件
     * @param webDav WebDav客户端
     * @param uniqueId 唯一标识符
     * @param password 解密密码
     * @return CoreToken对象
     */
    suspend fun downloadCoreFile(webDav: WebDav, uniqueId: String, password: String): CoreToken {
        return withContext(Dispatchers.IO) {
            val tokenPath = buildPath(webDav.path, BackupConstants.TOKEN_DIR, "$uniqueId.token")
            val tokenWebDav = WebDav(tokenPath, webDav.authorization)
            
            val encryptedData = tokenWebDav.download()
            val decryptedData = EncryptionUtil.decrypt(encryptedData, password)
            val coreJson = String(decryptedData, Charsets.UTF_8)
            
            return@withContext gson.fromJson(coreJson, CoreToken::class.java)
        }
    }
    
    /**
     * 上传图标文件
     * @param webDav WebDav客户端
     * @param uniqueId 唯一标识符
     * @param imageFile 图标文件
     */
    suspend fun uploadIconFile(webDav: WebDav, uniqueId: String, imageFile: File) {
        withContext(Dispatchers.IO) {
            val iconDir = buildPath(webDav.path, BackupConstants.ICON_DIR)
            val iconWebDavDir = WebDav(iconDir, webDav.authorization)
            iconWebDavDir.makeAsDir()
            
            val iconPath = buildPath(iconDir, "$uniqueId.png")
            val iconWebDav = WebDav(iconPath, webDav.authorization)
            iconWebDav.upload(imageFile, "image/png")
        }
    }
    
    /**
     * 下载图标文件
     * @param webDav WebDav客户端
     * @param uniqueId 唯一标识符
     * @return 图标文件的字节数组
     */
    suspend fun downloadIconFile(webDav: WebDav, uniqueId: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            val iconPath = buildPath(webDav.path, BackupConstants.ICON_DIR, "$uniqueId.png")
            val iconWebDav = WebDav(iconPath, webDav.authorization)
            
            return@withContext if (iconWebDav.exists()) {
                iconWebDav.download()
            } else {
                null
            }
        }
    }
    
    /**
     * 保存图标到本地
     * @param uniqueId 唯一标识符
     * @param iconData 图标数据
     * @return 本地文件路径
     */
    suspend fun saveIconToLocal(uniqueId: String, iconData: ByteArray, context: android.content.Context): String {
        return withContext(Dispatchers.IO) {
            val iconDir = File(context.filesDir, "icons")
            iconDir.mkdirs()
            
            val iconFile = File(iconDir, "$uniqueId.png")
            iconFile.writeBytes(iconData)
            
            return@withContext iconFile.absolutePath
        }
    }
    
    /**
     * 备份令牌到WebDAV
     * @param webDav WebDav客户端
     * @param tokens 要备份的令牌列表
     * @param encryptionPassword 加密密码
     * @param deviceId 设备ID
     * @param context Android上下文，用于访问资源文件
     * @param onProgress 进度回调，范围0-100
     */
    suspend fun backupTokens(webDav: WebDav, tokens: List<OtpToken>, encryptionPassword: String, deviceId: String, context: android.content.Context, onProgress: ((Int) -> Unit)? = null) {
        withContext(Dispatchers.IO) {
            // 创建必要的目录
            webDav.makeAsDir()
            WebDav(buildPath(webDav.path, BackupConstants.TOKEN_DIR), webDav.authorization).makeAsDir()
            WebDav(buildPath(webDav.path, BackupConstants.ICON_DIR), webDav.authorization).makeAsDir()
            
            onProgress?.invoke(10)
            
            // 下载现有元数据
            val metadata = downloadMetadata(webDav)
            val now = Instant.now().toString()
            
            onProgress?.invoke(20)
            
            // 更新设备信息
            val deviceInfo = metadata.devices.getOrPut(deviceId) { DeviceInfo(now) }
            deviceInfo.lastSyncAt = now
            
            // 处理每个令牌
            for ((index, token) in tokens.withIndex()) {
                val uniqueId = token.uniqueId // 直接使用数据库中已存储的uniqueId，无需重新生成
                val coreToken = token.toCoreToken()
                
                // 上传核心文件
                val contentHash = uploadCoreFile(webDav, uniqueId, coreToken, encryptionPassword)
                
                // 上传图标文件（如果有）
                if (token.imagePath != null) {
                    val imageFile = File(token.imagePath!!)
                    if (imageFile.exists()) {
                        uploadIconFile(webDav, uniqueId, imageFile)
                    }
                }
                
                // 更新元数据
                val tokenMetadata = metadata.tokens.getOrPut(uniqueId) {
                    TokenMetadata(
                        issuer = token.issuer,
                        label = token.label,
                        sort = token.ordinal,
                        imagePath = token.imagePath?.let { "/${BackupConstants.ICON_DIR}/$uniqueId.png" },
                        contentHash = contentHash,
                        updatedAt = now
                    )
                }
                
                tokenMetadata.issuer = token.issuer
                tokenMetadata.label = token.label
                tokenMetadata.sort = token.ordinal
                tokenMetadata.contentHash = contentHash
                tokenMetadata.updatedAt = now
                
                // 更新进度
                val progress = 30 + (index + 1) * 50 / tokens.size
                onProgress?.invoke(progress)
            }
            
            // 更新元数据的最后更新时间
            metadata.lastUpdated = now
            
            // 上传更新后的元数据
            uploadMetadata(webDav, metadata)
            
            onProgress?.invoke(90)
            
            // 上传解密脚本
            uploadDecryptionScript(webDav, context)
            
            onProgress?.invoke(100)
        }
    }
    
    /**
     * 从WebDAV恢复令牌
     * @param webDav WebDav客户端
     * @param password 解密密码
     * @param deviceId 设备ID
     * @param onProgress 进度回调，范围0-100
     * @return 恢复的令牌列表
     */
    suspend fun restoreTokens(webDav: WebDav, password: String, deviceId: String, onProgress: ((Int) -> Unit)? = null): List<OtpToken> {
        return withContext(Dispatchers.IO) {
            val restoredTokens = mutableListOf<OtpToken>()
            
            // 下载元数据
            val metadata = downloadMetadata(webDav)
            val now = Instant.now().toString()
            
            onProgress?.invoke(10)
            
            // 更新设备同步时间
            val deviceInfo = metadata.devices.getOrPut(deviceId) { DeviceInfo(now) }
            deviceInfo.lastSyncAt = now
            
            val tokenCount = metadata.tokens.size
            if (tokenCount == 0) {
                onProgress?.invoke(100)
                return@withContext restoredTokens
            }
            
            // 处理每个令牌
            val tokenEntries = metadata.tokens.entries.toList()
            for (index in tokenEntries.indices) {
                val entry = tokenEntries[index]
                try {
                    val uniqueId = entry.key
                    val tokenMetadata = entry.value
                    
                    // 下载并解密核心文件
                    val coreToken = downloadCoreFile(webDav, uniqueId, password)
                    
                    // 验证内容哈希
                    val tokenPath = buildPath(webDav.path, BackupConstants.TOKEN_DIR, "$uniqueId.token")
                    val tokenWebDav = WebDav(tokenPath, webDav.authorization)
                    val encryptedData = tokenWebDav.download()
                    val actualHash = calculateContentHash(encryptedData)
                    
                    if (actualHash == tokenMetadata.contentHash) {
                        // 下载图标文件（如果有）
                        // 暂时不处理图标文件，因为需要上下文
                        var localImagePath: String? = null
                        
                        // 创建OtpToken对象
                        val otpToken = coreToken.toOtpToken(tokenMetadata)
                        restoredTokens.add(otpToken)
                    }
                } catch (e: Exception) {
                    // 跳过恢复失败的令牌
                    e.printStackTrace()
                } finally {
                    // 更新进度
                    val progress = 20 + (index + 1) * 70 / tokenCount
                    onProgress?.invoke(progress)
                }
            }
            
            // 更新元数据的最后更新时间
            metadata.lastUpdated = now
            uploadMetadata(webDav, metadata)
            
            onProgress?.invoke(100)
            
            return@withContext restoredTokens
        }
    }
    
    /**
     * 上传解密脚本到WebDAV
     * @param webDav WebDav客户端
     * @param context Android上下文，用于访问资源文件
     */
    private suspend fun uploadDecryptionScript(webDav: WebDav, context: android.content.Context) {
        withContext(Dispatchers.IO) {
            // 从res/raw目录中读取解密脚本
            val scriptResId = context.resources.getIdentifier(
                BackupConstants.DECRYPT_SCRIPT_FILE.replace(".py", ""),
                "raw",
                context.packageName
            )
            
            val scriptContent = context.resources.openRawResource(scriptResId).use { inputStream ->
                inputStream.readBytes()
            }
            
            val scriptPath = buildPath(webDav.path, BackupConstants.DECRYPT_SCRIPT_FILE)
            val scriptWebDav = WebDav(scriptPath, webDav.authorization)
            scriptWebDav.upload(scriptContent, "text/x-python")
        }
    }
}
