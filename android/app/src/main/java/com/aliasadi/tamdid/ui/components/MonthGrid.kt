package com.aliasadi.tamdid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.CalendarMonth
import com.aliasadi.tamdid.core.CalendarSystem
import com.aliasadi.tamdid.core.Jalali
import com.aliasadi.tamdid.core.DateText
import com.aliasadi.tamdid.core.Digits
import com.aliasadi.tamdid.ui.LocalAppFormat
import java.time.LocalDate

/** "‹ Aban 1405 ›" header used by the calendar screen and the date picker. */
@Composable
fun MonthHeader(month: CalendarMonth, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val format = LocalAppFormat.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_previous_month))
        }
        Text(
            DateText.format(month, format.language),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next_month))
        }
    }
}

/**
 * A month of days in either calendar. Works the same for Jalali (weeks start on Saturday)
 * and Gregorian (Monday). Right-to-left layout is handled by Compose.
 */
@Composable
fun MonthGrid(
    month: CalendarMonth,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    markers: (LocalDate) -> List<Color> = { emptyList() },
) {
    val format = LocalAppFormat.current
    val firstWeekday = DateText.defaultFirstWeekday(month.system, format.language)
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            for (symbol in DateText.weekdaySymbols(firstWeekday, format.language)) {
                Text(
                    symbol,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        for (week in month.weeks(firstWeekday)) {
            Row(Modifier.fillMaxWidth()) {
                for (date in week) {
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, month, date == selected, date == format.today, markers(date), onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    month: CalendarMonth,
    isSelected: Boolean,
    isToday: Boolean,
    markers: List<Color>,
    onSelect: (LocalDate) -> Unit,
) {
    val format = LocalAppFormat.current
    val scheme = MaterialTheme.colorScheme
    val dayNumber = if (month.system == CalendarSystem.Gregorian) date.dayOfMonth else Jalali.fromGregorian(date).day
    val background = if (isSelected) scheme.primary else Color.Transparent
    val textColor = if (isSelected) scheme.onPrimary else scheme.onSurface
    Column(
        Modifier
            .clip(CircleShape)
            .background(background)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, scheme.primary, CircleShape) else Modifier)
            .clickable { onSelect(date) }
            .semantics {
                contentDescription = format.date(date)
                selected = isSelected
            }
            .padding(4.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            Digits.localize(dayNumber.toString(), format.language),
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
            for (color in markers.take(3)) ColorDot(if (isSelected) scheme.onPrimary else color, size = 5.dp)
        }
    }
}

/** A date picker that speaks the user's calendar, which Material's picker cannot do for Jalali. */
@Composable
fun CalendarDatePickerDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val format = LocalAppFormat.current
    var selected by remember { mutableStateOf(initial) }
    var month by remember { mutableStateOf(CalendarMonth.containing(initial, format.calendar)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(format.date(selected)) },
        text = {
            Column {
                MonthHeader(month, onPrevious = { month = month.plusMonths(-1) }, onNext = { month = month.plusMonths(1) })
                MonthGrid(month, selected, onSelect = { selected = it })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
