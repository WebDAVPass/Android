package xzynine.WebDAVPass.Android.ui.Screen

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import xzynine.WebDAVPass.Android.theme.AppTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.activity.PasswordDetailActivity
import xzynine.WebDAVPass.Android.ui.activity.TokenDetailActivity

/**
 * 功能卡片组件
 */
@Composable
fun FeatureCard(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(100.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainerHighest
        ),
        cornerRadius = CardDefaults.CornerRadius,
        insideMargin = CardDefaults.InsideMargin,
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 首页块状布局界面
 */
@Composable
fun HomeScreen(tokenViewModel: TokenViewModel) {
    val context = LocalContext.current
    val tokens by tokenViewModel.tokens.collectAsState(emptyList())
    val passwordTotalCount by tokenViewModel.passwordTotalCount.collectAsState(0)
    val tokenCount by remember {
        derivedStateOf {
            tokens.size
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 功能块
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 密码
                    FeatureCard(
                        title = "密码",
                        value = "$passwordTotalCount",
                        onClick = {
                            val intent = Intent(context, PasswordDetailActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // 动态令牌
                    FeatureCard(
                        title = "动态令牌",
                        value = "$tokenCount",
                        onClick = {
                            // 跳转到令牌列表
                            val intent = Intent(context, TokenDetailActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 安全相关
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 安全性
                    FeatureCard(
                        title = "安全性",
                        value = "0",
                        onClick = {
                            // TODO: tos提示待开发
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // 最近删除
                    FeatureCard(
                        title = "最近删除",
                        value = "0",
                        onClick = {
                            // TODO: tos提示待开发
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
