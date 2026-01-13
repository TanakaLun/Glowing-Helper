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
                        // .padding(vertical = 16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        )
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
    // 使用 remember 缓存处理后的位图，避免每一帧都重新创建位图导致卡顿
    // 只有当 pixelData 或 ambient 变化时才重新生成基础图层
    val baseImageBitmap = remember(pixelData, ambient) {
        val bmp = Bitmap.createBitmap(pixelData.width, pixelData.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until pixelData.height) {
            for (x in 0 until pixelData.width) {
                val pixel = pixelData.getPixel(x, y)
                if (pixel.alpha > 0) {
                    val isGlowing = pixel.alpha == 252 || pixel.alpha == 253
                    // 非发光像素应用环境光，发光像素保持原始亮度（稍后由 Glow 层处理）
                    val factor = if (isGlowing) ambient else ambient
                    val color = Color.argb(
                        pixel.alpha,
                        (pixel.red * ambient).toInt().coerceIn(0, 255),
                        (pixel.green * ambient).toInt().coerceIn(0, 255),
                        (pixel.blue * ambient).toInt().coerceIn(0, 255)
                    )
                    bmp.setPixel(x, y, color)
                }
            }
        }
        bmp.asImageBitmap()
    }

    Canvas(
        modifier = modifier.fillMaxSize() // 填充由外部 aspectRatio 约束的 Box
    ) {
        // 1. 绘制基础图层（自动缩放以填充画布，消除空隙和方格感）
        drawImage(
            image = baseImageBitmap,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low // 保持像素艺术的锐利感，若需要极度平滑可改用 Medium
        )

        // 2. 绘制发光层（仅针对发光像素进行叠加）
        // 只有在发光强度大于 0 时才遍历，且只处理发光逻辑
        val pixelScaleX = size.width / pixelData.width
        val pixelScaleY = size.height / pixelData.height

        for (y in 0 until pixelData.height) {
            for (x in 0 until pixelData.width) {
                val pixel = pixelData.getPixel(x, y)
                val isFullGlow = pixel.alpha == 252
                val isPartialGlow = pixel.alpha == 253

                if (isFullGlow || isPartialGlow) {
                    val pixelX = x * pixelScaleX
                    val pixelY = y * pixelScaleY
                    val glowStrength = if (isFullGlow) 1f else 0.4f
                    val shimmer = calculateShimmer(x, y, shimmerTime, shimmerIntensity)

                    // 绘制发光核心
                    drawRect(
                        color = ComposeColor(
                            red = (pixel.red / 255f) * glowIntensity * glowStrength * (1f + shimmer * 0.3f),
                            green = (pixel.green / 255f) * glowIntensity * glowStrength * (1f + shimmer * 0.3f),
                            blue = (pixel.blue / 255f) * glowIntensity * glowStrength * (1f + shimmer * 0.3f),
                            alpha = 0.7f // 发光层的透明度
                        ),
                        topLeft = Offset(pixelX, pixelY),
                        size = Size(pixelScaleX, pixelScaleY),
                        blendMode = BlendMode.Screen
                    )

                    // 绘制外溢光晕 (可选，仅在强度较高时显示)
                    if (glowIntensity > 2.0f) {
                        val radius = pixelScaleX * glowIntensity * 0.4f
                        drawCircle(
                            color = ComposeColor(
                                red = pixel.red / 255f,
                                green = pixel.green / 255f,
                                blue = pixel.blue / 255f,
                                alpha = 0.15f * glowStrength
                            ),
                            radius = radius,
                            center = Offset(pixelX + pixelScaleX / 2, pixelY + pixelScaleY / 2),
                            blendMode = BlendMode.Plus
                        )
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