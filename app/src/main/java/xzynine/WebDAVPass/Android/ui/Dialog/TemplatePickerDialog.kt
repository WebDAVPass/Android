package xzynine.WebDAVPass.Android.ui.Dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunzisoft.keepass.database.element.template.Template
import com.kunzisoft.keepass.database.element.template.TemplateBuilder
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog
import xzynine.WebDAVPass.Android.ui.component.EntryIcon

/**
 * 模板选择对话框。
 *
 * 提供内置规范化模板（邮件/Wi-Fi/安全笔记/身份证/银行卡/银行账户/加密货币）与「自定义」，
 * 选中后回调 [onPick]；「自定义」回调 null。
 */
@Composable
fun TemplatePickerDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onPick: (Template?) -> Unit,
) {
    val templates =
        remember {
            TemplateBuilder().let { builder ->
                listOf(
                    builder.email,
                    builder.wifi,
                    builder.notes,
                    builder.idCard,
                    builder.creditCard,
                    builder.bank,
                    builder.cryptocurrency,
                )
            }
        }

    WindowDialog(
        title = "从模板添加",
        summary = "选择模板将自动生成对应字段",
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(templates, key = { it.uuid.toString() }) { template ->
                    val fieldLabels =
                        template.sections
                            .flatMap { section -> section.attributes.map { localizeTemplateLabel(it.label) } }
                            .distinct()
                            .joinToString(" / ")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                        cornerRadius = 12.dp,
                        pressFeedbackType = PressFeedbackType.Sink,
                        showIndication = true,
                        onClick = { onPick(template) },
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EntryIcon(
                                customIconBytes = null,
                                standardIconId = template.icon.standard.id,
                                primary = localizeTemplateLabel(template.title),
                                secondary = null,
                                modifier = Modifier.size(36.dp),
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(
                                    text = localizeTemplateLabel(template.title),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = fieldLabels,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                }
            }

            // 自定义（无模板）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                cornerRadius = 12.dp,
                pressFeedbackType = PressFeedbackType.Sink,
                showIndication = true,
                onClick = { onPick(null) },
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EntryIcon(
                        customIconBytes = null,
                        standardIconId = 0,
                        primary = "自定义",
                        secondary = null,
                        modifier = Modifier.size(36.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            text = "自定义",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "不使用模板，手动添加字段",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 将模板标题与字段标签映射为中文显示文本。
 * 仅影响界面展示，不修改实际写入数据库的字段名。
 */
private fun localizeTemplateLabel(label: String): String =
    when (label) {
        "Email" -> "电子邮件"
        "Email address" -> "邮箱地址"
        "Wi-Fi" -> "无线网络"
        "Secure Note" -> "安全笔记"
        "ID Card" -> "身份证"
        "Debit / Credit Card" -> "银行卡"
        "Bank" -> "银行"
        "Cryptocurrency wallet" -> "加密货币钱包"
        "Membership" -> "会员"
        "Title" -> "标题"
        "Username" -> "用户名"
        "Password" -> "密码"
        "URL" -> "网址"
        "Expires" -> "过期时间"
        "Notes" -> "备注"
        "Holder" -> "持有人"
        "Number" -> "号码"
        "Name" -> "姓名"
        "Place of issue" -> "签发地"
        "Date of issue" -> "签发日期"
        "SSID" -> "SSID"
        "Type" -> "类型"
        "Token" -> "代币"
        "Public key" -> "公钥"
        "Private key" -> "私钥"
        "Seed" -> "助记词"
        "Account" -> "账户"
        "CVV" -> "安全码(CVV)"
        "PIN" -> "PIN码"
        "BIC" -> "银行识别码(BIC)"
        "IBAN" -> "国际银行账号(IBAN)"
        "Standard" -> "标准"
        "Template" -> "模板"
        "Version" -> "版本"
        else -> label
    }
