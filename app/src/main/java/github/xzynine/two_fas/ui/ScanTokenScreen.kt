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
    val tokenViewModel = remember { TokenViewModel(context) }
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
    var foundToken by remember { mutableStateOf(false) }
    
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
                                processImageProxy(
                                    context,
                                    imageProxy,
                                    tokenQRCodeDecoder,
                                    tokenViewModel
                                ) { 
                                    foundToken = true
                                    coroutineScope.launch {
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
    onTokenFound: () -> Unit
) {
    try {
        // 解析二维码
        val tokenString = tokenQRCodeDecoder.parseQRCode(imageProxy)
        
        if (tokenString != null) {
            Log.d("ScanTokenScreen", "Found token: $tokenString")
            
            // 从URI创建令牌
            val token = OtpTokenFactory.createFromUri(Uri.parse(tokenString))
            
            // 保存令牌
            tokenViewModel.addToken(token)
            
            // 显示成功提示
            Toast.makeText(
                context,
                "令牌添加成功",
                Toast.LENGTH_SHORT
            ).show()
            
            // 调用回调
            onTokenFound()
        }
    } catch (e: Exception) {
        Log.e("ScanTokenScreen", "Error processing image: ${e.message}")
    } finally {
        // 关闭图像代理
        imageProxy.close()
    }
}
