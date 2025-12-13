package github.xzynine.two_fas.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import github.xzynine.two_fas.data.OtpToken
import github.xzynine.two_fas.viewmodel.TokenViewModel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator

/**
 * 令牌列表界面
 */
@Composable
fun TokenListScreen() {
    val context = LocalContext.current
    val tokenViewModel: TokenViewModel = remember { TokenViewModel(context) }
    val tokens by tokenViewModel.tokens.collectAsState(emptyList())
    val isLoading by tokenViewModel.isLoading.collectAsState(false)

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (tokens.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "暂无令牌，请添加新的2FA令牌")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(tokens) { token ->
                TokenItem(token = token, tokenViewModel = tokenViewModel)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 单个令牌项组件
 */
@Composable
fun TokenItem(token: OtpToken, tokenViewModel: TokenViewModel) {
    val tokenCode by tokenViewModel.getTokenCode(token.id).collectAsState(null)
    
    // 实时更新的时间状态，用于倒计时显示
    val currentTime by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = token.issuer ?: token.label,
                fontSize = 16.sp
            )
            if (token.issuer != null) {
                Text(
                    text = token.label,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.outline
                )
            }
        }
        
        tokenCode?.let { code ->
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = code.code,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                // 显示剩余时间，使用实时更新的时间状态
                val remainingTime = maxOf(0, (code.end - currentTime) / 1000)
                Text(
                    text = "${remainingTime}s",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.outline
                )
            }
        }
    }
}