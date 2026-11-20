package com.aliasadi.tamdid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.Money
import com.aliasadi.tamdid.ui.LocalAppFormat
import com.aliasadi.tamdid.ui.components.ListRow
import com.aliasadi.tamdid.ui.components.MonthGrid
import com.aliasadi.tamdid.ui.components.MonthHeader
import com.aliasadi.tamdid.ui.components.SectionCard
import com.aliasadi.tamdid.ui.components.ServiceAvatar
import com.aliasadi.tamdid.ui.cycleText
import com.aliasadi.tamdid.ui.theme.TamdidColors

@Composable
fun CalendarScreen(viewModel: CalendarViewModel, contentPadding: PaddingValues, onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val format = LocalAppFormat.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard {
            Column(Modifier.padding(8.dp)) {
                MonthHeader(
                    state.month,
                    onPrevious = { viewModel.showMonth(state.month.plusMonths(-1)) },
                    onNext = { viewModel.showMonth(state.month.plusMonths(1)) },
                )
                MonthGrid(
                    month = state.month,
                    selected = state.selected,
                    onSelect = viewModel::select,
                    markers = { date -> state.charges[date].orEmpty().map { TamdidColors.parse(it.colorHex) } },
                )
                Text(
                    stringResource(R.string.calendar_month_total, format.money(state.monthTotal.primary)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        SectionCard(title = format.date(state.selected)) {
            val charges = state.selectedCharges
            if (charges.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_no_charges),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            for (sub in charges) {
                ListRow(
                    onClick = { onOpen(sub.id) },
                    leading = { ServiceAvatar(sub.name, sub.colorHex) },
                    title = sub.name,
                    subtitle = cycleText(sub.cycle),
                    trailing = { Text(format.money(Money(sub.priceOn(state.selected), sub.price.currency)), style = MaterialTheme.typography.titleSmall) },
                )
            }
        }
    }
}
