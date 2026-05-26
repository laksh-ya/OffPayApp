package com.offpay.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "vpa") val vpa: String,
    @ColumnInfo(name = "payee_name") val payeeName: String?,
    @ColumnInfo(name = "amount") val amount: String,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "carrier_reply") val carrierReply: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)
