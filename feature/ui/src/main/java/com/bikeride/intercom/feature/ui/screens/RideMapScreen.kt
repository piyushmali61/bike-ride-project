package com.bikeride.intercom.feature.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikeride.intercom.feature.ui.nav.DestinationGeocoder
import com.bikeride.intercom.feature.ui.nav.InteractiveRideMap
import com.bikeride.intercom.feature.ui.nav.NavigationManager
import com.bikeride.intercom.feature.ui.nav.RideDestination
import com.bikeride.intercom.feature.ui.theme.LocalAstraAccent
import com.bikeride.intercom.mesh.LocationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dedicated Ride Navigation / Map Screen matching Section 1 & Section 6 specifications:
 *
 * ┌─────────────────────────────┐
 * │        🗺️ RIDE MAP          │
 * │                             │
 * │          🏍️                 │
 * │           ↓                 │
 * │        📍 DESTINATION       │
 * │                             │
 * │   4.2 km remaining          │
 * │   ETA: 8 min                │
 * │                             │
 * ├─────────────────────────────┤
 * │ 🎙️ Intercom     🔇 Mute    │
 * │ 📍 Location      🆘 SOS     │
 * │ 📸 Picture Stop             │
 * ├─────────────────────────────┤
 * │      END NAVIGATION         │
 * └─────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideMapScreen(
    navManager: NavigationManager,
    geocoder: DestinationGeocoder,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onShareLocation: () -> Unit,
    onTriggerSos: () -> Unit,
    onPictureStop: () -> Unit,
    onBack: () -> Unit,
    isActive: Boolean = false,
    amplitude: Float = 0f
) {
    val context = LocalContext.current
    val accentColor = LocalAstraAccent.current
    val scope = rememberCoroutineScope()

    val currentLoc by navManager.currentLocation.collectAsState()
    val destination by navManager.destination.collectAsState()
    val distanceRemaining by navManager.distanceRemainingMeters.collectAsState()
    val etaMinutes by navManager.etaMinutes.collectAsState()
    val isNavigating by navManager.isNavigating.collectAsState()
    val hasArrived by navManager.hasArrived.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<RideDestination>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(destination == null) }
    var confirmSos by remember { mutableStateOf(false) }

    // Search query with debounce
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 2) {
            delay(400)
            isSearching = true
            searchResults = geocoder.search(searchQuery)
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    BackHandler(onBack = onBack)

    // An SOS alarms every rider in the convoy, so it is confirmed first and sent once
    if (confirmSos) {
        AlertDialog(
            onDismissRequest = { confirmSos = false },
            containerColor = Color(0xFF131D2D),
            title = { Text("Send SOS to the convoy?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Every rider hears an alarm and gets your location.", color = Color(0xFFCBD5E1), fontSize = 15.sp) },
            confirmButton = {
                Button(
                    onClick = { confirmSos = false; onTriggerSos() },
                    modifier = Modifier.height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("SEND SOS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { confirmSos = false }) { Text("CANCEL", color = Color(0xFF94A3B8)) } }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF090D16))) {
        // ── 1. Interactive Slippy OpenStreetMap Canvas ──
        InteractiveRideMap(
            currentLat = currentLoc?.first,
            currentLon = currentLoc?.second,
            destination = destination,
            modifier = Modifier.fillMaxSize(),
            isNavigationActive = isNavigating,
            onMapTap = { tapLat, tapLon ->
                scope.launch {
                    val placeName = geocoder.reverseGeocode(tapLat, tapLon) ?: "Pinned Location"
                    val pickedDest = RideDestination(name = placeName, latitude = tapLat, longitude = tapLon)
                    navManager.startNavigation(pickedDest)
                    showSearchBar = false
                    Toast.makeText(context, "Destination set: $placeName", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // ── 2. Top Header & Search Bar ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xE6111827),
                border = BorderStroke(1.dp, Color(0xFF2B3547)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "🗺️ RIDE MAP",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = accentColor)
                    }
                }
            }

            AnimatedVisibility(
                visible = showSearchBar,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search ride destination…", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = accentColor) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xF0111827),
                            unfocusedContainerColor = Color(0xF0111827),
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isSearching) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = accentColor)
                    }

                    if (searchResults.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xF0111827),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 220.dp)
                        ) {
                            LazyColumn {
                                items(searchResults) { result ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                navManager.startNavigation(result)
                                                showSearchBar = false
                                                searchQuery = ""
                                                searchResults = emptyList()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Place, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(result.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    HorizontalDivider(color = Color(0xFF1E293B))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 3. Bottom Navigation Info & Action Panel ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Voice activity, same card as the home screen
            com.bikeride.intercom.feature.ui.components.ChannelQuietCard(
                isActive = isActive, isMuted = isMuted, amplitude = amplitude
            )

            // Live Navigation Info Card (Remaining Distance & ETA)
            if (destination != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xE6111827),
                    border = BorderStroke(2.dp, if (hasArrived) Color(0xFF22C55E) else accentColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (hasArrived) "🏁 ARRIVED AT DESTINATION" else "📍 DESTINATION",
                                color = if (hasArrived) Color(0xFF22C55E) else accentColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            destination?.name ?: "Selected Location",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val distText = distanceRemaining?.let { LocationHelper.spokenDistance(it) } ?: "Calculating…"
                            val etaText = etaMinutes?.let { "ETA: $it min" } ?: "Calculating…"

                            Text(
                                "🛣️ $distText remaining",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (hasArrived) Color(0xFF14532D) else Color(0xFF1E293B)
                            ) {
                                Text(
                                    if (hasArrived) "Arrived" else "⏱️ $etaText",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = if (hasArrived) Color(0xFF4ADE80) else accentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xD9111827),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.TouchApp, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Tap anywhere on the map or search above to set destination",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Riding Action Bar (Glove-Friendly Touch Targets)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF2111827),
                border = BorderStroke(1.dp, Color(0xFF2B3547)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Row 1: Intercom / Mute & Location
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NavActionButton(
                            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = if (isMuted) "🔇 Mute (Muted)" else "🎙️ Intercom",
                            color = if (isMuted) Color(0xFFEF4444) else Color(0xFF22C55E),
                            modifier = Modifier.weight(1f),
                            onClick = onToggleMute
                        )
                        NavActionButton(
                            icon = Icons.Filled.MyLocation,
                            label = "📍 Location",
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f),
                            onClick = onShareLocation
                        )
                    }

                    // Row 2: SOS & Picture Stop
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NavActionButton(
                            icon = Icons.Filled.Campaign,
                            label = "🆘 SOS",
                            color = Color(0xFFFF1744),
                            modifier = Modifier.weight(1f),
                            onClick = { confirmSos = true }
                        )
                        NavActionButton(
                            icon = Icons.Filled.PhotoCamera,
                            label = "📸 Picture Stop",
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1f),
                            onClick = onPictureStop
                        )
                    }

                    // Row 3: End Navigation Button
                    if (destination != null) {
                        Button(
                            onClick = { navManager.endNavigation() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B))
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("END NAVIGATION", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.5.dp, color)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        }
    }
}
