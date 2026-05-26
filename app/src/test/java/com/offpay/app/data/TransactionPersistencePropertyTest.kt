package com.offpay.app.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Property-based tests for Transaction Persistence Round-Trip.
 *
 * **Validates: Requirements 11.1**
 *
 * Since Room with SQLCipher requires Android instrumentation, this test uses
 * a FakeTransactionDao that implements TransactionDao in-memory to verify
 * the round-trip property of the HistoryRepository logic.
 */
class TransactionPersistencePropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: offpay-native-app, Property 18: Transaction Persistence Round-Trip"))

    // In-memory fake DAO
    class FakeTransactionDao : TransactionDao {
        private val records = MutableStateFlow<List<TransactionEntity>>(emptyList())
        private var nextId = 1L

        override fun getAll(): Flow<List<TransactionEntity>> =
            records.map { list -> list.sortedByDescending { it.timestamp }.take(200) }

        override suspend fun insert(transaction: TransactionEntity) {
            val withId = transaction.copy(id = nextId++)
            records.update { it + withId }
        }

        override suspend fun trimOldest() {
            records.update { list ->
                list.sortedByDescending { it.timestamp }.take(200)
            }
        }

        override suspend fun deleteById(id: Long) {
            records.update { list -> list.filterNot { it.id == id } }
        }

        override suspend fun deleteAll() {
            records.update { emptyList() }
        }

        override suspend fun count(): Int = records.value.size
    }

    // Generator for non-empty VPA strings (alphanumeric + @)
    val vpaArb = arbitrary {
        val localLen = Arb.int(3..10).bind()
        val domainLen = Arb.int(3..8).bind()
        val local = (1..localLen).map {
            "abcdefghijklmnopqrstuvwxyz0123456789"[Arb.int(0..35).bind()]
        }.joinToString("")
        val domain = (1..domainLen).map {
            "abcdefghijklmnopqrstuvwxyz"[Arb.int(0..25).bind()]
        }.joinToString("")
        "$local@$domain"
    }

    // Generator for valid amount strings (positive decimal)
    val amountArb = arbitrary {
        val whole = Arb.int(1..5000).bind()
        val decimal = Arb.int(0..99).bind()
        if (decimal == 0) "$whole.00" else "$whole.${decimal.toString().padStart(2, '0')}"
    }

    // Generator for positive timestamps
    val timestampArb = Arb.long(1L..System.currentTimeMillis())

    // Generator for non-empty carrier reply strings
    val carrierReplyArb = arbitrary {
        val len = Arb.int(5..50).bind()
        Arb.string(len..len).bind().let { s ->
            if (s.isBlank()) "Transaction successful" else s
        }
    }

    test("inserting a valid TransactionEntity and querying returns entry with matching fields") {
        checkAll(100, vpaArb, amountArb, timestampArb, carrierReplyArb) { vpa, amount, timestamp, carrierReply ->
            val dao = FakeTransactionDao()
            val repo = HistoryRepository(dao)

            // Insert via the DAO directly to control the timestamp
            val entity = TransactionEntity(
                vpa = vpa,
                amount = amount,
                timestamp = timestamp,
                carrierReply = carrierReply,
                payeeName = null,
                note = null
            )
            dao.insert(entity)

            // Query all records
            val all = dao.getAll().first()

            // Verify round-trip: list contains an entry with matching fields
            all.any { record ->
                record.vpa == vpa &&
                    record.amount == amount &&
                    record.timestamp == timestamp &&
                    record.carrierReply == carrierReply
            } shouldBe true
        }
    }

    test("HistoryRepository.recordTransaction persists and is retrievable with matching fields") {
        checkAll(100, vpaArb, amountArb, carrierReplyArb) { vpa, amount, carrierReply ->
            val dao = FakeTransactionDao()
            val repo = HistoryRepository(dao)

            // Use repository API to record the transaction
            repo.recordTransaction(
                vpa = vpa,
                payeeName = null,
                amount = amount,
                note = null,
                carrierReply = carrierReply
            )

            // Query via repository flow
            val all = repo.transactions.first()

            // Verify the persisted record has matching VPA, amount, and carrier reply
            all.any { record ->
                record.vpa == vpa &&
                    record.amount == amount &&
                    record.carrierReply == carrierReply
            } shouldBe true
        }
    }
})
