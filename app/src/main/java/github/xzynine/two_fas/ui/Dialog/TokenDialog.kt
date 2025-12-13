package github.xzynine.two_fas.ui.Dialog

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import github.xzynine.two_fas.data.OtpToken
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.icons.basic.Check

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
    show: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (OtpToken) -> Unit
) {
    var issuer by remember { mutableStateOf(token?.issuer ?: "") }
    var label by remember { mutableStateOf(token?.label ?: "") }
    var secret by remember { mutableStateOf(token?.secret ?: "") }
    var secretVisible by remember { mutableStateOf(false) }
    val isEditMode = token != null

    SuperDialog(
        title = if (isEditMode) "编辑令牌" else "手动输入密钥/otpauth URI",
        summary = if (isEditMode) "修改令牌信息" else "请输入密钥或 otpauth URI",
        show = remember { mutableStateOf(show) },
        onDismissRequest = onDismiss,
        defaultWindowInsetsPadding = true, // 启用默认窗口插入内边距，正确处理输入法
        insideMargin = DpSize(16.dp, 16.dp) // 设置内部边距
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // 编辑区域
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 发行者输入框
                TextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    label = "发行者",
                    modifier = Modifier.Companion.fillMaxWidth(),
                    singleLine = true
                )

                // 标签输入框
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "标签",
                    modifier = Modifier.Companion.fillMaxWidth(),
                    singleLine = true
                )

                // 密钥输入框
                TextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = "密钥/URI",
                    modifier = Modifier.Companion.fillMaxWidth(),
                    readOnly = isEditMode, // 编辑模式下密钥不可修改
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

            // 操作按钮区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧按钮：编辑模式显示"删除"，手动输入模式显示"取消"
                Button(
                    onClick = if (isEditMode && onDelete != null) onDelete else onDismiss
                ) {
                    Text(text = if (isEditMode) "删除" else "取消")
                }

                // 右侧按钮：编辑模式显示"保存"，手动输入模式显示"添加"
                Button(
                    onClick = {
                        val finalToken = if (isEditMode) {
                            // 编辑现有令牌
                            token.copy(
                                issuer = if (issuer.isBlank()) null else issuer,
                                label = label,
                                secret = secret
                            )
                        } else {
                            // 手动输入新令牌，需要通过 OtpTokenFactory 创建
                            // 注意：这里需要根据 secret 内容判断是 URI 还是纯密钥
                            val uri = try {
                                val parsedUri = Uri.parse(secret)
                                if (parsedUri.scheme != null) {
                                    parsedUri
                                } else {
                                    // 纯密钥，构建默认的 otpauth URI
                                    val finalIssuer = if (issuer.isBlank()) null else issuer
                                    val finalLabel = if (label.isBlank()) "Manual" else label
                                    val issuerPart = if (finalIssuer != null) "${finalIssuer}:%20" else ""
                                    Uri.parse("otpauth://totp/${issuerPart}${finalLabel}?secret=${secret}&algorithm=SHA1&digits=6&period=30")
                                }
                            } catch (e: Exception) {
                                // 解析失败，构建默认的 otpauth URI
                                val finalIssuer = if (issuer.isBlank()) null else issuer
                                val finalLabel = if (label.isBlank()) "Manual" else label
                                val issuerPart = if (finalIssuer != null) "${finalIssuer}:%20" else ""
                                Uri.parse("otpauth://totp/${issuerPart}${finalLabel}?secret=${secret}&algorithm=SHA1&digits=6&period=30")
                            }
                            github.xzynine.two_fas.data.OtpTokenFactory.createFromUri(uri)
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
