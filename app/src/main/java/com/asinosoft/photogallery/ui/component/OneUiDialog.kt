package com.asinosoft.photogallery.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.ui.theme.isDarkColorScheme

@Composable
fun OneUiDialog(
    title: String,
    onDismissRequest: () -> Unit,
    dismissText: String,
    modifier: Modifier = Modifier,
    neutralText: String? = null,
    onNeutral: () -> Unit = {},
    confirmText: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismissRequest)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = oneUiDialogColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp,
                modifier = modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clickable(interactionSource = null, indication = null) {}
            ) {
                Column(Modifier.padding(top = 24.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        content = content
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .height(56.dp)
                    ) {
                        OneUiDialogButton(dismissText, onClick = onDismissRequest)

                        if (null != neutralText) {
                            VerticalDivider(
                                modifier = Modifier.height(20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            OneUiDialogButton(neutralText, onClick = onNeutral)
                        }

                        if (null != confirmText) {
                            VerticalDivider(
                                modifier = Modifier.height(20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            OneUiDialogButton(confirmText, enabled = confirmEnabled, onClick = onConfirm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.OneUiDialogButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
    ) {
        val style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            textAlign = TextAlign.Center,
        )
        BasicText(
            text = text,
            style = style,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 11.sp,
                maxFontSize = 16.sp,
                stepSize = 0.5.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun OneUiTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onKeyboardAction: KeyboardActionHandler? = null,
) {
    TextField(
        state = state,
        label = { Text(label) },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = imeAction
        ),
        onKeyboardAction = onKeyboardAction,
        colors = oneUiTextFieldColors(),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun OneUiSelectField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { width = it.width }
                .clickable { onExpandedChange(!expanded) }
                .padding(start = 16.dp, end = 8.dp, top = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }

        FloatingDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(with(density) { width.toDp() }),
            content = menuContent
        )
    }
}

@Composable
private fun oneUiTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
)

@Composable
private fun oneUiDialogColor(): Color =
    if (isDarkColorScheme()) Color(0xFF252525) else MaterialTheme.colorScheme.surface
