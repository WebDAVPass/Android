package github.xzynine.two_fas.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import github.xzynine.two_fas.data.OtpTokenFactory
import github.xzynine.two_fas.util.TokenQRCodeDecoder
import github.xzynine.two_fas.viewmodel.TokenViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * 扫描二维码界面
 */
@Composable
fun ScanTokenScreen(
    onTokenScanned: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    // 使用Application Context创建ViewModel，确保所有实例共享同一个ViewModel
    val appContext = context.applicationContext
    val tokenViewModel = remember { TokenViewModel(appContext) }
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
                Toast.makeText(
                    context,
                    "需要相机权限才能扫描二维码",
                    Toast.LENGTH_SHORT
                ).show()
                onDismiss()
            }
        }
    )
    
    // 相机提供程序
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    
    // 为每个AndroidView实例创建独立的foundToken状态
    val foundToken = remember { mutableStateOf(false) }
    
    // 请求相机权限
    LaunchedEffect(key1 = Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    // 相机预览
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
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                    
                    // 创建图像分析用例
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
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
                                    tokenViewModel
                                ) { tokenString ->
                                    // 标记已找到令牌
                                    foundToken.value = true
                                    
                                    // 在主线程上更新UI
                                    coroutineScope.launch {
                                        // 显示成功提示
                                        Toast.makeText(
                                            context,
                                            "令牌添加成功",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        
                                        // 通知父组件
                                        onTokenScanned()
                                    }
                                }
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
                        Log.e("ScanTokenScreen", "无法绑定相机用例", exc)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
            
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
    
    // 扫描成功后，直接通过回调通知父组件，不显示额外的对话框
    // 因为我们已经在processImageProxy中显示了Toast提示
}

/**
 * 处理相机捕获的图像
 */
private fun processImageProxy(
    context: Context,
    imageProxy: ImageProxy,
    tokenQRCodeDecoder: TokenQRCodeDecoder,
    tokenViewModel: TokenViewModel,
    onTokenFound: (String) -> Unit
) {
    try {
        // 使用use块确保资源正确释放
        val tokenString = imageProxy.use { image ->
            tokenQRCodeDecoder.parseQRCode(image)
        }
        
        if (tokenString != null) {
            Log.d("ScanTokenScreen", "Found token: $tokenString")
            
            try {
                // 从URI创建令牌
                val token = OtpTokenFactory.createFromUri(Uri.parse(tokenString))
                
                // 使用Dispatchers.Main协程作用域，确保UI操作在主线程执行
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        // 保存令牌
                        val isAdded = tokenViewModel.addToken(token)
                        
                        if (isAdded) {
                            // 调用回调
                            onTokenFound(tokenString)
                        } else {
                            // 密钥已存在，显示提示
                            Log.d("ScanTokenScreen", "Token with secret already exists")
                            Toast.makeText(
                                context,
                                "该令牌已存在",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("ScanTokenScreen", "Error adding token: ${e.message}")
                        Toast.makeText(
                            context,
                            "添加令牌失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanTokenScreen", "Error creating token from URI: ${e.message}")
                Toast.makeText(
                    context,
                    "无效的二维码格式",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    } catch (e: Exception) {
        Log.e("ScanTokenScreen", "Error processing image: ${e.message}")
    }
}
