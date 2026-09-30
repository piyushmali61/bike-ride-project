package com.bikeride.intercom.feature.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bikeride.intercom.mesh.RiderProfile

/** Colours riders can pick; index is what travels in the profile packet. */
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Person

val RiderColors = listOf(
    Color(0xFF38BDF8), Color(0xFF22C55E), Color(0xFFF97316), Color(0xFFEF4444),
    Color(0xFFA855F7), Color(0xFFEAB308), Color(0xFF14B8A6), Color(0xFFEC4899)
)

fun riderColor(index: Int) = RiderColors[index.mod(RiderColors.size)]

@Composable
fun RiderAvatar(profile: RiderProfile, size: Int = 40) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(riderColor(profile.colorIndex).copy(alpha = 0.22f))
            .border(2.dp, riderColor(profile.colorIndex), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(profile.avatar, fontSize = (size * 0.45f).sp)
    }
}

/** Edit display name, status, avatar, colour, and bike information. */
@Composable
fun ProfileDialog(
    current: RiderProfile,
    onDismiss: () -> Unit,
    onSave: (RiderProfile) -> Unit,
    onDeleteBike: (() -> Unit)? = null,
    onDeleteRider: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Rider, 1: Bike
    var name by remember { mutableStateOf(current.name) }
    var status by remember { mutableStateOf(current.status) }
    var avatar by remember { mutableStateOf(current.avatar) }
    var color by remember { mutableIntStateOf(current.colorIndex) }

    var bikeName by remember { mutableStateOf(current.bikeName) }
    var bikeModel by remember { mutableStateOf(current.bikeModel.ifBlank { "Yamaha R15 V4" }) }
    var bikeNickname by remember { mutableStateOf(current.bikeNickname) }
    var bikePlate by remember { mutableStateOf(current.bikePlate) }

    var showDeleteBikeConfirm by remember { mutableStateOf(false) }
    var showDeleteRiderConfirm by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFF38BDF8),
        unfocusedBorderColor = Color(0xFF334155),
        focusedLabelColor = Color(0xFF38BDF8),
        unfocusedLabelColor = Color(0xFF94A3B8)
    )

    val popularBikes = listOf(
        "CB Hornet 125",
        "Hunter 350",
        "Yamaha R15 V4",
        "KTM RC 390",
        "Kawasaki Ninja ZX-6R",
        "BMW S1000RR",
        "Ducati Panigale V4"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151C2A),
        title = {
            Column {
                Text("Rider & Bike Profile", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedTab = 0 },
                        color = if (selectedTab == 0) Color(0xFF0EA5E9) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("RIDER", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedTab = 1 },
                        color = if (selectedTab == 1) Color(0xFFFF2A42) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.DirectionsBike, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("BIKE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (selectedTab == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RiderAvatar(RiderProfile(name, status, avatar, color), size = 56)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(name.ifBlank { "Rider" }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(status.ifBlank { "Ready to ride" }, color = Color(0xFF94A3B8), fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        label = { Text("Display name") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = status,
                        onValueChange = { status = it.take(40) },
                        label = { Text("Status") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Avatar", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RiderProfile.AVATARS.forEach { a ->
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (a == avatar) Color(0xFF1E3A5F) else Color(0xFF1C2433))
                                    .clickable { avatar = a },
                                contentAlignment = Alignment.Center
                            ) { Text(a, fontSize = 20.sp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Colour", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        RiderColors.forEachIndexed { i, c ->
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (i == color) BorderStroke(3.dp, Color.White) else BorderStroke(0.dp, Color.Transparent), CircleShape)
                                    .clickable { color = i }
                            )
                        }
                    }
                    if (onDeleteRider != null) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { showDeleteRiderConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset Rider Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = bikeName,
                        onValueChange = { bikeName = it.take(30) },
                        label = { Text("Bike Name (e.g. My Hornet)") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bikeModel,
                        onValueChange = { bikeModel = it.take(30) },
                        label = { Text("Bike Model") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bikeNickname,
                        onValueChange = { bikeNickname = it.take(30) },
                        label = { Text("Bike Nickname (optional)") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = bikePlate,
                        onValueChange = { bikePlate = it.take(20).uppercase() },
                        label = { Text("Registration Number (optional)") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Popular Bikes:", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    popularBikes.forEach { bike ->
                        Text(
                            text = "• $bike",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { bikeModel = bike }
                                .padding(vertical = 3.dp)
                        )
                    }

                    if (onDeleteBike != null) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { showDeleteBikeConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF2A42)),
                            border = BorderStroke(1.dp, Color(0xFFFF2A42).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete Bike Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        RiderProfile(
                            name = name.ifBlank { "Rider" },
                            status = status,
                            avatar = avatar,
                            colorIndex = color,
                            bikeName = bikeName,
                            bikeModel = bikeModel.ifBlank { "Yamaha R15 V4" },
                            bikeNickname = bikeNickname,
                            bikePlate = bikePlate,
                            bikeImagePath = current.bikeImagePath
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
            ) { Text("SAVE", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color(0xFF94A3B8)) }
        }
    )

    if (showDeleteBikeConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteBikeConfirm = false },
            containerColor = Color(0xFF151C2A),
            title = {
                Text("Delete this item?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete your saved bike details? This cannot be undone.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteBikeConfirm = false
                        onDeleteBike?.invoke()
                        bikeName = ""
                        bikeModel = "Yamaha R15 V4"
                        bikeNickname = ""
                        bikePlate = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A42))
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBikeConfirm = false }) { Text("Cancel", color = Color(0xFF94A3B8)) }
            }
        )
    }

    if (showDeleteRiderConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteRiderConfirm = false },
            containerColor = Color(0xFF151C2A),
            title = {
                Text("Delete this item?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to reset your rider name and profile data?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteRiderConfirm = false
                        onDeleteRider?.invoke()
                        name = "Rider"
                        status = "Ready to ride"
                        avatar = "🏍️"
                        color = 0
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A42))
                ) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRiderConfirm = false }) { Text("Cancel", color = Color(0xFF94A3B8)) }
            }
        )
    }
}
