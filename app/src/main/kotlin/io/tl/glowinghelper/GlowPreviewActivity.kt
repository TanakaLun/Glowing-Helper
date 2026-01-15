package io.tl.glowinghelper

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.tl.glowinghelper.ui.theme.GlowingHelperTheme

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
    var pixelData by remember { mutableStateOf<PixelData?>(null) }
    var originalAspectRatio by remember { mutableStateOf(1f) }
    
    // 参数状态声明
    var ambient by rememberSaveable { mutableStateOf(0.4f) }
    var glowIntensity by rememberSaveable { mutableStateOf(2.2f) }
    var shimmerIntensity by rememberSaveable { mutableStateOf(1.0f) }
    var glowLeakIntensity by rememberSaveable { mutableStateOf(0.4f) }
    
    // 滚动状态
    val scrollState = rememberScrollState()
    
    // 动画逻辑
    val infiniteTransition = rememberInfiniteTransition()
    val shimmerTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing)
        )
    )
    
    // 图片加载逻辑
    LaunchedEffect(imageUri) {
        if (imageUri != null) {
            val bitmap = withContext(Dispatchers.IO) {
                loadImageBitmap(context, imageUri)
            }
            bitmap?.let { loadedBitmap ->
                pixelData = PixelData.fromBitmap(loadedBitmap)
                originalAspectRatio = if (loadedBitmap.height > 0) {
                    loadedBitmap.width.toFloat() / loadedBitmap.height.toFloat()
                } else { 1f }
            }
        }
    }

    androidx.activity.compose.BackHandler(onBack = onBackRequest)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Glowing效果预览", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            )
        }
    ) { innerPadding ->
        if (pixelData != null) {
            // 主容器：不再整体滚动
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // --- 第一部分：固定预览区 ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(originalAspectRatio)
                        .padding(vertical = 16.dp)
                        // .background(
                            // color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            // shape = RoundedCornerShape(8.dp)
                        // )
                ) {
                    GlowPreviewCanvas(
                        pixelData = pixelData!!,
                        ambient = ambient,
                        glowIntensity = glowIntensity,
                        shimmerIntensity = shimmerIntensity,
                        glowLeakIntensity = glowLeakIntensity,
                        shimmerTime = shimmerTime,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // --- 第二部分：可滚动控制区 ---
                // weight(1f) 确保它填满剩余空间，verticalScroll 允许其内部内容滚动
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                            Text("Glow效果参数调节", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            
                            ParameterSlider("环境光", ambient, { ambient = it }, 0f..1.5f, 74)
                            ParameterSlider("发光强度", glowIntensity, { glowIntensity = it }, 0f..5f, 250)
                            ParameterSlider("闪烁强度", shimmerIntensity, { shimmerIntensity = it }, 0f..1f, 50)
                            ParameterSlider("发光溢出", glowLeakIntensity, { glowLeakIntensity = it }, 0f..1f, 50)
                        }
                    }
                    
                    Text(
                        "提示: 像素透明度252/255为100%发光，253/255为40%发光",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Button(
                        onClick = onBackRequest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("返回编辑")
                    }
                }
            }
        } else {
            // 加载中居中显示
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
    Box(
        modifier = modifier.clipToBounds()
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val imageWidth = pixelData.width.toFloat()
            val imageHeight = pixelData.height.toFloat()
            
            val scaleToFit = minOf(
                canvasWidth / imageWidth,
                canvasHeight / imageHeight
            )
            
            val finalScale = scaleToFit
            val centerX = (canvasWidth - imageWidth * finalScale) / 2
            val centerY = (canvasHeight - imageHeight * finalScale) / 2
            
            // 修复点 1: glowCanvas 的 drawRect 调用
            val glowLayer = ImageBitmap(pixelData.width, pixelData.height)
            val glowCanvas = androidx.compose.ui.graphics.Canvas(glowLayer)
            val tempPaint = Paint() // 复用 Paint 对象

            for (y in 0 until pixelData.height) {
                for (x in 0 until pixelData.width) {
                    val pixel = pixelData.getPixel(x, y)
                    if (pixel.alpha > 0) {
                        val isFullGlow = pixel.alpha == 252
                        val isPartialGlow = pixel.alpha == 253
                        if (isFullGlow || isPartialGlow) {
                            val alphaMult = if (isFullGlow) 1f else 0.4f
                            tempPaint.color = ComposeColor(
                                red = pixel.red / 255f * alphaMult,
                                green = pixel.green / 255f * alphaMult,
                                blue = pixel.blue / 255f * alphaMult,
                                alpha = 1f
                            )
                            // 底层 Canvas 使用 left, top, right, bottom
                            glowCanvas.drawRect(
                                left = x.toFloat(),
                                top = y.toFloat(),
                                right = x.toFloat() + 1f,
                                bottom = y.toFloat() + 1f,
                                paint = tempPaint
                            )
                        }
                    }
                }
            }
            
            // 绘制主画面
            for (y in 0 until pixelData.height) {
                for (x in 0 until pixelData.width) {
                    val pixel = pixelData.getPixel(x, y)
                    if (pixel.alpha > 0) {
                        val pixelX = centerX + x * finalScale
                        val pixelY = centerY + y * finalScale
                        val pixelSize = finalScale
                        
                        if (pixelX + pixelSize > 0 && pixelX < canvasWidth && 
                            pixelY + pixelSize > 0 && pixelY < canvasHeight) {
                            
                            val isFullGlow = pixel.alpha == 252
                            val isPartialGlow = pixel.alpha == 253
                            val isGlowing = isFullGlow || isPartialGlow
                            
                            if (!isGlowing) {
                                // 普通像素
                                drawRect(
                                    color = ComposeColor(
                                        red = pixel.red / 255f * ambient,
                                        green = pixel.green / 255f * ambient,
                                        blue = pixel.blue / 255f * ambient,
                                        alpha = pixel.alpha / 255f
                                    ),
                                    topLeft = Offset(pixelX, pixelY),
                                    size = Size(pixelSize, pixelSize)
                                )
                                
                                // 修复点 2: 混合模式的处理。DrawScope 直接支持 blendMode 参数。
                                if (glowLeakIntensity > 0) {
                                    // ... (检查邻居的代码保持不变)
                                    var hasGlowingNeighbor = false
                                    for (dx in -1..1) {
                                        for (dy in -1..1) {
                                            if (dx == 0 && dy == 0) continue
                                            val nx = x + dx
                                            val ny = y + dy
                                            if (nx in 0 until pixelData.width && ny in 0 until pixelData.height) {
                                                val neighbor = pixelData.getPixel(nx, ny)
                                                if (neighbor.alpha == 252 || neighbor.alpha == 253) {
                                                    hasGlowingNeighbor = true; break
                                                }
                                            }
                                        }
                                        if (hasGlowingNeighbor) break
                                    }
                                    
                                    if (hasGlowingNeighbor) {
                                        drawRect(
                                            color = ComposeColor(
                                                red = pixel.red / 255f * ambient * glowLeakIntensity,
                                                green = pixel.green / 255f * ambient * glowLeakIntensity,
                                                blue = pixel.blue / 255f * ambient * glowLeakIntensity,
                                                alpha = pixel.alpha / 255f * glowLeakIntensity
                                            ),
                                            topLeft = Offset(pixelX, pixelY),
                                            size = Size(pixelSize, pixelSize),
                                            blendMode = BlendMode.Plus // 直接在这里设置混合模式
                                        )
                                    }
                                }
                            } else {
                                // 发光像素
                                val glowStrength = if (isFullGlow) 1f else 0.4f
                                val shimmer = calculateShimmer(x, y, shimmerTime, shimmerIntensity)
                                
                                // 绘制底色
                                drawRect(
                                    color = ComposeColor(
                                        red = pixel.red / 255f * ambient,
                                        green = pixel.green / 255f * ambient,
                                        blue = pixel.blue / 255f * ambient,
                                        alpha = pixel.alpha / 255f
                                    ),
                                    topLeft = Offset(pixelX, pixelY),
                                    size = Size(pixelSize, pixelSize)
                                )
                                
                                // 修复点 3: 发光效果绘制
                                drawRect(
                                    color = ComposeColor(
                                        red = pixel.red / 255f * glowIntensity * glowStrength * (1f + shimmer * 0.3f),
                                        green = pixel.green / 255f * glowIntensity * glowStrength * (1f + shimmer * 0.3f),
                                        blue = pixel.blue / 255f * glowIntensity * glowStrength * (1f + shimmer * 0.3f),
                                        alpha = 0.8f
                                    ),
                                    topLeft = Offset(pixelX, pixelY),
                                    size = Size(pixelSize, pixelSize),
                                    blendMode = BlendMode.Screen
                                )
                                
                                // 修复点 4: 光晕扩散
                                val glowRadius = pixelSize * 0.5f * glowIntensity * 0.3f
                                if (glowRadius > 0) {
                                    val spreadColorBase = ComposeColor(
                                        red = pixel.red / 255f * glowIntensity * 0.2f,
                                        green = pixel.green / 255f * glowIntensity * 0.2f,
                                        blue = pixel.blue / 255f * glowIntensity * 0.2f
                                    )
                                    
                                    for (i in 1..3) {
                                        val radius = glowRadius * i / 3f
                                        val alpha = 0.3f * (1f - i / 3f)
                                        drawCircle(
                                            color = spreadColorBase.copy(alpha = alpha),
                                            radius = radius,
                                            center = Offset(pixelX + pixelSize / 2, pixelY + pixelSize / 2),
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

private fun calculateShimmer(x: Int, y: Int, time: Float, intensity: Float): Float {
    // 基于位置和时间的简单闪烁计算
    val position = x.toFloat() + y.toFloat()
    val shimmer = sin(1.57f * position + 0.7854f * sin(position + 0.1f * time) + 0.8f * time)
    return shimmer * shimmer * intensity
}