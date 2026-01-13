package io.tl.glowinghelper

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.tl.glowinghelper.ui.theme.GlowingHelperTheme
import androidx.compose.ui.graphics.Color as ComposeColor
import kotlin.math.*

class GlowPreviewActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_PIXEL_DATA = "extra_pixel_data"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏UI
        setupFullScreenUI()
        
        val imageUri = intent.getParcelableExtra<Uri>(EXTRA_IMAGE_URI)
        
        setContent {
            GlowingHelperTheme {
                GlowPreviewScreen(
                    imageUri = imageUri,
                    onBackRequest = { finish() }
                )
            }
        }
    }

    private fun setupFullScreenUI() {
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or 
                android.view.WindowInsets.Type.navigationBars()
            )
        }
    }

    override fun onResume() {
        super.onResume()
        setupFullScreenUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or 
                android.view.WindowInsets.Type.navigationBars()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlowPreviewScreen(
    imageUri: Uri?,
    onBackRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var pixelData by remember { mutableStateOf<PixelData?>(null) }
    var originalAspectRatio by remember { mutableStateOf(1f) }
    
    // Glow效果参数
    var ambient by rememberSaveable { mutableStateOf(0.4f) }
    var glowIntensity by rememberSaveable { mutableStateOf(2.2f) }
    var shimmerIntensity by rememberSaveable { mutableStateOf(1.0f) }
    var glowLeakIntensity by rememberSaveable { mutableStateOf(0.4f) }
    
    // 动画时间
    val infiniteTransition = rememberInfiniteTransition()
    val shimmerTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing)
        )
    )
    
    // 加载图片
    LaunchedEffect(imageUri) {
        if (imageUri != null) {
            val bitmap = withContext(Dispatchers.IO) {
                loadImageBitmap(context, imageUri)
            }
            bitmap?.let { loadedBitmap ->
                pixelData = PixelData.fromBitmap(loadedBitmap)
                originalAspectRatio = if (loadedBitmap.height > 0) {
                    loadedBitmap.width.toFloat() / loadedBitmap.height.toFloat()
                } else {
                    1f
                }
            }
        }
    }
    
    // 处理返回操作
    androidx.activity.compose.BackHandler(onBack = onBackRequest)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Glowing效果预览",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (pixelData != null) {
                // 预览画布区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(originalAspectRatio)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(1.dp)
                ) {
                    GlowPreviewCanvas(
                        pixelData = pixelData!!,
                        ambient = ambient,
                        glowIntensity = glowIntensity,
                        shimmerIntensity = shimmerIntensity,
                        glowLeakIntensity = glowLeakIntensity,
                        shimmerTime = shimmerTime,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(7.dp)
                            )
                    )
                }
                
                // 参数调节区域
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Glow效果参数调节",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // 环境光参数
                        ParameterSlider(
                            label = "环境光",
                            value = ambient,
                            onValueChange = { ambient = it },
                            valueRange = 0f..1.5f,
                            steps = 74 // (1.5 - 0) / 0.02 = 75 steps
                        )
                        
                        // 发光强度参数
                        ParameterSlider(
                            label = "发光强度",
                            value = glowIntensity,
                            onValueChange = { glowIntensity = it },
                            valueRange = 0f..5f,
                            steps = 250 // (5 - 0) / 0.02 = 250 steps
                        )
                        
                        // 闪烁强度参数
                        ParameterSlider(
                            label = "闪烁强度",
                            value = shimmerIntensity,
                            onValueChange = { shimmerIntensity = it },
                            valueRange = 0f..1f,
                            steps = 50 // (1 - 0) / 0.02 = 50 steps
                        )
                        
                        // 发光溢出参数
                        ParameterSlider(
                            label = "发光溢出",
                            value = glowLeakIntensity,
                            onValueChange = { glowLeakIntensity = it },
                            valueRange = 0f..1f,
                            steps = 50
                        )
                    }
                }
                
                // 底部说明
                Text(
                    "提示: 像素透明度252/255为100%发光，253/255为40%发光",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                
                // 返回按钮
                Button(
                    onClick = onBackRequest,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("返回编辑")
                }
            } else {
                // 加载中
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("加载图片中...")
                    }
                }
            }
        }
    }
}

@Composable
fun GlowPreviewCanvas(
    pixelData: PixelData,
    ambient: Float,
    glowIntensity: Float,
    shimmerIntensity: Float,
    glowLeakIntensity: Float,
    shimmerTime: Float,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // 双击重置缩放和位置
                        scale = 1f
                        offset = Offset.Zero
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        scale *= zoom
                        offset += pan
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val imageWidth = pixelData.width.toFloat()
            val imageHeight = pixelData.height.toFloat()
            
            val scaleToFit = minOf(
                canvasWidth / imageWidth,
                canvasHeight / imageHeight
            )
            
            val finalScale = scaleToFit * scale
            val centerX = (canvasWidth - imageWidth * finalScale) / 2 + offset.x
            val centerY = (canvasHeight - imageHeight * finalScale) / 2 + offset.y
            
            // 创建一个离屏画布用于发光效果
            val glowLayer = ImageBitmap(pixelData.width, pixelData.height)
            val glowCanvas = Canvas(glowLayer)
            
            // 首先绘制基础像素和发光检测
            for (y in 0 until pixelData.height) {
                for (x in 0 until pixelData.width) {
                    val pixel = pixelData.getPixel(x, y)
                    
                    if (pixel.alpha > 0) {
                        val pixelX = x.toFloat()
                        val pixelY = y.toFloat()
                        
                        // 检查是否为发光像素
                        val isFullGlow = pixel.alpha == 252  // 100% 发光
                        val isPartialGlow = pixel.alpha == 253  // 40% 发光
                        val isGlowing = isFullGlow || isPartialGlow
                        
                        if (isGlowing) {
                            // 绘制发光像素到离屏画布
                            val glowColor = if (isFullGlow) {
                                ComposeColor(
                                    red = pixel.red / 255f,
                                    green = pixel.green / 255f,
                                    blue = pixel.blue / 255f,
                                    alpha = 1f
                                )
                            } else {
                                ComposeColor(
                                    red = pixel.red / 255f * 0.4f,
                                    green = pixel.green / 255f * 0.4f,
                                    blue = pixel.blue / 255f * 0.4f,
                                    alpha = 1f
                                )
                            }
                            
                            glowCanvas.drawRect(
                                color = glowColor,
                                topLeft = Offset(pixelX, pixelY),
                                size = Size(1f, 1f)
                            )
                        }
                    }
                }
            }
            
            // 绘制到主画布
            for (y in 0 until pixelData.height) {
                for (x in 0 until pixelData.width) {
                    val pixel = pixelData.getPixel(x, y)
                    
                    if (pixel.alpha > 0) {
                        val pixelX = centerX + x * finalScale
                        val pixelY = centerY + y * finalScale
                        val pixelSize = finalScale
                        
                        if (pixelX + pixelSize > 0 && 
                            pixelX < canvasWidth && 
                            pixelY + pixelSize > 0 && 
                            pixelY < canvasHeight) {
                            
                            val isFullGlow = pixel.alpha == 252
                            val isPartialGlow = pixel.alpha == 253
                            val isGlowing = isFullGlow || isPartialGlow
                            
                            if (!isGlowing) {
                                // 普通像素
                                val baseColor = ComposeColor(
                                    red = pixel.red / 255f * ambient,
                                    green = pixel.green / 255f * ambient,
                                    blue = pixel.blue / 255f * ambient,
                                    alpha = pixel.alpha / 255f
                                )
                                
                                drawRect(
                                    color = baseColor,
                                    topLeft = Offset(pixelX, pixelY),
                                    size = Size(pixelSize, pixelSize)
                                )
                                
                                // 添加发光溢出效果
                                if (glowLeakIntensity > 0) {
                                    // 检查周围像素是否有发光
                                    var hasGlowingNeighbor = false
                                    val neighborOffsets = listOf(
                                        -1 to -1, 0 to -1, 1 to -1,
                                        -1 to 0,          1 to 0,
                                        -1 to 1,  0 to 1,  1 to 1
                                    )
                                    
                                    for ((dx, dy) in neighborOffsets) {
                                        val nx = x + dx
                                        val ny = y + dy
                                        if (nx in 0 until pixelData.width && 
                                            ny in 0 until pixelData.height) {
                                            val neighbor = pixelData.getPixel(nx, ny)
                                            if (neighbor.alpha == 252 || neighbor.alpha == 253) {
                                                hasGlowingNeighbor = true
                                                break
                                            }
                                        }
                                    }
                                    
                                    if (hasGlowingNeighbor) {
                                        val leakColor = ComposeColor(
                                            red = pixel.red / 255f * ambient * glowLeakIntensity,
                                            green = pixel.green / 255f * ambient * glowLeakIntensity,
                                            blue = pixel.blue / 255f * ambient * glowLeakIntensity,
                                            alpha = pixel.alpha / 255f * glowLeakIntensity
                                        )
                                        
                                        drawRect(
                                            color = leakColor,
                                            topLeft = Offset(pixelX, pixelY),
                                            size = Size(pixelSize, pixelSize),
                                            blendMode = BlendMode.Plus
                                        )
                                    }
                                }
                            } else {
                                // 发光像素
                                val glowStrength = if (isFullGlow) 1f else 0.4f
                                
                                // 计算闪烁效果
                                val shimmer = calculateShimmer(x, y, shimmerTime, shimmerIntensity)
                                
                                // 基础发光颜色
                                val baseGlowColor = ComposeColor(
                                    red = pixel.red / 255f * ambient,
                                    green = pixel.green / 255f * ambient,
                                    blue = pixel.blue / 255f * ambient,
                                    alpha = pixel.alpha / 255f
                                )
                                
                                // 发光效果颜色
                                val glowColor = ComposeColor(
                                    red = pixel.red / 255f * glowIntensity * glowStrength * (1f + shimmer * 0.3f),
                                    green = pixel.green / 255f * glowIntensity * glowStrength * (1f + shimmer * 0.3f),
                                    blue = pixel.blue / 255f * glowIntensity * glowStrength * (1f + shimmer * 0.3f),
                                    alpha = 0.8f
                                )
                                
                                // 绘制基础颜色
                                drawRect(
                                    color = baseGlowColor,
                                    topLeft = Offset(pixelX, pixelY),
                                    size = Size(pixelSize, pixelSize)
                                )
                                
                                // 绘制发光效果
                                drawRect(
                                    color = glowColor,
                                    topLeft = Offset(pixelX, pixelY),
                                    size = Size(pixelSize, pixelSize),
                                    blendMode = BlendMode.Screen
                                )
                                
                                // 添加光晕扩散效果
                                val glowRadius = pixelSize * 0.5f * glowIntensity * 0.3f
                                if (glowRadius > 0) {
                                    val spreadColor = ComposeColor(
                                        red = pixel.red / 255f * glowIntensity * 0.2f,
                                        green = pixel.green / 255f * glowIntensity * 0.2f,
                                        blue = pixel.blue / 255f * glowIntensity * 0.2f,
                                        alpha = 0.3f
                                    )
                                    
                                    for (i in 1..3) {
                                        val radius = glowRadius * i / 3f
                                        val alpha = 0.3f * (1f - i / 3f)
                                        
                                        drawCircle(
                                            color = spreadColor.copy(alpha = alpha),
                                            center = Offset(pixelX + pixelSize / 2, pixelY + pixelSize / 2),
                                            radius = radius,
                                            blendMode = BlendMode.Screen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateShimmer(x: Int, y: Int, time: Float, intensity: Float): Float {
    // 基于位置和时间的简单闪烁计算
    val position = x.toFloat() + y.toFloat()
    val shimmer = sin(1.57f * position + 0.7854f * sin(position + 0.1f * time) + 0.8f * time)
    return shimmer * shimmer * intensity
}

@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "%.2f".format(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}