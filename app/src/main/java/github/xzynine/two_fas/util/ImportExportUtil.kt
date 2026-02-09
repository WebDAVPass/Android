package github.xzynine.two_fas.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import github.xzynine.two_fas.data.OtpToken
import github.xzynine.two_fas.data.AppDatabase
import github.xzynine.two_fas.data.legacy.SavedTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * 导入导出工具类
 */
class ImportExportUtil(
    private val context: Context,
    private val migrationUtil: MigrationUtil,
    private val gson: Gson,
    private val appDatabase: AppDatabase
) {
    
    /**
     * 导入 JSON 文件
     */
    suspend fun importJsonFile(uri: Uri) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri).use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                reader.readText()
            } .let {
                val savedTokens = gson.fromJson(it, SavedTokens::class.java)
                val newTokens = migrationUtil.convertLegacySavedTokensToOtpTokens(savedTokens)
                appDatabase.otpTokenDao().insertAll(newTokens)
            }
        }
    }

    /**
     * 导出 JSON 文件
     */
    suspend fun exportJsonFile(uri: Uri) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "w").use { outputStream ->
                val otpTokens = appDatabase.otpTokenDao().getAll().first()

                val legacyTokens = migrationUtil.convertOtpTokensToLegacyTokens(otpTokens)
                val tokenOrder = otpTokens.map {
                    if (it.issuer != null) {
                        "${it.issuer}:${it.label}"
                    } else {
                        it.label
                    }
                }.toList()

                val jsonString = gson.toJson(SavedTokens(legacyTokens, tokenOrder))

                outputStream?.write(jsonString.toByteArray())
            }
        }
    }

    /**
     * 导入 Key URI 文件
     */
    suspend fun importKeyUriFile(fileUri: Uri) {
        withContext(Dispatchers.IO) {
            val currentLastOrdinal = appDatabase.otpTokenDao().getLastOrdinal() ?: 0

            context.contentResolver.openInputStream(fileUri)?.reader()?.use { reader ->
                reader.readLines().filter {
                    it.isNotBlank()
                }.mapIndexed { index, line ->
                    // 简化实现：直接创建基础令牌
                    createBasicTokenFromUri(line.trim(), currentLastOrdinal + index + 1)
                }
            } ?.let { tokens ->
                appDatabase.otpTokenDao().insertAll(tokens)
            }
        }
    }

    /**
     * 导出 Key URI 文件
     */
    suspend fun exportKeyUriFile(fileUri: Uri) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(fileUri, "w")?.use { outputStream ->
                PrintWriter(outputStream).use { printWriter ->
                    val tokens = appDatabase.otpTokenDao().getAll().first()
                    for (token in tokens) {
                        printWriter.println(toUri(token).toString())
                    }
                }
            }
        }
    }

    /**
     * 从 URI 创建基础令牌（简化实现）
     */
    private fun createBasicTokenFromUri(uriString: String, ordinal: Long): OtpToken {
        // 简化实现：解析 URI 并创建基础令牌
        val uri = Uri.parse(uriString)
        val path = uri.path ?: ""
        
        // 解析发行商和标签
        val issuer = uri.getQueryParameter("issuer")
        val label = path.substringAfterLast("/")
        val description = uri.getQueryParameter("description")
        
        // 解析其他参数
        val secret = uri.getQueryParameter("secret") ?: ""
        val algorithm = uri.getQueryParameter("algorithm") ?: "SHA1"
        val digits = uri.getQueryParameter("digits")?.toIntOrNull() ?: 6
        val period = uri.getQueryParameter("period")?.toIntOrNull() ?: 30
        
        // 判断令牌类型
        val tokenType = if (uri.scheme == "otpauth" && uri.host == "totp") {
            github.xzynine.two_fas.data.OtpTokenType.TOTP
        } else {
            github.xzynine.two_fas.data.OtpTokenType.HOTP
        }

        // 生成唯一标识符
        val uniqueId = UniqueIdGenerator.generate(secret, algorithm, digits, period)
        
        return github.xzynine.two_fas.data.OtpToken(
            id = 0,
            ordinal = 0,
            issuer = issuer,
            label = label,
            description = description,
            imagePath = null,
            tokenType = tokenType,
            algorithm = algorithm,
            secret = secret,
            digits = digits,
            counter = 0,
            period = period,
            encryptionType = github.xzynine.two_fas.data.EncryptionType.NONE,
            uniqueId = uniqueId
        )
    }

    /**
     * 将令牌转换为 URI
     */
    private fun toUri(token: OtpToken): Uri {
        val builder = Uri.Builder()
            .scheme("otpauth")
            .authority(when (token.tokenType) {
                github.xzynine.two_fas.data.OtpTokenType.HOTP -> "hotp"
                github.xzynine.two_fas.data.OtpTokenType.TOTP -> "totp"
            })
            .path("/${token.issuer ?: ""}:${token.label}")
            .appendQueryParameter("secret", token.secret)
            .appendQueryParameter("algorithm", token.algorithm)
            .appendQueryParameter("digits", token.digits.toString())

        token.issuer?.let { builder.appendQueryParameter("issuer", it) }
        token.description?.let { builder.appendQueryParameter("description", it) }
        
        if (token.tokenType == github.xzynine.two_fas.data.OtpTokenType.TOTP) {
            builder.appendQueryParameter("period", token.period.toString())
        } else {
            builder.appendQueryParameter("counter", token.counter.toString())
        }

        return builder.build()
    }
}