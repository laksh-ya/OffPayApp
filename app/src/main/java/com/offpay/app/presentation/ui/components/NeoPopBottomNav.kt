package com.offpay.app.presentation.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType

/** Description of one bottom-nav tab. */
data class NeoPopNavItem(
    val key: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Custom bottom nav. Active tab gets a 3dp lime line above the icon and a
 * filled tint; inactive tabs are muted. Top border separates the bar from
 * the screen above.
 */
@Composable
fun NeoPopBottomNav(
    items: List<NeoPopNavItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = androidx.compose.ui.platform.LocalView.current
    Column(
        modifier
            .fillMaxWidth()
            .background(NeoPopColors.Surface)
            .drawBehind {
                drawLine(
                    color = NeoPopColors.Border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 2f
                )
            }
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isActive = item.key == selectedKey
                val tint by animateColorAsState(
                    if (isActive) NeoPopColors.Accent else NeoPopColors.TextMuted,
                    label = "nav_${item.key}_tint"
                )
                val markerAlpha by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0f,
                    label = "nav_${item.key}_marker"
                )
                Column(
                    Modifier
                        .clickable {
                            if (!isActive) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onSelect(item.key)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .alpha(markerAlpha)
                            .size(width = 20.dp, height = 2.dp)
                            .background(NeoPopColors.Accent)
                    )
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        style = NeoPopType.LabelSmall,
                        color = tint
                    )
                }
            }
        }
    }
}
