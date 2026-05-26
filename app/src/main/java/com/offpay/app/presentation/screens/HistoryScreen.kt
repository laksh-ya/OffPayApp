package com.offpay.app.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpay.app.data.TransactionEntity
import com.offpay.app.presentation.HistoryViewModel
import com.offpay.app.presentation.ui.components.NeoPopCard
import com.offpay.app.presentation.ui.components.NeoPopDangerOutlinedButton
import com.offpay.app.presentation.ui.components.NeoPopPrimaryButton
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onPay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val txns by viewModel.transactions.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Header(count = txns.size)
        Spacer(Modifier.height(20.dp))

        if (txns.isEmpty()) {
            EmptyState(onPay = onPay, modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = txns, key = { it.id }) { txn ->
                    TransactionCard(txn)
                }
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    NeoPopDangerOutlinedButton(
                        text = "Clear History",
                        onClick = viewModel::clearHistory,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun Header(count: Int) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "RECENT",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "History",
                style = NeoPopType.DisplayLarge,
                color = NeoPopColors.TextPrimary
            )
        }
        if (count > 0) {
            Box(
                Modifier
                    .background(NeoPopColors.SurfaceHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = count.toString(),
                    style = NeoPopType.LabelLarge,
                    color = NeoPopColors.Accent
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onPay: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeoPopCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = null,
                    tint = NeoPopColors.TextMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "NO TRANSACTIONS YET",
                    style = NeoPopType.LabelMedium,
                    color = NeoPopColors.TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Make your first payment to see it here.",
                    style = NeoPopType.BodyMedium,
                    color = NeoPopColors.TextMuted
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        NeoPopPrimaryButton(
            text = "Make Your First Payment",
            onClick = onPay,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TransactionCard(txn: TransactionEntity) {
    var expanded by remember { mutableStateOf(false) }
    NeoPopCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = txn.vpa,
                        style = NeoPopType.Mono.copy(color = NeoPopColors.TextPrimary)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(txn.timestamp),
                        style = NeoPopType.BodySmall,
                        color = NeoPopColors.TextMuted
                    )
                }
                Text(
                    text = "₹${txn.amount}",
                    style = NeoPopType.Mono.copy(
                        color = NeoPopColors.TextPrimary,
                        fontSize = 20.sp
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(NeoPopColors.Success)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "PAID",
                    style = NeoPopType.LabelSmall,
                    color = NeoPopColors.Success
                )
                if (!txn.note.isNullOrBlank()) {
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "•  ${txn.note}",
                        style = NeoPopType.BodySmall,
                        color = NeoPopColors.TextSecondary
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(NeoPopColors.Border)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "CARRIER REPLY",
                        style = NeoPopType.LabelSmall,
                        color = NeoPopColors.TextMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = txn.carrierReply,
                        style = NeoPopType.BodyMedium,
                        color = NeoPopColors.TextSecondary
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val delta = now - ts
    if (delta < 60_000) return "Just now"
    if (delta < 3_600_000) return "${delta / 60_000} min ago"
    val sdf = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
    return sdf.format(Date(ts))
}
