package io.tl.glowinghelper

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏UI
        setupFullScreenUI()
        
        setContent {
            GlowingHelperTheme {
                PNGFineTuneApp()
            }
        }
    }

    /**
     * 设置全屏UI，隐藏状态栏
     */
    private fun setupFullScreenUI() {
        // 启用边到边
        enableEdgeToEdge()
        
        // 隐藏状态栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 隐藏状态栏和导航栏
        window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
    }

    override fun onResume() {
        super.onResume()
        // 确保在Activity恢复时保持全屏状态
        setupFullScreenUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // 当窗口获得焦点时，保持状态栏和导航栏隐藏
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PNGFineTuneApp() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var pixelData by remember { mutableStateOf<PixelData?>(null) }
    var selectedPixel by remember { mutableStateOf<PixelInfo?>(null) }
    var isDraggingEnabled by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 图片选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                imageUri = it
                scope.launch {
                    loadImageBitmap(context, it)?.let { bitmap ->
                        imageBitmap = bitmap.asImageBitmap()
                        pixelData = PixelData.fromBitmap(bitmap)
                    }
                }
            }
        }
    )
    
    // 权限请求（如果需要）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            imagePicker.launch("image/*")
        }
    }
    
    // 如果没有选择图片，显示选择界面
    if (imageUri == null) {
        SelectImageScreen(
            onSelectImage = {
                // 检查权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ 不需要权限
                    imagePicker.launch("image/*")
                } else {
                    val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        imagePicker.launch("image/*")
                    } else {
                        permissionLauncher.launch(permission)
                    }
                }
            }
        )
    } else {
        // 显示图片编辑界面
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PNG像素透明度编辑器") },
                    actions = {
                        Switch(
                            checked = isDraggingEnabled,
                            onCheckedChange = { isDraggingEnabled = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDraggingEnabled) "缩放拖拽模式" else "像素编辑模式",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                // 图片显示区域
                if (imageBitmap != null && pixelData != null) {
                    ImageEditor(
                        imageBitmap = imageBitmap!!,
                        pixelData = pixelData!!,
                        isDraggingEnabled = isDraggingEnabled,
                        onPixelClick = { pixelInfo ->
                            selectedPixel = pixelInfo
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 保存按钮
                    Button(
                        onClick = {
                            scope.launch {
                                pixelData?.let { saveImage(context, it) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("保存修改")
                    }
                }
            }
        }
    }
    
    // 像素编辑对话框
    selectedPixel?.let { pixelInfo ->
        PixelEditDialog(
            pixelInfo = pixelInfo,
            onDismiss = { selectedPixel = null },
            onSave = { newAlpha ->
                pixelData?.updatePixelAlpha(pixelInfo.x, pixelInfo.y, newAlpha)
                selectedPixel = null
            }
        )
    }
}

@Composable
fun SelectImageScreen(onSelectImage: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp),
            onClick = onSelectImage
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "点击以选取图片",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "选择PNG图片进行像素透明度编辑",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ImageEditor(
    imageBitmap: ImageBitmap,
    pixelData: PixelData,
    isDraggingEnabled: Boolean,
    onPixelClick: (PixelInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(isDraggingEnabled) {
                if (isDraggingEnabled) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        scale *= zoom
                        offset += pan
                    }
                }
            }
    ) {
        // 绘制图片
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(!isDraggingEnabled) {
                    if (!isDraggingEnabled) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            // 在编辑模式下可以缩放，但不能拖拽
                            scale *= zoom
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val imageWidth = imageBitmap.width.toFloat()
            val imageHeight = imageBitmap.height.toFloat()
            
            // 计算缩放以保持宽高比
            val scaleToFit = minOf(
                canvasWidth / imageWidth,
                canvasHeight / imageHeight
            )
            
            val finalScale = scaleToFit * scale
            val scaledWidth = imageWidth * finalScale
            val scaledHeight = imageHeight * finalScale
            
            // 计算居中位置
            val centerX = (canvasWidth - scaledWidth) / 2 + offset.x
            val centerY = (canvasHeight - scaledHeight) / 2 + offset.y
            
            // 绘制像素网格
            drawPixelGrid(
                pixelData = pixelData,
                scale = finalScale,
                offsetX = centerX,
                offsetY = centerY,
                onPixelClick = if (!isDraggingEnabled) onPixelClick else null,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight
            )
        }
    }
}

@Composable
private fun DrawScope.drawPixelGrid(
    pixelData: PixelData,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onPixelClick: ((PixelInfo) -> Unit)?,
    canvasWidth: Float,
    canvasHeight: Float
) {
    // 绘制每个像素
    for (y in 0 until pixelData.height) {
        for (x in 0 until pixelData.width) {
            val pixel = pixelData.getPixel(x, y)
            if (pixel.alpha > 0) {
                // 计算像素在画布上的位置和大小
                val pixelX = offsetX + x * scale
                val pixelY = offsetY + y * scale
                val pixelSize = scale
                
                // 只在可见区域内绘制
                if (pixelX + pixelSize > 0 && 
                    pixelX < canvasWidth && 
                    pixelY + pixelSize > 0 && 
                    pixelY < canvasHeight) {
                    
                    // 绘制像素颜色
                    drawRect(
                        color = android.graphics.Color.argb(
                            pixel.alpha,
                            pixel.red,
                            pixel.green,
                            pixel.blue
                        ).toComposeColor(),
                        topLeft = Offset(pixelX, pixelY),
                        size = Size(pixelSize, pixelSize)
                    )
                    
                    // 绘制像素边框
                    drawRect(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        topLeft = Offset(pixelX, pixelY),
                        size = Size(pixelSize, pixelSize),
                        style = android.graphics.Paint.Style.STROKE.toComposeStyle(
                            strokeWidth = 0.5f
                        )
                    )
                }
            }
        }
    }
    
    // 如果需要处理点击，绘制一个透明的覆盖层来捕获点击
    onPixelClick?.let {
        // 这个实际点击处理应该在Canvas外部的pointerInput中处理
        // 这里只是绘制一个用于调试的透明覆盖层
        if (false) { // 调试模式下显示
            drawRect(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                topLeft = Offset(offsetX, offsetY),
                size = Size(pixelData.width * scale, pixelData.height * scale)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelEditDialog(
    pixelInfo: PixelInfo,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var alphaInput by remember { mutableStateOf(pixelInfo.alpha.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改像素透明度") },
        text = {
            Column {
                Text("位置: (${pixelInfo.x}, ${pixelInfo.y})")
                Text("颜色: RGB(${pixelInfo.red}, ${pixelInfo.green}, ${pixelInfo.blue})")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = alphaInput,
                    onValueChange = { newValue ->
                        alphaInput = newValue.filter { it.isDigit() }
                        error = null
                    },
                    label = { Text("透明度 (0-255)") },
                    keyboardType = KeyboardType.Number,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 透明度预览
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("预览:", modifier = Modifier.padding(end = 8.dp))
                    Canvas(modifier = Modifier.size(32.dp)) {
                        drawRect(
                            color = android.graphics.Color.argb(
                                alphaInput.toIntOrNull() ?: pixelInfo.alpha,
                                pixelInfo.red,
                                pixelInfo.green,
                                pixelInfo.blue
                            ).toComposeColor(),
                            size = Size(size.width, size.height)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val alpha = alphaInput.toIntOrNull()
                    if (alpha == null || alpha !in 0..255) {
                        error = "请输入0-255之间的整数"
                    } else {
                        onSave(alpha)
                    }
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text("取消")
            }
        }
    )
}

// 数据类
data class PixelInfo(
    val x: Int,
    val y: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int
)

class PixelData(
    val width: Int,
    val height: Int,
    private val pixels: Array<PixelInfo>
) {
    fun getPixel(x: Int, y: Int): PixelInfo {
        return pixels[y * width + x]
    }
    
    fun updatePixelAlpha(x: Int, y: Int, newAlpha: Int) {
        val index = y * width + x
        val oldPixel = pixels[index]
        pixels[index] = oldPixel.copy(alpha = newAlpha)
    }
    
    fun toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = getPixel(x, y)
                bitmap.setPixel(x, y, Color.argb(
                    pixel.alpha,
                    pixel.red,
                    pixel.green,
                    pixel.blue
                ))
            }
        }
        return bitmap
    }
    
    companion object {
        fun fromBitmap(bitmap: Bitmap): PixelData {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = Array(width * height) { index ->
                val x = index % width
                val y = index / width
                val color = bitmap.getPixel(x, y)
                PixelInfo(
                    x = x,
                    y = y,
                    red = Color.red(color),
                    green = Color.green(color),
                    blue = Color.blue(color),
                    alpha = Color.alpha(color)
                )
            }
            return PixelData(width, height, pixels)
        }
    }
}

// 扩展函数
fun android.graphics.Color.toComposeColor(): androidx.compose.ui.graphics.Color {
    return androidx.compose.ui.graphics.Color(
        red = this.red(),
        green = this.green(),
        blue = this.blue(),
        alpha = this.alpha()
    )
}

fun android.graphics.Paint.Style.toComposeStyle(strokeWidth: Float): androidx.compose.ui.graphics.drawscope.Stroke {
    return androidx.compose.ui.graphics.drawscope.Stroke(
        width = strokeWidth,
        cap = androidx.compose.ui.graphics.StrokeCap.Butt,
        join = androidx.compose.ui.graphics.StrokeJoin.Miter
    )
}

// 协程函数
suspend fun loadImageBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

suspend fun saveImage(context: android.content.Context, pixelData: PixelData) {
    withContext(Dispatchers.IO) {
        try {
            val bitmap = pixelData.toBitmap()
            val fileName = "edited_png_${System.currentTimeMillis()}.png"
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                
                // 清除IS_PENDING标志
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                
                withContext(Dispatchers.Main) {
                    // 显示保存成功消息
                    // 这里可以添加Toast或Snackbar
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                // 显示错误消息
            }
        }
    }
}

@Composable
fun GlowingHelperTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}