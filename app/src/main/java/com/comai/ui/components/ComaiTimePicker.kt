package com.comai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.comai.util.Time12

/**
 * Re-export of ClockTimeBar and ClockTimePickerDialog for backward compatibility.
 * All time inputs now use the official ClockTimePicker with circular dial and AM/PM toggle.
 */
@Composable
fun ComaiCompactTimeBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    defaultTime: Time12 = Time12(8, 0, true)
) {
    ClockTimeBar(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        isError = isError,
        defaultTime = defaultTime
    )
}

@Composable
fun ComaiTimePickerDialog(
    initialTime: Time12,
    onDismiss: () -> Unit,
    onConfirm: (Time12) -> Unit
) {
    ClockTimePickerDialog(
        initialTime = initialTime,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
