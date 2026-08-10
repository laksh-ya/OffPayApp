package com.offpay.app.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offpay.app.domain.SimInfo
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType

@Composable
fun SimPickerDialog(
    sims: List<SimInfo>,
    title: String = "Choose SIM",
    onSelect: (SimInfo) -> Unit,
    onAskEveryTime: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sims.forEach { sim ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(sim) }
                            .padding(vertical = 12.dp)
                    ) {
                        Column {
                            Text(
                                text = sim.carrierName?.ifBlank { "SIM ${sim.slotIndex + 1}" }
                                    ?: "SIM ${sim.slotIndex + 1}",
                                fontWeight = FontWeight.Bold,
                                color = NeoPopColors.TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Slot ${sim.slotIndex + 1}",
                                color = NeoPopColors.TextSecondary,
                                style = NeoPopType.LabelSmall
                            )
                        }
                    }
                }
                
                if (onAskEveryTime != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAskEveryTime() }
                            .padding(vertical = 12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Ask every time",
                                fontWeight = FontWeight.Bold,
                                color = NeoPopColors.Accent
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Disable default SIM",
                                color = NeoPopColors.TextSecondary,
                                style = NeoPopType.LabelSmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
