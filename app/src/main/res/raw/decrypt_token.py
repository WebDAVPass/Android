#!/usr/bin/env python3
"""
TOTP Token Decryption Script

Usage:
    python decrypt_token.py <token_file> <password>
"""

import sys
import json
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend


class BackupConstants:
    class Encryption:
        ALGORITHM = "AES/GCM/NoPadding"
        KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        KEY_LENGTH = 256
        ITERATIONS = 100000
        SALT_LENGTH = 16
        IV_LENGTH = 12
        TAG_LENGTH = 16


def derive_key(password: str, salt: bytes) -> bytes:
    """使用PBKDF2派生AES密钥"""
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=BackupConstants.Encryption.KEY_LENGTH // 8,  # 转换为字节长度
        salt=salt,
        iterations=BackupConstants.Encryption.ITERATIONS,
        backend=default_backend()
    )
    return kdf.derive(password.encode())


def decrypt_token(encrypted_data: bytes, password: str) -> dict:
    """解密令牌数据"""
    # 解析加密数据
    salt = encrypted_data[0:BackupConstants.Encryption.SALT_LENGTH]
    iv = encrypted_data[BackupConstants.Encryption.SALT_LENGTH:BackupConstants.Encryption.SALT_LENGTH + BackupConstants.Encryption.IV_LENGTH]
    # AES-GCM自动将认证标签附加到密文末尾
    ciphertext_with_tag = encrypted_data[BackupConstants.Encryption.SALT_LENGTH + BackupConstants.Encryption.IV_LENGTH:]
    
    # 派生密钥
    key = derive_key(password, salt)
    
    # 解密
    cipher = Cipher(
        algorithms.AES(key),
        modes.GCM(iv),
        backend=default_backend()
    )
    decryptor = cipher.decryptor()
    plaintext = decryptor.update(ciphertext_with_tag) + decryptor.finalize()
    
    # 解析JSON
    return json.loads(plaintext)


def main():
    if len(sys.argv) != 3:
        print("Usage: python decrypt_token.py <token_file> <password>")
        sys.exit(1)
    
    token_file = sys.argv[1]
    password = sys.argv[2]
    
    try:
        # 读取加密文件
        with open(token_file, 'rb') as f:
            encrypted_data = f.read()
        
        # 解密
        token_data = decrypt_token(encrypted_data, password)
        
        # 输出结果
        print(json.dumps(token_data, indent=2))
        
    except Exception as e:
        print(f"Error: {str(e)}")
        sys.exit(1)


if __name__ == "__main__":
    main()