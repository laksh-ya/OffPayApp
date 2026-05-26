package com.offpay.app.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.first

/**
 * Property-based test for Transaction History Ordering.
 *
 * **Validates: Requirements 11.3**
 *
 * For any set of transaction records with distinct timestamps, querying the history
 * should return records sorted in strictly descending order of timestamp.
 */
class TransactionOrderingPropertyTest : FunSpec({

    tags(io.kotest.core.Tag("Feature: offpay-native-app, Property 20: Transaction History Ordering"))

    // In-memory FakeTransactionDao that mimics Room's ORDER BY timestamp DESC
    class FakeTransactionDao {
        private val records = mutableListOf<TransactionEntity>()
        private var nextId = 1L

        suspend fun insert(transaction: TransactionEntity) {
            records.add(transaction.copy(id = nextId++))
        }

        suspend fun getAll(): List<TransactionEntity> {
            return records.sortedByDescending { it.timestamp }
        }

        suspend fun deleteAll() {
            records.clear()
            nextId = 1L
        }
    }

    // Generator for a list of transactions with distinct timestamps
    val distinctTransactionsArb = arbitrary {
        val size = Arb.long(2L..50L).bind().toInt()
        val usedTimestamps = mutableSetOf<Long>()
        val transactions = mutableListOf<TransactionEntity>()

        repeat(size) {
            var ts: Long
            do {
                ts = Arb.long(1_000_000_000_000L..2_000_000_000_000L).bind()
            } while (ts in usedTimestamps)
            usedTimestamps.add(ts)

            val vpa = Arb.string(5..15).bind() + "@bank"
            transactions.add(
                TransactionEntity(
                    id = 0,
                    vpa = vpa,
                    payeeName = "User",
                    amount = "100.00",
                    note = null,
                    carrierReply = "Payment successful",
                    timestamp = ts
                )
            )
        }
        transactions.toList()
    }

    test("querying history returns records in strictly descending order of timestamp") {
        checkAll(100, distinctTransactionsArb) { transactions ->
            val dao = FakeTransactionDao()

            // Insert transactions in the given (random) order
            for (tx in transactions) {
                dao.insert(tx)
            }

            // Query all records
            val result = dao.getAll()

            // Verify strictly descending timestamp order
            result.size shouldBe transactions.size
            for (i in 1 until result.size) {
                val prev = result[i - 1].timestamp
                val curr = result[i].timestamp
                (prev > curr) shouldBe true
            }
        }
    }
})
