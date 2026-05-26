package com.offpay.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.offpay.app.domain.OperationMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration tests for DataStore preference persistence.
 * Validates Requirements 4.5 (operation mode persistence), 1.1, 1.2.
 *
 * These tests verify that preferences survive DataStore recreation,
 * simulating app restart scenarios.
 */
@RunWith(AndroidJUnit4::class)
class DataStoreIntegrationTest {

    private lateinit var context: Context
    private lateinit var dataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: PreferencesRepository

    private val testDataStoreName = "test_preferences"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile = context.preferencesDataStoreFile(testDataStoreName)
        dataStore = PreferenceDataStoreFactory.create {
            dataStoreFile
        }
        repository = PreferencesRepository(dataStore)
    }

    @After
    fun teardown() {
        // Clean up the test DataStore file
        dataStoreFile.delete()
    }

    @Test
    fun operationMode_defaultsToAuto() = runBlocking {
        val mode = repository.operationMode.first()
        assertEquals(OperationMode.AUTO, mode)
    }

    @Test
    fun operationMode_persistsAdvancedMode() = runBlocking {
        repository.setOperationMode(OperationMode.ADVANCED)
        val mode = repository.operationMode.first()
        assertEquals(OperationMode.ADVANCED, mode)
    }

    @Test
    fun operationMode_persistsAutoMode() = runBlocking {
        repository.setOperationMode(OperationMode.AUTO)
        val mode = repository.operationMode.first()
        assertEquals(OperationMode.AUTO, mode)
    }

    @Test
    fun operationMode_survivesRepositoryRecreation() = runBlocking {
        // Write with original repository
        repository.setOperationMode(OperationMode.AUTO)

        // Recreate repository with same DataStore file (simulates restart)
        val newDataStore = PreferenceDataStoreFactory.create {
            dataStoreFile
        }
        val newRepository = PreferencesRepository(newDataStore)

        // Read back — should still be AUTO
        val mode = newRepository.operationMode.first()
        assertEquals(OperationMode.AUTO, mode)
    }

    @Test
    fun batteryWarningDismissed_defaultsToFalse() = runBlocking {
        val dismissed = repository.batteryWarningDismissed.first()
        assertFalse(dismissed)
    }

    @Test
    fun batteryWarningDismissed_persistsTrue() = runBlocking {
        repository.setBatteryWarningDismissed(true)
        val dismissed = repository.batteryWarningDismissed.first()
        assertTrue(dismissed)
    }

    @Test
    fun batteryWarningDismissed_survivesRepositoryRecreation() = runBlocking {
        repository.setBatteryWarningDismissed(true)

        val newDataStore = PreferenceDataStoreFactory.create {
            dataStoreFile
        }
        val newRepository = PreferencesRepository(newDataStore)

        val dismissed = newRepository.batteryWarningDismissed.first()
        assertTrue(dismissed)
    }

    @Test
    fun firstLaunchComplete_defaultsToFalse() = runBlocking {
        val complete = repository.firstLaunchComplete.first()
        assertFalse(complete)
    }

    @Test
    fun firstLaunchComplete_survivesRepositoryRecreation() = runBlocking {
        repository.setFirstLaunchComplete(true)

        val newDataStore = PreferenceDataStoreFactory.create {
            dataStoreFile
        }
        val newRepository = PreferencesRepository(newDataStore)

        val complete = newRepository.firstLaunchComplete.first()
        assertTrue(complete)
    }

    @Test
    fun multiplePreferences_persistIndependently() = runBlocking {
        repository.setOperationMode(OperationMode.ADVANCED)
        repository.setBatteryWarningDismissed(true)
        repository.setFirstLaunchComplete(true)

        // Recreate
        val newDataStore = PreferenceDataStoreFactory.create {
            dataStoreFile
        }
        val newRepository = PreferencesRepository(newDataStore)

        assertEquals(OperationMode.ADVANCED, newRepository.operationMode.first())
        assertTrue(newRepository.batteryWarningDismissed.first())
        assertTrue(newRepository.firstLaunchComplete.first())
    }
}
