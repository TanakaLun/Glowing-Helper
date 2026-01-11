package io.tl.glowinghelper

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions // 修正此处导入
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFullScreenUI()
        setContent {
            MaterialTheme {
                PNGFineTuneApp()
            }
        }
    }

    private fun setupFullScreenUI() {
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PNGFineTuneApp() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var pixelData by remember { mutableStateOf<PixelData?>(null) }
    var selectedPixel by remember { mutableStateOf<PixelInfo?>(null) }
    var isDraggingEnabled by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 使用 PickVisualMedia 替代 GetContent，无需申请权限即可访问图片
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                imageUri = it
                scope.launch {
                    val bitmap = loadImageBitmap(context, it)
                    bitmap?.let { loadedBitmap ->
                        imageBitmap = loadedBitmap.asImageBitmap()
                        pixelData = PixelData.fromBitmap(loadedBitmap)
                    }
                }
            }
        }
    )
    
    if (imageUri == null) {
        SelectImageScreen {
            imagePicker.launch(ActivityResultContracts.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PNG像素透明度编辑器", fontSize = 18.sp) },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isDraggingEnabled) "缩放" else "编辑", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = isDraggingEnabled,
                                onCheckedChange = { isDraggingEnabled = it },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
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
                if (pixelData != null) {
                    Box(modifier = Modifier.weight(1f)) {
                        ImageEditor(
                            pixelData = pixelData!!,
                            isDraggingEnabled = isDraggingEnabled,
                            onPixelClick = { selectedPixel = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                pixelData?.let { saveImage(context, it) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Text("导出无损 PNG")
                    }
                }
            }
        }
    }
    
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
fun ImageEditor(
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
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale *= zoom
                        offset += pan
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(!isDraggingEnabled, pixelData, scale, offset) {
                    if (!isDraggingEnabled) {
                        detectTapGestures { tapOffset ->
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val finalScale = (minOf(canvasWidth / pixelData.width, canvasHeight / pixelData.height)) * scale
                            
                            val centerX = (canvasWidth - pixelData.width * finalScale) / 2 + offset.x
                            val centerY = (canvasHeight - pixelData.height * finalScale) / 2 + offset.y
                            
                            val pixelX = ((tapOffset.x - centerX) / finalScale).toInt()
                            val pixelY = ((tapOffset.y - centerY) / finalScale).toInt()
                            
                            if (pixelX in 0 until pixelData.width && pixelY in 0 until pixelData.height) {
                                onPixelClick(pixelData.getPixel(pixelX, pixelY))
                            }
                        }
                    }
                }
        ) {
            val finalScale = (minOf(size.width / pixelData.width, size.height / pixelData.height)) * scale
            val centerX = (size.width - pixelData.width * finalScale) / 2 + offset.x
            val centerY = (size.height - pixelData.height * finalScale) / 2 + offset.y
            
            // 性能优化：只绘制可见区域内的像素（虽然对于小图嵌套循环OK，但大图建议优化）
            for (y in 0 until pixelData.height) {
                for (x in 0 until pixelData.width) {
                    val pixel = pixelData.getPixel(x, y)
                    val px = centerX + x * finalScale
                    val py = centerY + y * finalScale
                    
                    if (px + finalScale > 0 && px < size.width && py + finalScale > 0 && py < size.height) {
                        drawRect(
                            color = androidx.compose.ui.graphics.Color(
                                red = pixel.red / 255f,
                                green = pixel.green / 255f,
                                blue = pixel.blue / 255f,
                                alpha = pixel.alpha / 255f
                            ),
                            topLeft = Offset(px, py),
                            size = Size(finalScale, finalScale)
                        )
                        // 只有缩放到足够大时才绘制网格线
                        if (finalScale > 20f) {
                            drawRect(
                                color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.3f),
                                topLeft = Offset(px, py),
                                size = Size(finalScale, finalScale),
                                style = Stroke(width = 1f)
                            )
                        }
                    }
                }
            }
        }
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
fun PixelEditDialog(
    pixelInfo: PixelInfo,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var alphaInput by remember { mutableStateOf(pixelInfo.alpha.toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑像素") },
        text = {
            Column {
                Text("坐标: (${pixelInfo.x}, ${pixelInfo.y})", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = alphaInput,
                    onValueChange = { alphaInput = it.filter { c -> c.isDigit() } },
                    label = { Text("透明度 (0-255)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), // 修正此处引用
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = alphaInput.toIntOrNull() ?: 255
                onSave(a.coerceIn(0, 255))
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// 数据类部分保持基本不变，但建议将 PixelData 的 toBitmap 放在 IO 线程执行
data class PixelInfo(val x: Int, val y: Int, val red: Int, val green: Int, val blue: Int, var alpha: Int)

class PixelData(val width: Int, val height: Int, private val pixels: Array<PixelInfo>) {
    fun getPixel(x: Int, y: Int) = pixels[y * width + x]
    fun updatePixelAlpha(x: Int, y: Int, newAlpha: Int) {
        pixels[y * width + x].alpha = newAlpha
    }
    fun toBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val colors = IntArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            colors[i] = Color.argb(p.alpha, p.red, p.green, p.blue)
        }
        bmp.setPixels(colors, 0, width, 0, 0, width, height)
        return bmp
    }
    companion object {
        fun fromBitmap(bitmap: Bitmap): PixelData {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = Array(w * h) { i ->
                val x = i % w
                val y = i / w
                val c = bitmap.getPixel(x, y)
                PixelInfo(x, y, Color.red(c), Color.green(c), Color.blue(c), Color.alpha(c))
            }
            return PixelData(w, h, pixels)
        }
    }
}

suspend fun loadImageBitmap(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.use { 
            // 关键：inScaled = false 确保不按屏幕密度缩放图片像素
            val options = BitmapFactory.Options().apply { inScaled = false }
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (e: Exception) { null }
}

suspend fun saveImage(context: Context, pixelData: PixelData) = withContext(Dispatchers.IO) {
    try {
        val bitmap = pixelData.toBitmap()
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Edited_${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/GlowingHelper")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
            withContext(Dispatchers.Main) { Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show() }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectImageScreen(onSelectImage: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onSelectImage) { Text("选取 PNG 图片") }
    }
}
