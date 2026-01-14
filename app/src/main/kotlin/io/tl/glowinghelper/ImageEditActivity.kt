package io.tl.glowinghelper

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions 
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import io.tl.glowinghelper.ui.theme.GlowingHelperTheme

class ImageEditActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏UI
        setupFullScreenUI()
        
        val imageUri = intent.getParcelableExtra<Uri>(EXTRA_IMAGE_URI)
        
        if (imageUri == null) {
            // 如果没有传递图片URI，直接关闭并返回
            finish()
            return
        }
        
        setContent {
            GlowingHelperTheme {
                ImageEditScreen(imageUri = imageUri, onBackRequest = { finish() })
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
fun ImageEditScreen(
    imageUri: Uri,
    onBackRequest: () -> Unit
) {
    var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var pixelData by remember { mutableStateOf<PixelData?>(null) }
    var selectedPixel by remember { mutableStateOf<PixelInfo?>(null) }
    var isDraggingEnabled by remember { mutableStateOf(false) }
    var originalAspectRatio by remember { mutableStateOf(1f) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var isSavingAndExiting by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 加载图片
    LaunchedEffect(imageUri) {
        val bitmap = withContext(Dispatchers.IO) {
            loadImageBitmap(context, imageUri)
        }
        bitmap?.let { loadedBitmap ->
            imageBitmap = loadedBitmap.asImageBitmap()
            pixelData = PixelData.fromBitmap(loadedBitmap)
            // 计算原始图片的宽高比
            originalAspectRatio = if (loadedBitmap.height > 0) {
                loadedBitmap.width.toFloat() / loadedBitmap.height.toFloat()
            } else {
                1f
            }
        }
    }
    
    // 处理系统返回操作
    androidx.activity.compose.BackHandler(
        enabled = selectedPixel == null, // 仅在图像编辑界面且没有打开像素编辑对话框时生效
        onBack = {
            if (hasUnsavedChanges) {
                // 如果有未保存的修改，显示警告对话框
                showUnsavedDialog = true
            } else {
                // 没有未保存的修改，直接返回
                onBackRequest()
            }
        }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Glowing Helper",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = if (isDraggingEnabled) "缩放/拖拽模式" else "像素编辑模式",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasUnsavedChanges) {
                            Text(
                                text = "有未保存的修改",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { isDraggingEnabled = !isDraggingEnabled },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDraggingEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Text(
                            text = if (isDraggingEnabled) "切换至编辑" else "切换至缩放",
                            fontSize = 14.sp
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (imageBitmap != null && pixelData != null) {
                // 图片编辑器区域 - 使用原始宽高比
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
                    ImageEditor(
                        imageBitmap = imageBitmap!!,
                        pixelData = pixelData!!,
                        isDraggingEnabled = isDraggingEnabled,
                        onPixelClick = { pixelInfo ->
                            if (!isDraggingEnabled) {
                                selectedPixel = pixelInfo
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(7.dp)
                            )
                    )
                }
                
                // 保存按钮
                Button(
                    onClick = {
                        scope.launch {
                            pixelData?.let {
                                val saved = saveImage(context, it)
                                if (saved) {
                                    hasUnsavedChanges = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("保存修改")
                }
                
                // 预览按钮
                Button(
                    onClick = {
                        scope.launch {
                            pixelData?.let { data ->
                                // 保存临时图片
                                val tempFile = withContext(Dispatchers.IO) {
                                    saveToTempFile(context, data)
                                }
                                // 启动预览Activity
                                val intent = Intent(context, GlowPreviewActivity::class.java).apply {
                                    putExtra(GlowPreviewActivity.EXTRA_IMAGE_URI, Uri.fromFile(tempFile))
                                }
                                context.startActivity(intent)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("预览Glowing效果")
                }
            } else {
                // 加载中
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载图片中...")
                }
            }
        }
    }
    
    selectedPixel?.let { pixelInfo ->
        PixelEditDialog(
            pixelInfo = pixelInfo,
            onDismiss = { selectedPixel = null },
            onSave = { newAlpha, applyToAllSameColor ->
                if (applyToAllSameColor) {
                    // 应用到此颜色的所有像素
                    pixelData?.updateAllPixelsWithColor(
                        targetRed = pixelInfo.red,
                        targetGreen = pixelInfo.green,
                        targetBlue = pixelInfo.blue,
                        newAlpha = newAlpha
                    )
                } else {
                    // 只修改当前像素
                    pixelData?.updatePixelAlpha(pixelInfo.x, pixelInfo.y, newAlpha)
                }
                hasUnsavedChanges = true
                selectedPixel = null
            }
        )
    }
    
    // 未保存修改警告对话框
    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            isSavingAndExiting = isSavingAndExiting,
            onCancel = { showUnsavedDialog = false },
            onExitWithoutSaving = {
                showUnsavedDialog = false
                // 直接退出，不保存
                onBackRequest()
            },
            onSaveAndExit = {
                scope.launch {
                    isSavingAndExiting = true
                    pixelData?.let {
                        val saved = saveImage(context, it)
                        if (saved) {
                            // 保存成功，退出
                            showUnsavedDialog = false
                            isSavingAndExiting = false
                            onBackRequest()
                        } else {
                            // 保存失败，保持对话框打开
                            isSavingAndExiting = false
                        }
                    } ?: run {
                        // 没有像素数据，直接退出
                        isSavingAndExiting = false
                        showUnsavedDialog = false
                        onBackRequest()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelEditDialog(
    pixelInfo: PixelInfo,
    onDismiss: () -> Unit,
    onSave: (Int, Boolean) -> Unit  // 修改：添加布尔参数表示是否应用到所有相同颜色
) {
    var alphaInput by remember { mutableStateOf(pixelInfo.alpha.toString()) }
    var applyToAllSameColor by remember { mutableStateOf(false) }
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 批量修改选项
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = applyToAllSameColor,
                        onCheckedChange = { applyToAllSameColor = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("应用到所有相同颜色的像素", fontWeight = FontWeight.Medium)
                        Text(
                            "将此透明度设置应用到所有RGB(${pixelInfo.red}, ${pixelInfo.green}, ${pixelInfo.blue})的像素",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("预览:", modifier = Modifier.padding(end = 8.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                androidx.compose.ui.graphics.Color(
                                    red = pixelInfo.red / 255f,
                                    green = pixelInfo.green / 255f,
                                    blue = pixelInfo.blue / 255f,
                                    alpha = (alphaInput.toIntOrNull() ?: pixelInfo.alpha) / 255f
                                ),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
                
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
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
                        onSave(alpha, applyToAllSameColor)
                    }
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ImageEditor(
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
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
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(!isDraggingEnabled, pixelData, scale, offset) {
                    if (!isDraggingEnabled) {
                        detectTapGestures { tapOffset ->
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val imageWidth = imageBitmap.width.toFloat()
                            val imageHeight = imageBitmap.height.toFloat()
                            
                            val scaleToFit = minOf(
                                canvasWidth / imageWidth,
                                canvasHeight / imageHeight
                            )
                            
                            val finalScale = scaleToFit * scale
                            
                            val centerX = (canvasWidth - imageWidth * finalScale) / 2 + offset.x
                            val centerY = (canvasHeight - imageHeight * finalScale) / 2 + offset.y
                            
                            val pixelX = ((tapOffset.x - centerX) / finalScale).toInt()
                            val pixelY = ((tapOffset.y - centerY) / finalScale).toInt()
                            
                            if (pixelX in 0 until pixelData.width && 
                                pixelY in 0 until pixelData.height) {
                                val pixelInfo = pixelData.getPixel(pixelX, pixelY)
                                onPixelClick(pixelInfo)
                            }
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val imageWidth = imageBitmap.width.toFloat()
            val imageHeight = imageBitmap.height.toFloat()
            
            val scaleToFit = minOf(
                canvasWidth / imageWidth,
                canvasHeight / imageHeight
            )
            
            val finalScale = scaleToFit * scale
            val centerX = (canvasWidth - imageWidth * finalScale) / 2 + offset.x
            val centerY = (canvasHeight - imageHeight * finalScale) / 2 + offset.y
            
            // 绘制每个像素
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
                            
                            // 绘制像素
                            drawRect(
                                color = androidx.compose.ui.graphics.Color(
                                    red = pixel.red / 255f,
                                    green = pixel.green / 255f,
                                    blue = pixel.blue / 255f,
                                    alpha = pixel.alpha / 255f
                                ),
                                topLeft = Offset(pixelX, pixelY),
                                size = Size(pixelSize, pixelSize)
                            )
                            
                            // 绘制边框
                            // drawRect(
                                // color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f),
                                // topLeft = Offset(pixelX, pixelY),
                                // size = Size(pixelSize, pixelSize),
                                // style = Stroke(width = 0.5f)
                            // )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnsavedChangesDialog(
    isSavingAndExiting: Boolean,
    onCancel: () -> Unit,
    onExitWithoutSaving: () -> Unit,
    onSaveAndExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("保存修改？") },
        text = { Text("当前图片有未保存的修改，退出前是否保存？") },
        confirmButton = {
            // 采用垂直排列方式解决拥挤问题
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveAndExit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSavingAndExiting
                ) {
                    Text(if (isSavingAndExiting) "保存中..." else "保存并退出")
                }
                
                OutlinedButton(
                    onClick = onExitWithoutSaving,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSavingAndExiting,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("丢弃修改")
                }

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSavingAndExiting
                ) {
                    Text("取消", color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    )
}


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
    
    // 批量修改相同颜色像素的透明度
    fun updateAllPixelsWithColor(targetRed: Int, targetGreen: Int, targetBlue: Int, newAlpha: Int) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = getPixel(x, y)
                if (pixel.red == targetRed && pixel.green == targetGreen && pixel.blue == targetBlue) {
                    updatePixelAlpha(x, y, newAlpha)
                }
            }
        }
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

suspend fun saveImage(context: android.content.Context, pixelData: PixelData): Boolean {
    return withContext(Dispatchers.IO) {
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
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show()
                }
                true
            } ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "保存失败: 无法创建文件", Toast.LENGTH_SHORT).show()
                }
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
}

// 保存临时文件的函数
suspend fun saveToTempFile(context: android.content.Context, pixelData: PixelData): java.io.File {
    return withContext(Dispatchers.IO) {
        val bitmap = pixelData.toBitmap()
        val tempFile = java.io.File.createTempFile(
            "glow_preview_${System.currentTimeMillis()}",
            ".png",
            context.cacheDir
        )
        val outputStream = java.io.FileOutputStream(tempFile)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.close()
        tempFile
    }
}