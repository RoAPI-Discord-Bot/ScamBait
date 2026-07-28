package com.scambait.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.scambait.app.engine.TtsEngine
import com.scambait.app.ui.theme.DarkBg
import com.scambait.app.ui.theme.PrimaryNeon
import com.scambait.app.ui.theme.TextPrimary
import com.scambait.app.ui.theme.TextSecondary

@Composable
fun PersonaSettingsScreen(
    currentPersona: String,
    currentPrompt: String,
    pitch: Float,
    speed: Float,
    onSavePersona: (String, String, Float, Float) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedPersona by remember { mutableStateOf(currentPersona) }
    var promptText by remember { mutableStateOf(currentPrompt) }
    var pitchVal by remember { mutableFloatStateOf(pitch) }
    var speedVal by remember { mutableFloatStateOf(speed) }

    val ttsEngine = remember { TtsEngine(context, pitchVal, speedVal) }

    DisposableEffect(Unit) {
        onDispose { ttsEngine.shutdown() }
    }

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
                    text = "AI Persona & Voice Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Persona Select
            Text(
                text = "Choose AI Persona",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            listOf(
                "MARGARET" to "Margaret (78 y/o Grandma - Confused, slow, stalls scammer)",
                "ARTHUR" to "Arthur (81 y/o Senior - Tech impaired, talks about modem)",
                "NAVY_SEAL" to "Commander Jack (Navy Seal - Aggressive, yelling, hangs up call with [HANG_UP])",
                "CUSTOM" to "Custom Prompt Persona (Use [HANG_UP] to auto-disconnect call)"
            ).forEach { (key, label) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        RadioButton(
                            selected = (selectedPersona == key),
                            onClick = { selectedPersona = key },
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryNeon)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }
                }
            }

            if (selectedPersona == "CUSTOM") {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("Custom System Persona Prompt") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryNeon,
                        unfocusedBorderColor = TextSecondary,
                        focusedLabelColor = PrimaryNeon,
                        unfocusedLabelColor = TextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // TTS Controls
            Text(
                text = "Voice Pitch: ${String.format("%.2f", pitchVal)}x",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Slider(
                value = pitchVal,
                onValueChange = {
                    pitchVal = it
                    ttsEngine.applySettings(pitchVal, speedVal)
                },
                valueRange = 0.5f..1.5f,
                colors = SliderDefaults.colors(thumbColor = PrimaryNeon, activeTrackColor = PrimaryNeon)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Speech Rate: ${String.format("%.2f", speedVal)}x",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Slider(
                value = speedVal,
                onValueChange = {
                    speedVal = it
                    ttsEngine.applySettings(pitchVal, speedVal)
                },
                valueRange = 0.5f..1.5f,
                colors = SliderDefaults.colors(thumbColor = PrimaryNeon, activeTrackColor = PrimaryNeon)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Test Voice Button
            Button(
                onClick = {
                    ttsEngine.speak("Hello sonny, hold on let me put on my reading glasses.")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Test Voice",
                    tint = PrimaryNeon
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Test Voice Sample", color = PrimaryNeon)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Settings
            Button(
                onClick = {
                    onSavePersona(selectedPersona, promptText, pitchVal, speedVal)
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save Settings", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
