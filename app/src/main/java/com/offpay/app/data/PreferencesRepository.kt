package com.offpay.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.offpay.app.domain.OperationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object PreferencesKeys {
    val OPERATION_MODE = stringPreferencesKey("operation_mode")
    val BATTERY_WARNING_DISMISSED = booleanPreferencesKey("battery_warning_dismissed")
    val FIRST_LAUNCH_COMPLETE = booleanPreferencesKey("first_launch_complete")
    val LAST_BALANCE_TEXT = stringPreferencesKey("last_balance_text")
    val LAST_BALANCE_TIMESTAMP = longPreferencesKey("last_balance_timestamp")
}

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val operationMode: Flow<OperationMode> = dataStore.data.map { preferences ->
        val stored = preferences[PreferencesKeys.OPERATION_MODE]
        if (stored != null) {
            try {
                OperationMode.valueOf(stored)
            } catch (_: IllegalArgumentException) {
                // Migrate legacy persisted enum names to the current set.
                //   OVERLAY     → AUTO   (very old name for the full overlay mode)
                //   MINIMAL     → ADVANCED  (renamed for clarity)
                //   DIALER      → MANUAL    (renamed for clarity)
                //   ADVANCED    → AUTO   (one prior name briefly meant the
                //                         full overlay; remap so we don't
                //                         silently switch a legacy ADVANCED
                //                         user into the new ADVANCED mode
                //                         which behaves differently)
                // Anything else falls back to AUTO.
                when (stored) {
                    "MINIMAL" -> OperationMode.ADVANCED
                    "DIALER" -> OperationMode.MANUAL
                    "OVERLAY", "ADVANCED" -> OperationMode.AUTO
                    else -> OperationMode.AUTO
                }
            }
        } else {
            OperationMode.AUTO
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

    /** Last successful balance check — full carrier reply text. */
    val lastBalanceText: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_BALANCE_TEXT]
    }

    /** Epoch millis of the last successful balance check. */
    val lastBalanceTimestamp: Flow<Long?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_BALANCE_TIMESTAMP]
    }

    suspend fun setLastBalance(text: String, timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_BALANCE_TEXT] = text
            preferences[PreferencesKeys.LAST_BALANCE_TIMESTAMP] = timestamp
        }
    }

    suspend fun clearLastBalance() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.LAST_BALANCE_TEXT)
            preferences.remove(PreferencesKeys.LAST_BALANCE_TIMESTAMP)
        }
    }
}
