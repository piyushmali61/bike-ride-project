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

/** Edit display name, status, avatar and colour. The name is independent from the device id. */
@Composable
fun ProfileDialog(
    current: RiderProfile,
    onDismiss: () -> Unit,
    onSave: (RiderProfile) -> Unit
) {
    var name by remember { mutableStateOf(current.name) }
    var status by remember { mutableStateOf(current.status) }
    var avatar by remember { mutableStateOf(current.avatar) }
    var color by remember { mutableIntStateOf(current.colorIndex) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFF38BDF8),
        unfocusedBorderColor = Color(0xFF334155),
        focusedLabelColor = Color(0xFF38BDF8),
        unfocusedLabelColor = Color(0xFF94A3B8)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151C2A),
        title = { Text("Rider Profile", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RiderAvatar(RiderProfile(name, status, avatar, color), size = 56)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name.ifBlank { "Rider" }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(status, color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
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
                Spacer(Modifier.height(14.dp))
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
                Spacer(Modifier.height(14.dp))
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
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your profile is shared only with riders in your convoy room.",
                    color = Color(0xFF64748B), fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(RiderProfile(name.ifBlank { "Rider" }, status, avatar, color)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
            ) { Text("SAVE", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color(0xFF94A3B8)) }
        }
    )
}
