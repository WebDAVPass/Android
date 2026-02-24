package xzynine.WebDAVPass.Android.ui.Dialog

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import xzynine.WebDAVPass.Android.data.OtpToken
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check

/**
 * 验证字符串是否为有效的 Base32 格式
 */
private fun isValidBase32(input: String): Boolean {
    // Base32 字符集：A-Z, 2-7
    val base32Regex = Regex("^[A-Z2-7]+")
    return base32Regex.matches(input.uppercase())
}

/**
 * 令牌对话框，用于编辑令牌或手动输入密钥
 * @param token 要编辑的令牌，null 表示手动输入新令牌
 * @param show 是否显示对话框
 * @param onDismiss 关闭对话框的回调
 * @param onDelete 删除令牌的回调，仅在编辑现有令牌时有效
 * @param onSave 保存令牌的回调
 */
@Composable
    fun TokenDialog(
        token: OtpToken?,
        show: MutableState<Boolean>,
        onDismiss: () -> Unit,
        onDelete: (() -> Unit)? = null,
        onSave: (OtpToken) -> Unit
    ) {
        val context = LocalContext.current
        var issuer by remember { mutableStateOf(token?.issuer ?: "") }
        var label by remember { mutableStateOf(token?.label ?: "") }
        var description by remember { mutableStateOf(token?.description ?: "") }
        var secret by remember { mutableStateOf(token?.secret ?: "") }
        var secretVisible by remember { mutableStateOf(false) }
        val isEditMode = token != null

    WindowDialog(
        title = if (isEditMode) "编辑令牌" else "手动输入密钥/otpauth URI",
        summary = if (isEditMode) "修改令牌信息" else "请输入密钥或 otpauth URI",
        show = show,
        onDismissRequest = onDismiss,
        defaultWindowInsetsPadding = true,
        insideMargin = DpSize(16.dp, 16.dp)
    ) {
        BackHandler(enabled = true) {
            onDismiss()
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    label = "发行者",
                    modifier = Modifier.Companion.fillMaxWidth(),
                    singleLine = true
                )

                TextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "标签",
                    modifier = Modifier.Companion.fillMaxWidth(),
                    singleLine = true
                )

                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "描述（包名或备注）",
                    modifier = Modifier.Companion.fillMaxWidth(),
                    singleLine = true
                )

                TextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = "密钥/URI",
                    modifier = Modifier.Companion.fillMaxWidth(),
                    readOnly = isEditMode,
                    singleLine = true,
                    visualTransformation = if (secretVisible || !isEditMode) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = if (isEditMode) {
                        { IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(
                                imageVector = if (secretVisible) MiuixIcons.Basic.Check else MiuixIcons.Basic.ArrowRight,
                                contentDescription = if (secretVisible) "隐藏密钥" else "显示密钥"
                            )
                        }}
                    } else {
                        null
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = if (isEditMode && onDelete != null) onDelete else onDismiss
                ) {
                    Text(text = if (isEditMode) "删除" else "取消")
                }

                Button(
                    onClick = {
                        val finalToken = if (isEditMode) {
                            token.copy(
                                issuer = if (issuer.isBlank()) null else issuer,
                                label = label,
                                description = description.ifBlank { null },
                                secret = secret
                            )
                        } else {
                            // 验证输入
                            val tokenResult = runCatching {
                                val uri = run {
                                    val parsedUri = Uri.parse(secret)
                                    if (parsedUri.scheme != null) {
                                        // URL 格式，验证其中的 secret 参数是否为有效的 Base32 格式
                                        val secretParam = parsedUri.getQueryParameter("secret")
                                        if (secretParam != null && !isValidBase32(secretParam)) {
                                            throw IllegalArgumentException("无效的密钥格式，请输入有效的 Base32 字符")
                                        }
                                        parsedUri
                                    } else {
                                        // 纯密钥，验证是否为有效的 Base32 格式
                                        if (!isValidBase32(secret)) {
                                            throw IllegalArgumentException("无效的密钥格式，请输入有效的 Base32 字符")
                                        }
                                        // 构建默认的 otpauth URI
                                        val finalIssuer = if (issuer.isBlank()) null else issuer
                                        val finalLabel = if (label.isBlank()) "Manual" else label
                                        val issuerPart = if (finalIssuer != null) "${finalIssuer}: %20" else ""
                                        Uri.parse("otpauth://totp/${issuerPart}${finalLabel}?secret=${secret}&algorithm=SHA1&digits=6&period=30")
                                    }
                                }
                                xzynine.WebDAVPass.Android.data.OtpTokenFactory.createFromUri(uri)
                                    .copy(description = description.ifBlank { null })
                            }
                            if (tokenResult.isSuccess) {
                                tokenResult.getOrThrow()
                            } else {
                                // 显示错误提示并中断保存
                                Toast.makeText(
                                    context,
                                    tokenResult.exceptionOrNull()?.message ?: "无效的密钥或URI格式，请检查输入",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                        }
                        onSave(finalToken)
                    }
                ) {
                    Text(text = if (isEditMode) "保存" else "添加")
                }
            }
        }
    }
}
