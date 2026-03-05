package xzynine.WebDAVPass.Android.ui.Dialog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import xzylib.base.util.Logger
import android.util.Size
import android.view.ViewGroup
import xzylib.base.util.ToastUtils  
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.graphics.BitmapFactory
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import xzynine.WebDAVPass.Android.data.OtpTokenFactory
import xzynine.WebDAVPass.Android.util.TokenQRCodeDecoder
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Scan
import java.security.NoSuchAlgorithmException
import java.util.concurrent.Executors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.extra.WindowDialog
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.DelicateCoroutinesApi

/**
 * 扫描二维码界面
 */
@Composable
fun ScanTokenScreen(
    tokenViewModel: TokenViewModel,
    onTokenScanned: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    // 由父组件传入共享的 ViewModel，避免多实例导致状态不一致
    val tokenQRCodeDecoder = remember { TokenQRCodeDecoder() }

    // 相机权限状态
    val hasCameraPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 权限请求启动器
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                ToastUtils.showShortToast(
                    context,
                    "需要相机权限才能扫描二维码"
                )
                onDismiss()
            }
        }
    )

    // 图片选择（上传截图）Launcher：使用 OpenDocument 以支持 4.4+
    var pickedImageError by remember { mutableStateOf<String?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                // 读取位图
                val input = context.contentResolver.openInputStream(uri)
                val bitmap = input.use { stream ->
                    if (stream == null) null else BitmapFactory.decodeStream(stream)
                }
                if (bitmap == null) {
                    pickedImageError = "无法读取图片文件"
                    return@rememberLauncherForActivityResult
                }

                // 复用二维码解析逻辑（期望 TokenQRCodeDecoder 支持位图解析）
                val parseResult = tokenQRCodeDecoder.parseQRCode(bitmap)
                if (parseResult.success) {
                    val tokenString = parseResult.content ?: run {
                        pickedImageError = "二维码内容为空"
                        return@rememberLauncherForActivityResult
                    }

                    try {
                        val uriStr = Uri.parse(tokenString)
                        val token = OtpTokenFactory.createFromUri(uriStr)
                        coroutineScope.launch(Dispatchers.Main) {
                            val added = tokenViewModel.addToken(token)
                            if (added) {
                                ToastUtils.showShortToast(context, "令牌添加成功")
                                onTokenScanned()
                            } else {
                                pickedImageError = "该令牌已存在"
                            }
                        }
                    } catch (e: Exception) {
                        val errorMessage = when (e) {
                            is IllegalArgumentException -> e.message ?: "无效的令牌参数"
                            is NoSuchAlgorithmException -> "不支持的加密算法"
                            else -> "无效的二维码格式"
                        }
                        pickedImageError = errorMessage
                    }
                } else {
                    pickedImageError = when (parseResult.errorType) {
                        TokenQRCodeDecoder.ParseResult.ErrorType.CHECKSUM_ERROR -> "二维码校验和错误"
                        TokenQRCodeDecoder.ParseResult.ErrorType.FORMAT_ERROR -> "二维码格式错误"
                        TokenQRCodeDecoder.ParseResult.ErrorType.NOT_FOUND -> "未检测到二维码"
                        else -> "二维码解析失败"
                    }
                }
            } catch (e: Exception) {
                Logger.e("ImagePicker", "Error: ${e.message}", e)
                pickedImageError = "图片处理失败"
            }
        }
    )

    // 相机提供程序
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // 为每个AndroidView实例创建独立的状态
    val foundToken = remember { mutableStateOf(false) }
    // 缓存最近识别到的URL，用于同一次识别周期内的重复截停
    val lastScannedUrl = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 底部操作面板状态（手动输入）
    val showManualInput = remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }

    MiuixTheme {
        BackHandler(enabled = true) {
            onDismiss()
        }
        
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // 相机区域占页面高度约 3/5，并应用圆角
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.6f)
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            AndroidView(
                factory = { context ->
                    val previewView = PreviewView(context)
                    previewView.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // 设置相机
                    if (hasCameraPermission) {
                        // 初始化相机提供程序并设置相机
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            // 创建预览用例
                            val preview = Preview.Builder()
                                .setResolutionSelector(
                                    ResolutionSelector.Builder()
                                        .setResolutionStrategy(
                                            ResolutionStrategy(
                                                Size(1280, 720),
                                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                            )
                                        )
                                        .build()
                                )
                                .build()
                                .also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                            // 创建图像分析用例
                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setResolutionSelector(
                                    ResolutionSelector.Builder()
                                        .setResolutionStrategy(
                                            ResolutionStrategy(
                                                Size(1280, 720),
                                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                            )
                                        )
                                        .build()
                                )
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(
                                        Executors.newSingleThreadExecutor()
                                    ) { imageProxy ->
                                        // 如果已经找到令牌，直接关闭图像代理
                                        if (foundToken.value) {
                                            imageProxy.close()
                                            return@setAnalyzer
                                        }

                                        // 处理图像
                                        processImageProxy(
                                            context,
                                            imageProxy,
                                            tokenQRCodeDecoder,
                                            tokenViewModel,
                                            onTokenFound = { tokenString ->
                                                // 标记已找到令牌
                                                foundToken.value = true

                                                // 在主线程上更新UI
                                                coroutineScope.launch {
                                                    // 显示成功提示
                                                    ToastUtils.showShortToast(
                                                        context,
                                                        "令牌添加成功"
                                                    )

                                                    // 通知父组件
                                                    onTokenScanned()
                                                }
                                            },
                                            lastScannedUrl = lastScannedUrl
                                        )
                                    }
                                }

                            // 选择后置摄像头
                            val cameraSelector = CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                .build()

                            try {
                                // 清除之前的绑定
                                cameraProvider.unbindAll()

                                // 绑定相机用例
                                camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalyzer
                                )
                            } catch (exc: Exception) {
                                Logger.e("ScanTokenScreen", "无法绑定相机用例", exc)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 底部操作行：其他方法
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(text = "其他方法")
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { showManualInput.value = true }) {
                    Icon(imageVector = MiuixIcons.Back, contentDescription = "手动输入")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "手动输入密钥")
                }
                Button(onClick = {
                    // 选择图片（图片/*），解析二维码并尝试添加
                    imagePickerLauncher.launch(arrayOf("image/*"))
                }) {
                    Icon(imageVector = MiuixIcons.Scan, contentDescription = "上传图片")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "上传带有二维码的截图")
                }
            }
        }
        }
    }

    WindowDialog(
        title = "手动输入密钥",
        summary = "请输入完整 otpauth:// 链接",
        show = showManualInput,
        onDismissRequest = {
            showManualInput.value = false
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = manualInputText,
                onValueChange = { manualInputText = it },
                label = "otpauth 链接",
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        showManualInput.value = false
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val raw = manualInputText.trim()
                        if (raw.isBlank()) {
                            pickedImageError = "请输入 otpauth 链接"
                            return@Button
                        }

                        coroutineScope.launch {
                            try {
                                val token = OtpTokenFactory.createFromUri(Uri.parse(raw))
                                val added = tokenViewModel.addToken(token)
                                if (added) {
                                    ToastUtils.showShortToast(context, "令牌添加成功")
                                    manualInputText = ""
                                    showManualInput.value = false
                                    onTokenScanned()
                                } else {
                                    pickedImageError = "该令牌已存在"
                                }
                            } catch (e: Exception) {
                                pickedImageError = when (e) {
                                    is IllegalArgumentException -> e.message ?: "无效的令牌参数"
                                    is NoSuchAlgorithmException -> "不支持的加密算法"
                                    else -> "无效的二维码格式"
                                }
                            }
                        }
                    },
                    enabled = manualInputText.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "添加")
                }
            }
        }
    }

    // 当识别到二维码错误或令牌规则错误时，使用 ConfirmationDialog 提示更换图片后重试
    val showErrorDialog = remember { mutableStateOf(pickedImageError != null) }
    LaunchedEffect(pickedImageError) {
        showErrorDialog.value = pickedImageError != null
    }
    
    if (showErrorDialog.value) {
        ConfirmationDialog(
            title = pickedImageError ?: "解析失败",
            show = showErrorDialog,
            onDismiss = { 
                pickedImageError = null 
                showErrorDialog.value = false
            },
            confirmButtonText = "更换图片重试",
            onConfirm = { 
                pickedImageError = null 
                showErrorDialog.value = false
                imagePickerLauncher.launch(arrayOf("image/*")) 
            }
        )
    }
}

/**
 * 处理相机捕获的图像
 */
@OptIn(DelicateCoroutinesApi::class)
private fun processImageProxy(
    context: Context,
    imageProxy: ImageProxy,
    tokenQRCodeDecoder: TokenQRCodeDecoder,
    tokenViewModel: TokenViewModel,
    onTokenFound: (String) -> Unit,
    lastScannedUrl: MutableState<String?>
) {
    try {
        // 在use块内部处理所有逻辑，确保imageProxy未被关闭
        imageProxy.use { image ->
            val parseResult = tokenQRCodeDecoder.parseQRCode(image)

            when {
                parseResult.success -> {
                // 解析成功，获取二维码内容
                val tokenString = parseResult.content ?: return@use

                // 检查同一次识别周期内是否重复识别到相同的URL
                if (tokenString == lastScannedUrl.value) {
                    // 重复识别到相同URL，直接截停，不记录日志
                    return@use
                }

                // 更新最近扫描的URL
                lastScannedUrl.value = tokenString

                Logger.d("QRCodeScanner", "Found QR code: $tokenString")

                try {
                    // 解析URI对象
                    val uri = Uri.parse(tokenString)

                    // 从URI创建令牌 - 这里会执行令牌规则验证
                    val token = OtpTokenFactory.createFromUri(uri)
                    Logger.d("QRCodeScanner", "令牌规则验证通过，准备检查是否已存在")

                    // 检查令牌是否已存在，使用同步方式避免重复处理
                    val isExists = runBlocking {
                        tokenViewModel.isTokenDuplicate(
                            token.secret,
                            token.algorithm,
                            token.digits,
                            token.period
                        )
                    }

                    if (isExists) {
                        // 令牌已存在，直接截停，显示提示
                        Logger.d("QRCodeScanner", "令牌已存在，跳过添加操作")
                        // 使用主线程显示Toast
                        Handler(context.mainLooper).post {
                            ToastUtils.showShortToast(
                                context,
                                "该令牌已存在"
                            )
                        }
                        return@use // 直接返回，不进入后续步骤
                    }

                    // 使用Dispatchers.Main协程作用域，确保UI操作在主线程执行
                    GlobalScope.launch(Dispatchers.Main) {
                        try {
                            // 保存令牌
                            val isAdded = tokenViewModel.addToken(token)

                            if (isAdded) {
                                // 调用回调，通知父组件关闭扫描窗口
                                onTokenFound(tokenString)
                                // 不在这里清除URL，由父组件关闭扫描窗口时处理
                            } else {
                                // 理论上不会走到这里，因为已经提前检查过了
                                Logger.d("QRCodeScanner", "Token addition failed unexpectedly")
                                ToastUtils.showShortToast(
                                    context,
                                    "添加令牌失败"
                                )
                            }
                        } catch (e: Exception) {
                            Logger.e("QRCodeScanner", "Error adding token: ${e.message}", e)
                            ToastUtils.showShortToast(
                                context,
                                "添加令牌失败"
                            )
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = when (e) {
                        is IllegalArgumentException -> {
                            // 令牌规则验证失败，显示具体错误信息
                            Logger.e("QRCodeScanner", "令牌规则验证失败: ${e.message}", e)
                            e.message ?: "无效的令牌参数"
                        }
                        is NoSuchAlgorithmException -> {
                            // 算法不支持
                            Logger.e("QRCodeScanner", "不支持的算法: ${e.message}", e)
                            "不支持的加密算法"
                        }
                        else -> {
                            // 其他错误
                            Logger.e("QRCodeScanner", "二维码处理失败: ${e.message}", e)
                            "无效的二维码格式"
                        }
                    }

                    // 使用主线程显示Toast
                    Handler(context.mainLooper).post {
                        ToastUtils.showShortToast(
                            context,
                            errorMsg
                        )
                    }
                }
                }
                parseResult.errorType != null && parseResult.errorType != TokenQRCodeDecoder.ParseResult.ErrorType.NOT_FOUND -> {
                    // 解析失败，且不是"未找到二维码"类型，显示错误提示
                    val errorMessage = when (parseResult.errorType) {
                        TokenQRCodeDecoder.ParseResult.ErrorType.CHECKSUM_ERROR -> "二维码校验和错误"
                        TokenQRCodeDecoder.ParseResult.ErrorType.FORMAT_ERROR -> "二维码格式错误"
                        TokenQRCodeDecoder.ParseResult.ErrorType.UNKNOWN_ERROR -> "二维码解析失败"
                        else -> "二维码解析失败"
                    }
                    // 添加更多调试信息，包括图像信息
                    Logger.e("QRCodeScanner", "$errorMessage: 图像尺寸=${image.width}x${image.height}，图像格式=${image.format}")

                    // 在主线程显示Toast
                    Handler(context.mainLooper).post {
                        ToastUtils.showShortToast(
                            context,
                            errorMessage
                        )
                    }
                }
                // 其他情况（如NOT_FOUND），不显示提示，也不记录日志
                else -> {
                    // 未找到二维码，不记录日志
                }
            }
        }
    } catch (e: Exception) {
        Logger.e("QRCodeScanner", "Error processing image: ${e.message}", e)

        // 显示未知错误提示
        Handler(context.mainLooper).post {
            ToastUtils.showShortToast(
                context,
                "二维码处理失败"
            )
        }
    }
}