package com.bikeride.intercom.feature.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.bikeride.intercom.feature.ui.theme.LocalAstraAccent
import com.bikeride.intercom.mesh.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Picture Stop Capture & Preview Dialog.
 *
 * Directly opens phone camera for one-tap photo capture, previews photo with
 * rider name, location, and timestamp, and compresses for mesh delivery.
 */
@Composable
fun PictureStopDialog(
    riderName: String,
    onDismiss: () -> Unit,
    onSendPhoto: (jpeg: ByteArray, locationName: String?, lat: Double?, lon: Double?) -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalAstraAccent.current
    val scope = rememberCoroutineScope()

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCompressing by remember { mutableStateOf(false) }

    val location = remember { LocationHelper.lastKnown(context) }
    var locationName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(location) {
        if (location != null) {
            locationName = LocationHelper.placeName(context, location.first, location.second)
        }
    }

    val timestampStr = remember {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    }

    fun prepareNewPhotoUri(): Uri {
        val file = File(context.cacheDir, "picture_stop_${System.currentTimeMillis()}.jpg")
        photoFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        photoUri = uri
        return uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile != null && photoFile!!.exists() && photoFile!!.length() > 0) {
            try {
                val bmp = BitmapFactory.decodeFile(photoFile!!.absolutePath)
                photoBitmap = bmp
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load photo", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        } else {
            // User cancelled camera without taking photo
            onDismiss()
        }
    }

    // Launch camera automatically when dialog opens
    LaunchedEffect(Unit) {
        val uri = prepareNewPhotoUri()
        cameraLauncher.launch(uri)
    }

    if (photoBitmap != null) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF151C2A),
                border = BorderStroke(1.5.dp, Color(0xFF2B3547))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📸", fontSize = 22.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Picture Stop Preview",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Photo Container with Stamp Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        Image(
                            bitmap = photoBitmap!!.asImageBitmap(),
                            contentDescription = "Captured Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Bottom Overlay Badge
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            color = Color.Black.copy(alpha = 0.70f)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    "📸 $riderName shared a Picture Stop",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val locDisplay = locationName ?: location?.let { "%.4f, %.4f".format(it.first, it.second) } ?: "On the road"
                                    Text("📍 $locDisplay", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text("🕐 $timestampStr", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Glove-friendly Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Retake Button
                        OutlinedButton(
                            onClick = {
                                val uri = prepareNewPhotoUri()
                                photoBitmap = null
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF475569)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("RETAKE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Send Button
                        Button(
                            onClick = {
                                val uri = photoUri ?: return@Button
                                isCompressing = true
                                scope.launch {
                                    val jpeg = withContext(Dispatchers.Default) { compressPhoto(context, uri) }
                                    isCompressing = false
                                    if (jpeg != null) {
                                        onSendPhoto(jpeg, locationName, location?.first, location?.second)
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Could not compress photo", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isCompressing,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            if (isCompressing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("SEND", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compresses photo to fit within mesh MAX_PHOTO_BYTES (16 KB) for reliable offline transmission.
 */
private fun compressPhoto(context: Context, uri: Uri): ByteArray? {
    try {
        for (maxSide in intArrayOf(360, 280, 200)) {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxSide.toFloat() / maxOf(w, h)
                if (scale < 1f) {
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            for (quality in intArrayOf(60, 45, 30)) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= com.bikeride.intercom.mesh.MAX_PHOTO_BYTES) {
                    bitmap.recycle()
                    return out.toByteArray()
                }
            }
            bitmap.recycle()
        }
    } catch (e: Exception) {
        // Return null on failure
    }
    return null
}
