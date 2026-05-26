package com.offpay.app.data

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: TransactionDao) {

    val transactions: Flow<List<TransactionEntity>> = dao.getAll()

    suspend fun recordTransaction(
        vpa: String,
        payeeName: String?,
        amount: String,
        note: String?,
        carrierReply: String
    ) {
        val entity = TransactionEntity(
            vpa = vpa,
            payeeName = payeeName,
            amount = amount,
            note = note,
            carrierReply = carrierReply,
            timestamp = System.currentTimeMillis()
        )
        dao.insert(entity)
        // Enforce 200-record cap
        if (dao.count() > 200) {
            dao.trimOldest()
        }
    }

    /** Delete a single transaction by id. */
    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearHistory() {
        dao.deleteAll()
    }

    suspend fun count(): Int = dao.count()
}
