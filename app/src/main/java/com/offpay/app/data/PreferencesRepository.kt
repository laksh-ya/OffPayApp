package com.offpay.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.offpay.app.domain.OperationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object PreferencesKeys {
    val OPERATION_MODE = stringPreferencesKey("operation_mode")
    val BATTERY_WARNING_DISMISSED = booleanPreferencesKey("battery_warning_dismissed")
    val FIRST_LAUNCH_COMPLETE = booleanPreferencesKey("first_launch_complete")
}

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val operationMode: Flow<OperationMode> = dataStore.data.map { preferences ->
        val stored = preferences[PreferencesKeys.OPERATION_MODE]
        if (stored != null) {
            try {
                OperationMode.valueOf(stored)
            } catch (_: IllegalArgumentException) {
                OperationMode.DIALER
            }
        } else {
            OperationMode.DIALER
        }
    }

    suspend fun setOperationMode(mode: OperationMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.OPERATION_MODE] = mode.name
        }
    }

    val batteryWarningDismissed: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BATTERY_WARNING_DISMISSED] ?: false
    }

    suspend fun setBatteryWarningDismissed(dismissed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BATTERY_WARNING_DISMISSED] = dismissed
        }
    }

    val firstLaunchComplete: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FIRST_LAUNCH_COMPLETE] ?: false
    }

    suspend fun setFirstLaunchComplete(complete: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIRST_LAUNCH_COMPLETE] = complete
        }
    }
}
