package github.xzynine.two_fas.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * OTP令牌实体类
 */
@Entity(tableName = "otp_tokens")
data class OtpToken (
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ordinal: Long,
    val issuer: String?,
    val label: String,
    val imagePath: String?,
    val tokenType: OtpTokenType,
    val algorithm: String,
    val secret: String,
    val digits: Int,
    val counter: Long,
    val period: Int,
    val encryptionType: EncryptionType
)