package com.example.aiconversation.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class UserPreferencesRepository(context: Context) {
    private val ds = context.dataStore

    companion object {
        val CAMERA_ON_KEY = booleanPreferencesKey("camera_on")
        val LANGUAGE_KEY = stringPreferencesKey("language_preference")
    }

    val isCameraOnFlow: Flow<Boolean> = ds.data.map { prefs ->
        prefs[CAMERA_ON_KEY] ?: false
    }

    val languageFlow: Flow<String> = ds.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: "en"
    }

    suspend fun setCameraOn(isOn: Boolean) {
        ds.edit { prefs ->
            prefs[CAMERA_ON_KEY] = isOn
        }
    }

    suspend fun setLanguage(lang: String) {
        ds.edit { prefs ->
            prefs[LANGUAGE_KEY] = lang
        }
    }
}
