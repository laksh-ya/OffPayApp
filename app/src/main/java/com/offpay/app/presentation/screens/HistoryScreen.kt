package com.offpay.app.presentation.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpay.app.data.TransactionEntity
import com.offpay.app.presentation.HistoryViewModel
import com.offpay.app.presentation.ui.components.NeoPopCard
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction history with per-item delete via swipe-from-right or
 * long-press, and "CLEAR ALL" with a confirm sheet at the top.
 *
 * Tapping a row expands it to show the carrier reply plus a "Pay again"
 * shortcut that navigates back to the Pay screen pre-filled with this
 * transaction's recipient + amount + note.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onPay: () -> Unit,
    onPayAgain: (TransactionEntity) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val txns by viewModel.transactions.collectAsState()
    var showClearAllDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Header(
            count = txns.size,
            onClose = onClose,
            onClearAll = { showClearAllDialog = true }
        )
        Spacer(Modifier.height(20.dp))

        if (txns.isEmpty()) {
            EmptyState(onPay = onPay, modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = txns, key = { it.id }) { txn ->
                    SwipeableTransactionCard(
                        txn = txn,
                        onDelete = { viewModel.deleteTransaction(txn.id) },
                        onLongPress = { pendingDelete = txn },
                        onPayAgain = { onPayAgain(txn) }
                    )
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear all transactions?", style = NeoPopType.HeadlineLarge) },
            text = {
                Text(
                    text = "This will delete every transaction in your history. " +
                        "This action can't be undone.",
                    style = NeoPopType.BodyMedium
                )
            },
            confirmButton = {
                Box(
                    Modifier.clickable {
                        viewModel.clearHistory()
                        showClearAllDialog = false
                    }
                ) {
                    Text(
                        text = "Clear all",
                        style = NeoPopType.LabelLarge,
                        color = NeoPopColors.Danger,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            dismissButton = {
                Box(
                    Modifier.clickable { showClearAllDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        style = NeoPopType.LabelLarge,
                        color = NeoPopColors.TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            containerColor = NeoPopColors.SurfaceHigh,
            titleContentColor = NeoPopColors.TextPrimary,
            textContentColor = NeoPopColors.TextSecondary
        )
    }

    pendingDelete?.let { txn ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this transaction?", style = NeoPopType.HeadlineLarge) },
            text = {
                Text(
                    text = "₹${txn.amount} to ${txn.vpa}. This can't be undone.",
                    style = NeoPopType.BodyMedium
                )
            },
            confirmButton = {
                Box(
                    Modifier.clickable {
                        viewModel.deleteTransaction(txn.id)
                        pendingDelete = null
                    }
                ) {
                    Text(
                        text = "Delete",
                        style = NeoPopType.LabelLarge,
                        color = NeoPopColors.Danger,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            dismissButton = {
                Box(
                    Modifier.clickable { pendingDelete = null }
                ) {
                    Text(
                        text = "Cancel",
                        style = NeoPopType.LabelLarge,
                        color = NeoPopColors.TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            },
            containerColor = NeoPopColors.SurfaceHigh,
            titleContentColor = NeoPopColors.TextPrimary,
            textContentColor = NeoPopColors.TextSecondary
        )
    }
}

@Composable
private fun Header(count: Int, onClose: () -> Unit, onClearAll: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CornerIconButton(
            icon = Icons.Default.ArrowBack,
            contentDescription = "Back",
            onClick = onClose
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Recent",
                style = NeoPopType.LabelMedium,
                color = NeoPopColors.Accent
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (count > 0) "$count transaction${if (count == 1) "" else "s"}" else "no transactions",
                style = NeoPopType.BodySmall,
                color = NeoPopColors.TextMuted
            )
        }
        if (count > 0) {
            Box(
                Modifier
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClearAll()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Clear all",
                    style = NeoPopType.LabelMedium,
                    color = NeoPopColors.Danger
                )
            }
        }
    }
}

/**
 * 40dp tap-target with a subtle 12% white hairline ring and a 0.92
 * scale-on-press feedback. Mirrors the FAQ corner button.
 */
@Composable
private fun CornerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "history_close_scale"
    )
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                drawRect(
                    color = NeoPopColors.TextPrimary.copy(alpha = 0.12f),
                    style = Stroke(width = 1f)
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = NeoPopColors.TextPrimary
        )
    }
}

@Composable
private fun EmptyState(onPay: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(NeoPopColors.Accent)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "no transactions yet",
            style = NeoPopType.HeadlineLarge,
            color = NeoPopColors.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "your payments will appear here",
            style = NeoPopType.BodyMedium,
            color = NeoPopColors.TextMuted
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .clickable { onPay() }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Make a payment",
                style = NeoPopType.LabelLarge,
                color = NeoPopColors.Accent
            )
        }
    }
}

@Composable
private fun SwipeableTransactionCard(
    txn: TransactionEntity,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    onPayAgain: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // Only allow EndToStart (right→left swipe) to trigger delete.
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Clean reveal: surface-coloured row that just shows a trash
            // icon at the trailing edge — no full-width red wash.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(NeoPopColors.SurfaceHigh)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = NeoPopColors.Danger,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        TransactionCard(txn = txn, onLongPress = onLongPress, onPayAgain = onPayAgain)
    }
}

@Composable
private fun TransactionCard(
    txn: TransactionEntity,
    onLongPress: () -> Unit,
    onPayAgain: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val view = LocalView.current

    NeoPopCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .pointerInput(txn.id) {
                detectTapGestures(
                    onLongPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongPress()
                    },
                    onTap = { expanded = !expanded }
                )
            }
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = txn.vpa,
                        style = NeoPopType.Mono.copy(
                            color = NeoPopColors.TextPrimary,
                            fontSize = 14.sp
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(txn.timestamp),
                        style = NeoPopType.BodySmall,
                        color = NeoPopColors.TextMuted
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    StatusPill()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "₹${txn.amount}",
                        style = NeoPopType.MonoAmount.copy(
                            color = NeoPopColors.TextPrimary,
                            fontSize = 17.sp
                        )
                    )
                }
            }
            if (!txn.note.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = txn.note,
                    style = NeoPopType.BodySmall,
                    color = NeoPopColors.TextSecondary
                )
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
                        text = "Carrier reply",
                        style = NeoPopType.LabelSmall,
                        color = NeoPopColors.TextMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = txn.carrierReply,
                        style = NeoPopType.BodyMedium,
                        color = NeoPopColors.TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    // "Pay again" — repeats this transaction by routing the
                    // user back to the Pay screen with VPA + amount + note
                    // pre-filled. The Pay flow then validates the form and
                    // requests PIN as usual; we never resend money silently.
                    PayAgainButton(onClick = onPayAgain)
                }
            }
        }
    }
}

@Composable
private fun StatusPill() {
    Box(
        Modifier
            .background(NeoPopColors.Accent.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "✓ paid",
            style = NeoPopType.LabelSmall,
            color = NeoPopColors.Accent
        )
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

/**
 * Compact "Pay again" affordance shown inside an expanded transaction
 * row. Lime accent border + replay icon + label, full-width, scales down
 * on press. Triggers the host's onPayAgain callback which routes back to
 * the Pay screen with the VPA / amount / note pre-filled.
 */
@Composable
private fun PayAgainButton(onClick: () -> Unit) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pay_again_scale"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(NeoPopColors.Accent.copy(alpha = 0.10f))
            .drawBehind {
                drawRect(
                    color = NeoPopColors.Accent,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = null,
                tint = NeoPopColors.Accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Pay again",
                style = NeoPopType.LabelLarge,
                color = NeoPopColors.Accent
            )
        }
    }
}
