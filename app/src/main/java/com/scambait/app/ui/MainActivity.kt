package com.scambait.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scambait.app.data.db.AppDatabase
import com.scambait.app.data.repository.SettingsRepository
import com.scambait.app.service.ScamTrapConnectionService
import com.scambait.app.service.ScamTrapService
import com.scambait.app.ui.screens.CallDetailScreen
import com.scambait.app.ui.screens.ForwardingGuideScreen
import com.scambait.app.ui.screens.HomeScreen
import com.scambait.app.ui.screens.PersonaSettingsScreen
import com.scambait.app.ui.theme.ScamBaitTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var scamTrapService: ScamTrapService? by mutableStateOf(null)
    private var isBound by mutableStateOf(false)
    private lateinit var settingsRepository: SettingsRepository

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ScamTrapService.LocalBinder
            scamTrapService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            scamTrapService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (audioGranted) {
            ScamTrapConnectionService.registerPhoneAccount(this)
            startScamService()
            // After other permissions granted, request call screening role
            requestCallScreeningRole()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(this)

        checkAndRequestPermissions()

        setContent {
            ScamBaitTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                val isTrapActive by settingsRepository.isTrapActive.collectAsState(initial = true)
                val protectContacts by settingsRepository.protectContacts.collectAsState(initial = true)
                val personaType by settingsRepository.personaType.collectAsState(initial = "MARGARET")
                val customPrompt by settingsRepository.customPrompt.collectAsState(initial = "")
                val pitch by settingsRepository.ttsPitch.collectAsState(initial = 0.75f)
                val speed by settingsRepository.ttsSpeed.collectAsState(initial = 0.85f)
                val sipUri by settingsRepository.sipUri.collectAsState(initial = "scamtrap_bot@sip.scambaiter.net")

                val database = AppDatabase.getDatabase(applicationContext)
                val recentCalls by database.callLogDao().getAllCallLogs().collectAsState(initial = emptyList())

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            isTrapActive = isTrapActive,
                            protectContacts = protectContacts,
                            service = scamTrapService,
                            recentCalls = recentCalls,
                            onToggleTrap = { active ->
                                scope.launch { settingsRepository.setTrapActive(active) }
                            },
                            onToggleProtectContacts = { protect ->
                                scope.launch { settingsRepository.setProtectContacts(protect) }
                            },
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToGuide = { navController.navigate("guide") },
                            onSelectCall = { id -> navController.navigate("detail/$id") }
                        )
                    }

                    composable("settings") {
                        PersonaSettingsScreen(
                            currentPersona = personaType,
                            currentPrompt = customPrompt,
                            pitch = pitch,
                            speed = speed,
                            onSavePersona = { newType, newPrompt, newPitch, newSpeed ->
                                scope.launch {
                                    settingsRepository.setPersonaType(newType)
                                    settingsRepository.setCustomPrompt(newPrompt)
                                    settingsRepository.setTtsParams(newPitch, newSpeed)
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("guide") {
                        ForwardingGuideScreen(
                            currentSipUri = sipUri,
                            onSaveSipUri = { newUri ->
                                scope.launch { settingsRepository.setSipUri(newUri) }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        "detail/{callId}",
                        arguments = listOf(navArgument("callId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val callId = backStackEntry.arguments?.getLong("callId") ?: 0L
                        var selectedCall by remember { mutableStateOf<com.scambait.app.data.model.CallLogEntity?>(null) }

                        androidx.compose.runtime.LaunchedEffect(callId) {
                            selectedCall = database.callLogDao().getCallLogById(callId)
                        }

                        CallDetailScreen(
                            callLog = selectedCall,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissionsToRequest.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    /**
     * Requests the ROLE_CALL_SCREENING role so ScamBaitCallScreeningService
     * can intercept calls directly on the user's personal number.
     * No VoIP or carrier forwarding needed — works with ANY carrier.
     */
    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                startActivityForResult(roleIntent, 1001)
                Log.i("MainActivity", "Requesting Call Screening role")
            }
        }
    }

    private fun startScamService() {
        try {
            val intent = Intent(this, ScamTrapService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start service safely", e)
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}
