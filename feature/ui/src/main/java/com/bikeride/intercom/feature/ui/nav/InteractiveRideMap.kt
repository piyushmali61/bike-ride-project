package com.bikeride.intercom.feature.ui.nav

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikeride.intercom.feature.ui.theme.LocalAstraAccent
import com.bikeride.intercom.mesh.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * High-Performance, OLED-Dark Interactive Motorcycle Map.
 * Renders OpenStreetMap slippy tiles with offline disk caching and dark night mode styling.
 * Supports:
 * - Touch pan / drag with gloves
 * - Pinch to zoom (zoom 3 to 18)
 * - Tap anywhere to drop / set destination
 * - Live Rider GPS icon (🏍️) with pulsing glow ring
 * - Destination Marker (📍) with distance and callout
 * - Direct route connection line
 */
@Composable
fun InteractiveRideMap(
    currentLat: Double?,
    currentLon: Double?,
    destination: RideDestination?,
    modifier: Modifier = Modifier,
    onMapTap: ((lat: Double, lon: Double) -> Unit)? = null,
    isNavigationActive: Boolean = false
) {
    val context = LocalContext.current
    val accentColor = LocalAstraAccent.current
    val coroutineScope = rememberCoroutineScope()

    // Default center to current location, destination, or center of India (20.59, 78.96)
    var centerLat by remember { mutableDoubleStateOf(currentLat ?: destination?.latitude ?: 20.5937) }
    var centerLon by remember { mutableDoubleStateOf(currentLon ?: destination?.longitude ?: 78.9629) }
    var zoomLevel by remember { mutableFloatStateOf(15.0f) }

    // Follow rider location automatically when navigation starts
    LaunchedEffect(currentLat, currentLon, isNavigationActive) {
        if (isNavigationActive && currentLat != null && currentLon != null) {
            centerLat = currentLat
            centerLon = currentLon
        }
    }

    val tileManager = remember { OsmTileManager(context) }
    var tileRefreshTrigger by remember { mutableIntStateOf(0) }

    // Pulsing circle animation for rider position
    val infiniteTransition = rememberInfiniteTransition(label = "riderPulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // Dark-mode ColorMatrix for night motorcycle riding
    val darkMapFilter = remember {
        val matrix = ColorMatrix(floatArrayOf(
            -0.75f, 0f, 0f, 0f, 215f,
            0f, -0.75f, 0f, 0f, 220f,
            0f, 0f, -0.75f, 0f, 235f,
            0f, 0f, 0f, 1f, 0f
        ))
        ColorFilter.colorMatrix(matrix)
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF090D16))) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomLevel = (zoomLevel * zoom).coerceIn(4f, 18.5f)
                        val z = zoomLevel.toInt()
                        val scale = 2.0.pow(zoomLevel.toDouble()) * 256.0
                        val dLon = -pan.x / scale * 360.0
                        val dLat = pan.y / scale * 180.0
                        centerLon = (centerLon + dLon).coerceIn(-180.0, 180.0)
                        centerLat = (centerLat + dLat).coerceIn(-85.0, 85.0)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val (tapLat, tapLon) = screenToLatLon(
                            screenX = tapOffset.x,
                            screenY = tapOffset.y,
                            centerX = size.width / 2f,
                            centerY = size.height / 2f,
                            centerLat = centerLat,
                            centerLon = centerLon,
                            zoom = zoomLevel.toDouble()
                        )
                        onMapTap?.invoke(tapLat, tapLon)
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f
            val z = zoomLevel.toInt().coerceIn(3, 18)

            // ── 1. Draw OpenStreetMap Tiles ──
            val centerTileX = lonToTileX(centerLon, z)
            val centerTileY = latToTileY(centerLat, z)
            val centerPixelX = lonToPixelX(centerLon, z)
            val centerPixelY = latToPixelY(centerLat, z)

            val tilesX = ceil(width / 256f).toInt() + 2
            val tilesY = ceil(height / 256f).toInt() + 2
            val startTileX = floor(centerTileX - tilesX / 2f).toInt()
            val endTileX = ceil(centerTileX + tilesX / 2f).toInt()
            val startTileY = floor(centerTileY - tilesY / 2f).toInt()
            val endTileY = ceil(centerTileY + tilesY / 2f).toInt()

            val maxTile = (1 shl z)

            for (tx in startTileX..endTileX) {
                for (ty in startTileY..endTileY) {
                    if (ty in 0 until maxTile) {
                        val normTx = ((tx % maxTile) + maxTile) % maxTile
                        val tileBitmap = tileManager.getTile(z, normTx, ty) {
                            tileRefreshTrigger++
                        }

                        val tileScreenX = centerX + (tx * 256 - centerPixelX).toFloat()
                        val tileScreenY = centerY + (ty * 256 - centerPixelY).toFloat()

                        if (tileBitmap != null) {
                            drawImage(
                                image = tileBitmap,
                                dstOffset = IntOffset(tileScreenX.toInt(), tileScreenY.toInt()),
                                dstSize = IntSize(256, 256),
                                colorFilter = darkMapFilter
                            )
                        } else {
                            // Subtle placeholder grid for tiles loading
                            drawRect(
                                color = Color(0xFF101726),
                                topLeft = Offset(tileScreenX, tileScreenY),
                                size = androidx.compose.ui.geometry.Size(256f, 256f)
                            )
                            drawRect(
                                color = Color(0xFF1E293B),
                                topLeft = Offset(tileScreenX, tileScreenY),
                                size = androidx.compose.ui.geometry.Size(256f, 256f),
                                style = Stroke(1f)
                            )
                        }
                    }
                }
            }

            // ── 2. Draw Route Polyline from Rider to Destination ──
            if (currentLat != null && currentLon != null && destination != null) {
                val riderScreen = latLonToScreen(currentLat, currentLon, centerX, centerY, centerLat, centerLon, zoomLevel.toDouble())
                val destScreen = latLonToScreen(destination.latitude, destination.longitude, centerX, centerY, centerLat, centerLon, zoomLevel.toDouble())

                // Glow path
                drawLine(
                    color = accentColor.copy(alpha = 0.35f),
                    start = riderScreen,
                    end = destScreen,
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
                // Solid path
                drawLine(
                    color = accentColor,
                    start = riderScreen,
                    end = destScreen,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }

            // ── 3. Draw Destination Pin (📍) ──
            destination?.let { dest ->
                val destPos = latLonToScreen(dest.latitude, dest.longitude, centerX, centerY, centerLat, centerLon, zoomLevel.toDouble())

                // Destination arrival radius circle
                val pixelsPerMeter = (2.0.pow(zoomLevel.toDouble()) * 256.0) / (40_075_016.686 * cos(Math.toRadians(dest.latitude)))
                val radiusPx = (dest.arrivalRadiusMeters * pixelsPerMeter).toFloat().coerceAtLeast(18f)

                drawCircle(
                    color = Color(0xFFEF4444).copy(alpha = 0.22f),
                    radius = radiusPx,
                    center = destPos
                )
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = radiusPx,
                    center = destPos,
                    style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )

                // Pin pole
                drawLine(
                    color = Color(0xFFEF4444),
                    start = destPos,
                    end = Offset(destPos.x, destPos.y - 32f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                // Pin head
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = 12f,
                    center = Offset(destPos.x, destPos.y - 32f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(destPos.x, destPos.y - 32f)
                )
            }

            // ── 4. Draw Rider Position (🏍️) ──
            if (currentLat != null && currentLon != null) {
                val riderPos = latLonToScreen(currentLat, currentLon, centerX, centerY, centerLat, centerLon, zoomLevel.toDouble())

                // Pulsing GPS radar wave
                drawCircle(
                    color = accentColor.copy(alpha = pulseAlpha),
                    radius = pulseRadius,
                    center = riderPos
                )

                // Solid outer ring
                drawCircle(
                    color = accentColor,
                    radius = 12f,
                    center = riderPos
                )
                // Core dot
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = riderPos
                )
            }
        }

        // Floating Map Controls: Center on Me, Zoom In, Zoom Out
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = {
                    if (currentLat != null && currentLon != null) {
                        centerLat = currentLat
                        centerLon = currentLon
                        zoomLevel = 16.0f
                    }
                },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B).copy(alpha = 0.90f))
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Center on Me", tint = accentColor)
            }

            IconButton(
                onClick = { zoomLevel = (zoomLevel + 1f).coerceAtMost(18.5f) },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B).copy(alpha = 0.90f))
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom In", tint = Color.White)
            }

            IconButton(
                onClick = { zoomLevel = (zoomLevel - 1f).coerceAtLeast(4f) },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B).copy(alpha = 0.90f))
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }
        }
    }
}

// ── Web Mercator Conversions ──────────────────────────────────────────

private fun lonToTileX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * (1 shl zoom)

private fun latToTileY(lat: Double, zoom: Int): Double {
    val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
    return (1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * (1 shl zoom)
}

private fun lonToPixelX(lon: Double, zoom: Int): Double = lonToTileX(lon, zoom) * 256.0

private fun latToPixelY(lat: Double, zoom: Int): Double = latToTileY(lat, zoom) * 256.0

private fun latLonToScreen(
    lat: Double, lon: Double,
    centerX: Float, centerY: Float,
    centerLat: Double, centerLon: Double,
    zoom: Double
): Offset {
    val scale = 2.0.pow(zoom) * 256.0
    val x = (lon + 180.0) / 360.0 * scale
    val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
    val y = (1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * scale

    val centerScale = 2.0.pow(zoom) * 256.0
    val cX = (centerLon + 180.0) / 360.0 * centerScale
    val cLatRad = Math.toRadians(centerLat.coerceIn(-85.05112878, 85.05112878))
    val cY = (1.0 - asinh(tan(cLatRad)) / Math.PI) / 2.0 * centerScale

    return Offset(centerX + (x - cX).toFloat(), centerY + (y - cY).toFloat())
}

private fun screenToLatLon(
    screenX: Float, screenY: Float,
    centerX: Float, centerY: Float,
    centerLat: Double, centerLon: Double,
    zoom: Double
): Pair<Double, Double> {
    val scale = 2.0.pow(zoom) * 256.0
    val cX = (centerLon + 180.0) / 360.0 * scale
    val cLatRad = Math.toRadians(centerLat.coerceIn(-85.05112878, 85.05112878))
    val cY = (1.0 - asinh(tan(cLatRad)) / Math.PI) / 2.0 * scale

    val targetPixelX = cX + (screenX - centerX)
    val targetPixelY = cY + (screenY - centerY)

    val lon = (targetPixelX / scale) * 360.0 - 180.0
    val n = Math.PI - 2.0 * Math.PI * (targetPixelY / scale)
    val lat = Math.toDegrees(atan(sinh(n)))

    return lat.coerceIn(-85.0, 85.0) to lon.coerceIn(-180.0, 180.0)
}

// ── Slippy Tile Manager with Memory & Disk Cache ───────────────────────

private class OsmTileManager(private val context: Context) {
    private val memoryCache = LruCache<String, ImageBitmap>(80)
    private val diskDir = File(context.cacheDir, "osm_tiles").apply { mkdirs() }
    private val downloading = ConcurrentHashMap.newKeySet<String>()
    private val tileScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    fun getTile(z: Int, x: Int, y: Int, onLoaded: () -> Unit): ImageBitmap? {
        val key = "$z/$x/$y"
        memoryCache.get(key)?.let { return it }

        // Check disk
        val diskFile = File(diskDir, "$z-$x-$y.png")
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)?.asImageBitmap()
                if (bitmap != null) {
                    memoryCache.put(key, bitmap)
                    return bitmap
                }
            } catch (e: Exception) {
                diskFile.delete()
            }
        }

        // Fetch asynchronously if not already in flight
        if (downloading.add(key)) {
            tileScope.launch {
                try {
                    val url = "https://tile.openstreetmap.org/$z/$x/$y.png"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "AstraRide/1.3 (Smart Motorcycle Intercom; Android)")
                        .build()
                    val response = client.newCall(req).execute()
                    response.use { res ->
                        if (res.isSuccessful) {
                            val bytes = res.body?.bytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                FileOutputStream(diskFile).use { it.write(bytes) }
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                if (bmp != null) {
                                    memoryCache.put(key, bmp)
                                    withContext(Dispatchers.Main) { onLoaded() }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore network glitches silently
                } finally {
                    downloading.remove(key)
                }
            }
        }
        return null
    }
}
