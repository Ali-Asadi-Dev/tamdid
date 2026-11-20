package com.aliasadi.tamdid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.DateText
import com.aliasadi.tamdid.core.Money
import com.aliasadi.tamdid.core.UpcomingCharge
import com.aliasadi.tamdid.ui.LocalAppFormat
import com.aliasadi.tamdid.ui.components.ColorDot
import com.aliasadi.tamdid.ui.components.EmptyState
import com.aliasadi.tamdid.ui.components.ListRow
import com.aliasadi.tamdid.ui.components.SectionCard
import com.aliasadi.tamdid.ui.components.SegmentedBar
import com.aliasadi.tamdid.ui.components.ServiceAvatar
import com.aliasadi.tamdid.ui.theme.TamdidColors

@Composable
fun HomeScreen(viewModel: HomeViewModel, contentPadding: PaddingValues, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.loading) return
    if (state.subscriptionCount == 0) {
        EmptyState(
            icon = Icons.Rounded.Autorenew,
            title = stringResource(R.string.empty_title),
            body = stringResource(R.string.empty_body),
            modifier = Modifier.padding(contentPadding),
        ) {
            Button(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.action_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { TotalCard(state) }
        if (state.endingTrials.isNotEmpty()) {
            item { ChargeList(stringResource(R.string.home_trials_ending), state.endingTrials, onOpen, trial = true) }
        }
        item {
            if (state.upcoming.isEmpty()) {
                SectionCard(title = stringResource(R.string.home_next_7_days)) {
                    Text(
                        stringResource(R.string.home_nothing_soon),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                ChargeList(stringResource(R.string.home_next_7_days), state.upcoming, onOpen, trial = false)
            }
        }
        if (state.categories.isNotEmpty()) item { CategoryCard(state) }
    }
}

@Composable
private fun TotalCard(state: HomeUiState) {
    val format = LocalAppFormat.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.home_monthly_total), style = MaterialTheme.typography.labelLarge)
            Text(
                format.money(state.monthly.primary),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.home_yearly, format.money(state.yearly)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.home_daily, format.money(state.daily)), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                com.aliasadi.tamdid.core.Digits.localize(pluralStringResource(R.plurals.home_active_count, state.activeCount, state.activeCount), format.language),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!state.monthly.isComplete) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_unconverted, format.unconverted(state.monthly)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ChargeList(title: String, charges: List<UpcomingCharge>, onOpen: (String) -> Unit, trial: Boolean) {
    val format = LocalAppFormat.current
    SectionCard(title = title) {
        charges.forEachIndexed { index, charge ->
            val sub = charge.subscription
            ListRow(
                onClick = { onOpen(sub.id) },
                leading = { ServiceAvatar(sub.name, sub.colorHex) },
                title = sub.name,
                subtitle = "${format.relative(charge.date)} · ${format.date(charge.date, DateText.Style.DayMonth)}",
                trailing = {
                    Text(
                        format.money(Money(sub.priceOn(charge.date), sub.price.currency)),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (trial) TamdidColors.Warning else MaterialTheme.colorScheme.onSurface,
                    )
                },
            )
            if (index < charges.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun CategoryCard(state: HomeUiState) {
    val format = LocalAppFormat.current
    val colors = TamdidColors.Palette.map(TamdidColors::parse)
    SectionCard(title = stringResource(R.string.home_by_category)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedBar(state.categories.mapIndexed { i, c -> c.monthlyMinor.toFloat() to colors[i % colors.size] })
            state.categories.forEachIndexed { i, c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorDot(colors[i % colors.size], size = 10.dp)
                    Text(c.category.displayName(format.language), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(format.money(Money(c.monthlyMinor, state.monthly.primary.currency)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
