@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.simon.harmonichackernews.ui.stories



import com.simon.harmonichackernews.resources.*

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import com.simon.harmonichackernews.ui.common.TextButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.presentation.StoryFrontDatePickerRequest
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

@Composable
internal fun FrontPageDatePickerDialog(
    request: StoryFrontDatePickerRequest,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit,
) {
    val selectableDates = remember(request.earliestDay, request.latestDay) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in request.earliestDay..request.latestDay
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = request.initialDay,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onSelected) },
                enabled = state.selectedDateMillis != null,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        FrontPageDatePickerContent(state = state)
    }
}

@Composable
private fun FrontPageDatePickerContent(
    state: androidx.compose.material3.DatePickerState,
) {
    DatePicker(
        state = state,
        title = {
            Text(
                text = "Select front page day",
                modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
            )
        },
    )
}
