package com.offpay.app.presentation.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType

/**
 * Segmented toggle with the NeoPOP "pressed in" look on the active segment.
 * The active segment fills with the accent colour and shifts slightly inward;
 * inactive segments are dim with a thin border.
 */
@Composable
fun <T> NeoPopToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(NeoPopColors.Surface)
            .drawBehind {
                drawRect(NeoPopColors.Border, style = Stroke(width = 2f))
            },
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        options.forEachIndexed { index, (value, label) ->
            val isActive = value == selected
            val bg by animateColorAsState(
                if (isActive) NeoPopColors.Accent else NeoPopColors.Surface,
                label = "toggle_bg_$index"
            )
            val fg by animateColorAsState(
                if (isActive) NeoPopColors.Black else NeoPopColors.TextSecondary,
                label = "toggle_fg_$index"
            )
            Box(
                Modifier
                    .weight(1f)
                    .background(bg)
                    .clickable {
                        if (!isActive) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSelect(value)
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.uppercase(),
                    style = NeoPopType.LabelMedium,
                    color = fg,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
