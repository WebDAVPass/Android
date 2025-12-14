package github.xzynine.two_fas.data

/**
 * 备份相关常量定义
 */
object BackupConstants {
    /**
     * 备份模式
     */
    const val SCHEMA_VERSION = "1.0"
    
    /**
     * 核心文件存储目录
     */
    const val TOKEN_DIR = "token"
    
    /**
     * 元数据文件名
     */
    const val METADATA_FILE = "metadata.json"
    
    /**
     * 解密脚本文件名
     */
    const val DECRYPT_SCRIPT_FILE = "decrypt_token.py"
    
    /**
     * 加密算法相关常量
     */
    object Encryption {
        /**
         * 加密算法
         */
        const val ALGORITHM = "AES/GCM/NoPadding"
        
        /**
         * 密钥派生算法
         */
        const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        
        /**
         * 密钥长度（256位）
         */
        const val KEY_LENGTH = 256
        
        /**
         * 迭代次数
         */
        const val ITERATIONS = 100000
        
        /**
         * 盐长度（16字节）
         */
        const val SALT_LENGTH = 16
        
        /**
         * IV长度（12字节）
         */
        const val IV_LENGTH = 12
        
        /**
         * 认证标签长度（16字节）
         */
        const val TAG_LENGTH = 16
    }
}