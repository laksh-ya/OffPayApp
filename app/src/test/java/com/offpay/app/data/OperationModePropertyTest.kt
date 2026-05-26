package com.offpay.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.offpay.app.domain.OperationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import java.io.File

/**
 * Property-based tests for Operation Mode persistence round-trip.
 *
 * **Validates: Requirements 4.5**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OperationModePropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: offpay-native-app, Property 8: Operation Mode Persistence Round-Trip"))

    val modeArb = Arb.enum<OperationMode>()

    test("persisting any OperationMode and reading it back yields the same value") {
        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val tempFile = File.createTempFile("test_prefs_", ".preferences_pb")
        tempFile.deleteOnExit()
        // Delete the file so DataStore can create it fresh
        tempFile.delete()

        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { tempFile }
        )
        val repository = PreferencesRepository(dataStore)

        checkAll(100, modeArb) { mode ->
            repository.setOperationMode(mode)
            val readBack = repository.operationMode.first()
            readBack shouldBe mode
        }

        // Clean up
        tempFile.delete()
    }
})
