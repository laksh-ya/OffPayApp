package com.offpay.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Integration tests for Room + SQLCipher CRUD operations.
 *
 * Validates: Requirements 11.1, 11.5
 */
@RunWith(AndroidJUnit4::class)
class RoomSqlCipherIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao
    private lateinit var dbFile: File
    private val passphrase = "test-passphrase-12345".toByteArray()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = context.getDatabasePath("test_offpay.db")
        val factory = SupportFactory(passphrase)
        db = Room.databaseBuilder(context, AppDatabase::class.java, "test_offpay.db")
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()
        dao = db.transactionDao()
    }

    @After
    fun teardown() {
        db.close()
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    // --- Insert and Query ---

    @Test
    fun insertAndQueryTransaction() = runTest {
        val entity = TransactionEntity(
            vpa = "merchant@upi",
            payeeName = "Test Merchant",
            amount = "100.00",
            note = "Test payment",
            carrierReply = "Payment of Rs 100.00 is successful",
            timestamp = System.currentTimeMillis()
        )

        dao.insert(entity)

        val results = dao.getAll().first()
        assertEquals(1, results.size)
        val stored = results[0]
        assertEquals("merchant@upi", stored.vpa)
        assertEquals("Test Merchant", stored.payeeName)
        assertEquals("100.00", stored.amount)
        assertEquals("Test payment", stored.note)
        assertEquals("Payment of Rs 100.00 is successful", stored.carrierReply)
    }

    @Test
    fun insertMultipleAndQueryReturnsDescendingOrder() = runTest {
        val now = System.currentTimeMillis()
        val entities = listOf(
            createTransaction("a@upi", "10.00", now - 2000),
            createTransaction("b@upi", "20.00", now - 1000),
            createTransaction("c@upi", "30.00", now)
        )
        entities.forEach { dao.insert(it) }

        val results = dao.getAll().first()
        assertEquals(3, results.size)
        // Most recent first
        assertEquals("c@upi", results[0].vpa)
        assertEquals("b@upi", results[1].vpa)
        assertEquals("a@upi", results[2].vpa)
    }

    // --- trimOldest ---

    @Test
    fun trimOldestRemovesExcessRecords() = runTest {
        // Insert 205 records
        val now = System.currentTimeMillis()
        for (i in 1..205) {
            dao.insert(createTransaction("user$i@upi", "$i.00", now + i))
        }

        assertEquals(205, dao.count())

        dao.trimOldest()

        assertEquals(200, dao.count())

        // Verify the oldest 5 are removed (those with smallest timestamps)
        val results = dao.getAll().first()
        assertEquals(200, results.size)
        // The first result should be the most recent (user205)
        assertEquals("user205@upi", results[0].vpa)
        // The last result should be user6 (oldest retained)
        assertEquals("user6@upi", results[199].vpa)
    }

    @Test
    fun trimOldestDoesNothingWhenUnderLimit() = runTest {
        for (i in 1..5) {
            dao.insert(createTransaction("user$i@upi", "$i.00", System.currentTimeMillis() + i))
        }

        dao.trimOldest()

        assertEquals(5, dao.count())
    }

    // --- deleteAll ---

    @Test
    fun deleteAllEmptiesTable() = runTest {
        for (i in 1..10) {
            dao.insert(createTransaction("user$i@upi", "$i.00", System.currentTimeMillis() + i))
        }
        assertEquals(10, dao.count())

        dao.deleteAll()

        assertEquals(0, dao.count())
        val results = dao.getAll().first()
        assertTrue(results.isEmpty())
    }

    // --- Encryption Verification ---

    @Test
    fun databaseFileIsNotReadableAsPlaintext() = runTest {
        // Insert data so the file has content
        dao.insert(createTransaction("secret@upi", "999.99", System.currentTimeMillis()))
        // Force a checkpoint so data is flushed to disk
        db.query("PRAGMA wal_checkpoint(FULL)", null)

        // Close the database to ensure all writes are flushed
        db.close()

        // Read raw bytes from the database file
        assertTrue("Database file should exist", dbFile.exists())
        val rawBytes = dbFile.readBytes()
        val rawText = String(rawBytes, Charsets.ISO_8859_1)

        // An unencrypted SQLite file starts with "SQLite format 3\u0000"
        assertFalse(
            "Database should not have plain SQLite header",
            rawText.startsWith("SQLite format 3")
        )

        // The VPA we inserted should not be findable in the raw bytes
        assertFalse(
            "Sensitive data should not be readable as plaintext",
            rawText.contains("secret@upi")
        )
        assertFalse(
            "Amount should not be readable as plaintext",
            rawText.contains("999.99")
        )
    }

    // --- Helper ---

    private fun createTransaction(
        vpa: String,
        amount: String,
        timestamp: Long
    ) = TransactionEntity(
        vpa = vpa,
        payeeName = null,
        amount = amount,
        note = null,
        carrierReply = "Success",
        timestamp = timestamp
    )
}
