package com.scambait.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scambait_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val IS_TRAP_ACTIVE = booleanPreferencesKey("is_trap_active")
        val PROTECT_CONTACTS = booleanPreferencesKey("protect_contacts")
        val PERSONA_TYPE = stringPreferencesKey("persona_type")
        val CUSTOM_PROMPT = stringPreferencesKey("custom_prompt")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val SIP_URI = stringPreferencesKey("sip_uri")
        val SIP_USERNAME = stringPreferencesKey("sip_username")
        val SIP_SERVER = stringPreferencesKey("sip_server")
        val SIP_PASSWORD = stringPreferencesKey("sip_password")
    }

    val isTrapActive: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_TRAP_ACTIVE] ?: true
    }

    val protectContacts: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PROTECT_CONTACTS] ?: true // Safe default: Protect standard contacts
    }

    val personaType: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PERSONA_TYPE] ?: "MARGARET"
    }

    val customPrompt: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[CUSTOM_PROMPT] ?: "You are Margaret, a 78-year-old confused grandmother. You keep mishearing tech support terms, talking about your cat Barnaby, and asking the caller to slow down."
    }

    val ttsPitch: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[TTS_PITCH] ?: 0.75f
    }

    val ttsSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[TTS_SPEED] ?: 0.85f
    }

    val sipUri: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SIP_URI] ?: "scamtrap_bot@sip.scambaiter.net"
    }

    val sipUsername: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SIP_USERNAME] ?: ""
    }

    val sipServer: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SIP_SERVER] ?: ""
    }

    val sipPassword: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SIP_PASSWORD] ?: ""
    }

    suspend fun setTrapActive(active: Boolean) {
        context.dataStore.edit { prefs -> prefs[IS_TRAP_ACTIVE] = active }
    }

    suspend fun setProtectContacts(protect: Boolean) {
        context.dataStore.edit { prefs -> prefs[PROTECT_CONTACTS] = protect }
    }

    suspend fun setPersonaType(type: String) {
        context.dataStore.edit { prefs -> prefs[PERSONA_TYPE] = type }
    }

    suspend fun setCustomPrompt(prompt: String) {
        context.dataStore.edit { prefs -> prefs[CUSTOM_PROMPT] = prompt }
    }

    suspend fun setTtsParams(pitch: Float, speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[TTS_PITCH] = pitch
            prefs[TTS_SPEED] = speed
        }
    }

    suspend fun setSipUri(uri: String) {
        context.dataStore.edit { prefs -> prefs[SIP_URI] = uri }
    }

    suspend fun setSipCredentials(username: String, server: String, pass: String) {
        context.dataStore.edit { prefs ->
            prefs[SIP_USERNAME] = username
            prefs[SIP_SERVER] = server
            prefs[SIP_PASSWORD] = pass
        }
    }
}
