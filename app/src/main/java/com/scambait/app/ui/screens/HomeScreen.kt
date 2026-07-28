package com.scambait.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.scambait.app.data.model.CallLogEntity
import com.scambait.app.service.ScamTrapService
import com.scambait.app.ui.theme.AccentEmerald
import com.scambait.app.ui.theme.AlertRed
import com.scambait.app.ui.theme.DarkBg
import com.scambait.app.ui.theme.GlassBorder
import com.scambait.app.ui.theme.PrimaryNeon
import com.scambait.app.ui.theme.TextPrimary
import com.scambait.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    isTrapActive: Boolean,
    protectContacts: Boolean,
    service: ScamTrapService?,
    recentCalls: List<CallLogEntity>,
    onToggleTrap: (Boolean) -> Unit,
    onToggleProtectContacts: (Boolean) -> Unit,
    onOpenCallScreeningRole: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGuide: () -> Unit,
    onSelectCall: (Long) -> Unit
) {
    val isCallActive by service?.isCallActive?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(false)
    val currentCallerId by service?.currentCallerId?.collectAsState() ?: androidx.compose.runtime.mutableStateOf("")

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ScamBait",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "AI Call Trapper (Pixel 8a)",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }

                Row {
                    IconButton(onClick = onNavigateToGuide) {
                        Icon(
                            imageVector = Icons.Default.ContactPhone,
                            contentDescription = "Forwarding Setup Guide",
                            tint = PrimaryNeon
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Persona & Settings",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Master Protection Toggle Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x1F1E293B)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isTrapActive) AccentEmerald else AlertRed)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isTrapActive) "Protection ACTIVE" else "Protection PAUSED",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextPrimary
                            )
                        }

                        Switch(
                            checked = isTrapActive,
                            onCheckedChange = onToggleTrap,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentEmerald
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contacts Safeguard Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Safeguard",
                                tint = PrimaryNeon,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Bypass Saved Contacts",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (protectContacts) "ON: Friends/family bypass bot • Turn OFF to test with a friend" else "OFF: Trapping enabled for ALL callers (Test Mode Active)",
                                    fontSize = 11.sp,
                                    color = if (protectContacts) TextSecondary else PrimaryNeon
                                )
                            }
                        }

                        Switch(
                            checked = protectContacts,
                            onCheckedChange = onToggleProtectContacts,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryNeon
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // System Permissions & Setup Cards
            val context = LocalContext.current
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Required Pixel 8a Setup",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNeon
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Grant these 2 system settings so ScamBait can auto-answer and hang up on your cellular number:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "1. Call Screening Role", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "Allows app to catch calls before ringing", fontSize = 11.sp, color = TextSecondary)
                        }
                        Button(
                            onClick = onOpenCallScreeningRole,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "Enable", color = PrimaryNeon, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "2. Auto-Hangup Accessibility", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "Allows bot to tap 'End Call' automatically", fontSize = 11.sp, color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "Open Settings", color = PrimaryNeon, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Test Simulation Button
            Button(
                onClick = { service?.handleIncomingCall("+1 (800) 555-0199") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Simulate Incoming Scam Call",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Trapped Calls Feed
            Text(
                text = "Recent Trapped Calls",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (recentCalls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No trapped calls recorded yet.\nForwarded scam calls will appear here.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentCalls) { call ->
                        CallItemCard(call = call, onClick = { onSelectCall(call.id) })
                    }
                }
            }
        }

        // Active Call Overlay (Using Box + zIndex per User Rule instead of Dialog)
        if (isCallActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF00F172A))
                    .zIndex(100f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Active Call",
                            tint = AccentEmerald,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "TRAPPING SCAMMER",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentEmerald
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentCallerId,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "AI persona 'Margaret' is conversing and recording...",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Button(
                        onClick = { service?.endCurrentCall() },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallItemCard(call: CallLogEntity, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(call.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0x1F1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = call.callerNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    if (!call.isSpamConfirmed) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "REAL CALLER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNeon,
                            modifier = Modifier
                                .background(PrimaryNeon.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$formattedDate • ${call.durationSeconds}s",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play recording",
                tint = PrimaryNeon
            )
        }
    }
}
