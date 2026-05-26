package com.offpay.app.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.offpay.app.presentation.ui.theme.NeoPopColors
import com.offpay.app.presentation.ui.theme.NeoPopType

/**
 * NeoPOP text field — looks "punched into" the page rather than raised.
 * Black inner fill, lighter recessed shadow on the top/left edges, single
 * underline below. Animated label that floats up on focus or when filled.
 */
@Composable
fun NeoPopTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    textStyle: TextStyle = NeoPopType.BodyLarge.copy(color = NeoPopColors.TextPrimary)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hasError = error != null
    val labelFloating = focused || value.isNotEmpty()

    val borderColor by animateColorAsState(
        targetValue = when {
            hasError -> NeoPopColors.Danger
            focused -> NeoPopColors.Accent
            else -> NeoPopColors.Border
        },
        label = "border"
    )
    val labelColor = when {
        hasError -> NeoPopColors.Danger
        focused -> NeoPopColors.Accent
        else -> NeoPopColors.TextSecondary
    }

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(NeoPopColors.Black)
                .drawBehind {
                    // Inset shadow on the top + left edges (the "punched in" feel)
                    val shadow = NeoPopColors.SurfaceHigher
                    drawLine(
                        color = shadow,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = shadow,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 2f
                    )
                    // Solid underline at the bottom (state-aware colour)
                    drawLine(
                        color = borderColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 3f
                    )
                }
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column {
                // Floating label
                Text(
                    text = label,
                    style = if (labelFloating) NeoPopType.LabelSmall else NeoPopType.LabelMedium,
                    color = labelColor
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = NeoPopColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = textStyle,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        visualTransformation = visualTransformation,
                        cursorBrush = SolidColor(NeoPopColors.Accent),
                        singleLine = singleLine,
                        enabled = enabled,
                        interactionSource = interactionSource,
                        decorationBox = { inner ->
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    text = placeholder,
                                    style = textStyle.copy(color = NeoPopColors.TextMuted)
                                )
                            }
                            inner()
                        }
                    )
                }
            }
        }
        if (hasError) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = NeoPopColors.Danger,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = error!!,
                    style = NeoPopType.BodySmall,
                    color = NeoPopColors.Danger
                )
            }
        }
    }
}
