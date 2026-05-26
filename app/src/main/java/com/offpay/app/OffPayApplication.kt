package com.offpay.app

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.offpay.app.data.AppDatabase
import com.offpay.app.data.HistoryRepository
import com.offpay.app.data.PreferencesRepository
import com.offpay.app.domain.ActionRunner
import com.offpay.app.platform.CarrierDetector
import com.offpay.app.platform.OverlayControllerImpl
import com.offpay.app.platform.QrScannerManager
import com.offpay.app.platform.UssdEngine

/**
 * Top-level DataStore delegate (must be at file level per DataStore docs).
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Application class wiring all singleton dependencies via manual DI.
 *
 * Validates: Requirements 14.1, 14.2, 15.1
 */
class OffPayApplication : Application() {

    // ─── Data Layer ────────────────────────────────────────────────────────────

    lateinit var database: AppDatabase
        private set

    lateinit var historyRepo: HistoryRepository
        private set

    lateinit var prefsRepo: PreferencesRepository
        private set

    // ─── Platform Layer ────────────────────────────────────────────────────────

    lateinit var overlayController: OverlayControllerImpl
        private set

    lateinit var ussdEngine: UssdEngine
        private set

    lateinit var carrierDetector: CarrierDetector
        private set

    lateinit var qrScannerManager: QrScannerManager
        private set

    // ─── Domain Layer ──────────────────────────────────────────────────────────

    lateinit var actionRunner: ActionRunner
        private set

    override fun onCreate() {
        super.onCreate()

        // Data layer
        val passphrase = "offpay_secure_db".toByteArray() // In production, derive from secure source
        database = AppDatabase.create(this, passphrase)
        historyRepo = HistoryRepository(database.transactionDao())
        prefsRepo = PreferencesRepository(dataStore)

        // Platform layer
        overlayController = OverlayControllerImpl(this)
        ussdEngine = UssdEngine(this, overlayController)
        carrierDetector = CarrierDetector(this)
        qrScannerManager = QrScannerManager()

        // Domain layer
        actionRunner = ActionRunner(ussdEngine)
    }
}

/**
 * Extension property for convenient access to [OffPayApplication] from any Context.
 */
val Context.offPayApp: OffPayApplication
    get() = applicationContext as OffPayApplication
