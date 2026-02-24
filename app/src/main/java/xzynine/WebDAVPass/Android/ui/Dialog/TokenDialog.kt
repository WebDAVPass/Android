package xzynine.WebDAVPass.Android.ui.Dialog

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
    var description by remember { mutableStateOf(token?.description ?: "") }
    var secret by remember { mutableStateOf(token?.secret ?: "") }
    var secretVisible by remember { mutableStateOf(false) }
    val isEditMode = token != null
    
    val showState = remember { mutableStateOf(show) }
    showState.value = show

    WindowDialog(
        title = if (isEditMode) "编辑令牌" else "手动输入密钥/otpauth URI",
        summary = if (isEditMode) "修改令牌信息" else "请输入密钥或 otpauth URI",
        show = showState,
        onDismissRequest = onDismiss,
        defaultWindowInsetsPadding = true,
        insideMargin = DpSize(16.dp, 16.dp)
    ) {
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
                            val uri = try {
                                val parsedUri = Uri.parse(secret)
                                if (parsedUri.scheme != null) {
                                    parsedUri
                                } else {
                                    val finalIssuer = if (issuer.isBlank()) null else issuer
                                    val finalLabel = if (label.isBlank()) "Manual" else label
                                    val issuerPart = if (finalIssuer != null) "${finalIssuer}:%20" else ""
                                    Uri.parse("otpauth://totp/${issuerPart}${finalLabel}?secret=${secret}&algorithm=SHA1&digits=6&period=30")
                                }
                            } catch (e: Exception) {
                                val finalIssuer = if (issuer.isBlank()) null else issuer
                                val finalLabel = if (label.isBlank()) "Manual" else label
                                val issuerPart = if (finalIssuer != null) "${finalIssuer}:%20" else ""
                                Uri.parse("otpauth://totp/${issuerPart}${finalLabel}?secret=${secret}&algorithm=SHA1&digits=6&period=30")
                            }
                            xzynine.WebDAVPass.Android.data.OtpTokenFactory.createFromUri(uri)
                                .copy(description = description.ifBlank { null })
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
