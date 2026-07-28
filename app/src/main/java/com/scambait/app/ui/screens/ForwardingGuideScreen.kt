package com.scambait.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scambait.app.ui.theme.DarkBg
import com.scambait.app.ui.theme.GlassBorder
import com.scambait.app.ui.theme.PrimaryNeon
import com.scambait.app.ui.theme.TextPrimary
import com.scambait.app.ui.theme.TextSecondary

@Composable
fun ForwardingGuideScreen(
    currentSipUri: String,
    onSaveSipUri: (String) -> Unit,
    sipUsername: String = "",
    sipPassword: String = "",
    sipServer: String = "",
    onSaveSipCredentials: (username: String, password: String, server: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var sipAddress by remember { mutableStateOf(currentSipUri) }
    var username by remember { mutableStateOf(sipUsername) }
    var password by remember { mutableStateOf(sipPassword) }
    var server by remember { mutableStateOf(sipServer) }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Carrier Call Forwarding Setup",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Explanation Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How Conditional Forwarding Works",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNeon
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "When a scammer calls your personal number, tap DECLINE. Your carrier automatically redirects ONLY declined or spam-blocked calls to your custom ScamBait SIP bot without ringing your line.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SIP Configuration Field
            OutlinedTextField(
                value = sipAddress,
                onValueChange = {
                    sipAddress = it
                    onSaveSipUri(it)
                },
                label = { Text("Your 10-Digit SIP/VoIP Phone Number") },
                placeholder = { Text("e.g. 5551234567") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryNeon,
                    unfocusedBorderColor = TextSecondary,
                    focusedLabelColor = PrimaryNeon,
                    unfocusedLabelColor = TextSecondary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "⚠️ Note: Carriers like Verizon require a 10-digit phone number (digits only, no letters/@). Enter your VoIP DID or Google Voice number.",
                fontSize = 11.sp,
                color = PrimaryNeon
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Carrier Activation Codes",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            val digitsOnly = sipAddress.filter { it.isDigit() }.ifEmpty { "5551234567" }

            // Carrier 1: T-Mobile / Metro
            CarrierCodeCard(
                title = "T-Mobile / MetroPCS",
                codeDecline = "*67*$digitsOnly#",
                codeNoAnswer = "*61*$digitsOnly#",
                context = context
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Carrier 2: AT&T / Cricket
            CarrierCodeCard(
                title = "AT&T / Cricket",
                codeDecline = "*67*$digitsOnly#",
                codeNoAnswer = "*61*$digitsOnly#",
                context = context
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Carrier 3: Verizon
            CarrierCodeCard(
                title = "Verizon Wireless",
                codeDecline = "*71$digitsOnly",
                codeNoAnswer = "*71$digitsOnly",
                deactivateCode = "*73",
                context = context
            )
        }
    }
}

@Composable
fun CarrierCodeCard(
    title: String,
    codeDecline: String,
    codeNoAnswer: String,
    deactivateCode: String? = null,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x1F1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Forward on Decline/No Answer: $codeDecline", fontSize = 13.sp, color = TextSecondary)
            if (deactivateCode != null) {
                Text(text = "Deactivate Forwarding: $deactivateCode", fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:" + Uri.encode(codeDecline))
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = PrimaryNeon)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Dial Activation Code", color = PrimaryNeon, fontSize = 12.sp)
                }

                if (deactivateCode != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:" + Uri.encode(deactivateCode))
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Deactivate (*73)", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
