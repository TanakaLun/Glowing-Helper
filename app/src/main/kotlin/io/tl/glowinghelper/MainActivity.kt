package io.tl.glowinghelper

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
        
        // 设置全屏UI
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
fun PNGFineTuneApp() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var pixelData by remember { mutableStateOf<PixelData?>(null) }
    var selectedPixel by remember { mutableStateOf<PixelInfo?>(null) }
    var isDraggingEnabled by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                imageUri = it
                scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        loadImageBitmap(context, it)
                    }
                    bitmap?.let { loadedBitmap ->
                        imageBitmap = loadedBitmap.asImageBitmap()
                        pixelData = PixelData.fromBitmap(loadedBitmap)
                    }
                }
            }
        }
    )
    
    if (imageUri == null) {
        SelectImageScreen(
            onSelectImage = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    imagePicker.launch("image/*")
                } else {
                    val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                    if (ContextCompat.checkSelfPermission(context, permission) == 
                        PackageManager.PERMISSION_GRANTED) {
                        imagePicker.launch("image/*")
                    } else {
                        imagePicker.launch("image/*")
                    }
                }
            }
        )
    } else {
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
                            text = if (isDraggingEnabled) "缩放拖拽" else "编辑模式",
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
                            drawRect(
                                color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f),
                                topLeft = Offset(pixelX, pixelY),
                                size = Size(pixelSize, pixelSize),
                                style = Stroke(width = 0.5f)
                            )
                        }
                    }
                }
            }
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
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
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}