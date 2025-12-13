package github.xzynine.two_fas.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import github.xzynine.two_fas.data.OtpToken
import github.xzynine.two_fas.viewmodel.TokenViewModel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Delete
import top.yukonga.miuix.kmp.icon.icons.useful.Edit

/**
 * 弹窗状态枚举
 */
enum class DialogState { NONE, EDIT, DELETE }

/**
 * 令牌列表界面
 */
@Composable
fun TokenListScreen() {
    val context = LocalContext.current
    val tokenViewModel: TokenViewModel = remember { TokenViewModel(context) }
    val tokens by tokenViewModel.tokens.collectAsState(emptyList())
    val isLoading by tokenViewModel.isLoading.collectAsState(false)
    
    // 长按功能状态管理 - 使用枚举确保单例
    var dialogState by remember { mutableStateOf(DialogState.NONE) }
    var selectedToken by remember { mutableStateOf<OtpToken?>(null) }

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
                TokenItem(
                    token = token, 
                    tokenViewModel = tokenViewModel,
                    onLongClick = {
                        selectedToken = token
                        dialogState = DialogState.EDIT
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp
                )
            }
        }
        
        // 根据状态显示对应的弹窗
        when (dialogState) {
            DialogState.EDIT -> selectedToken?.let { token ->
                    EditTokenDialog(
                        token = token,
                        show = true,
                        onDismiss = { 
                            dialogState = DialogState.NONE
                            selectedToken = null
                        },
                        onDelete = {
                            dialogState = DialogState.DELETE
                        },
                        onSave = { updatedToken ->
                            tokenViewModel.updateToken(updatedToken)
                            dialogState = DialogState.NONE
                            selectedToken = null
                        }
                    )
                }
            
            DialogState.DELETE -> selectedToken?.let { token ->
                DeleteConfirmationDialog(
                    token = token,
                    show = true,
                    onDismiss = { 
                        dialogState = DialogState.NONE
                        selectedToken = null
                    },
                    onConfirm = {
                        tokenViewModel.deleteToken(token.id)
                        dialogState = DialogState.NONE
                        selectedToken = null
                    }
                )
            }
            
            DialogState.NONE -> { /* 不显示任何弹窗 */ }
        }
    }
}

/**
 * 编辑令牌对话框
 */
@Composable
fun EditTokenDialog(
    token: OtpToken,
    show: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (OtpToken) -> Unit
) {
    var issuer by remember { mutableStateOf(token.issuer ?: "") }
    var label by remember { mutableStateOf(token.label) }
    var secret by remember { mutableStateOf(token.secret) }

    SuperDialog(
        title = "编辑令牌",
        summary = "修改令牌信息",
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
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 标签输入框
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "标签",
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 密钥输入框
                TextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = "密钥",
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true // 密钥通常不建议修改
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 操作按钮区域
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 删除按钮
                TextButton(
                    text = "删除",
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
                
                // 保存按钮
                TextButton(
                    text = "保存",
                    onClick = {
                        val updatedToken = token.copy(
                            issuer = if (issuer.isBlank()) null else issuer,
                            label = label,
                            secret = secret
                        )
                        onSave(updatedToken)
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

/**
 * 删除确认对话框
 */
@Composable
fun DeleteConfirmationDialog(
    token: OtpToken,
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    SuperDialog(
        title = "确认删除",
        summary = "确定要删除令牌 \"${token.issuer ?: token.label}\" 吗？此操作无法撤销。",
        show = remember { mutableStateOf(show) },
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 取消按钮
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 确认删除按钮
            TextButton(
                text = "删除",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

/**
 * 单个令牌项组件
 */
@Composable
fun TokenItem(token: OtpToken, tokenViewModel: TokenViewModel, onLongClick: () -> Unit) {
    val tokenCode by tokenViewModel.getTokenCode(token.id).collectAsState(null)
    
    // 实时更新的时间状态，用于倒计时显示
    val currentTime by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

    // 长按检测
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 长按计时器
    var longPressTriggered by remember { mutableStateOf(false) }
    
    LaunchedEffect(isPressed) {
        if (isPressed && !longPressTriggered) {
            delay(500) // 长按500ms触发
            if (isPressed) {
                longPressTriggered = true
                onLongClick()
            }
        } else if (!isPressed) {
            longPressTriggered = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
                onClickLabel = "长按编辑令牌"
            ),
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