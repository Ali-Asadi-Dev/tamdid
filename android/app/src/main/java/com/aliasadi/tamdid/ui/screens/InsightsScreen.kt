package com.aliasadi.tamdid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.DateText
import com.aliasadi.tamdid.core.Digits
import com.aliasadi.tamdid.core.Money
import com.aliasadi.tamdid.ui.LocalAppFormat
import com.aliasadi.tamdid.ui.components.EmptyState
import com.aliasadi.tamdid.ui.components.KeyValueRow
import com.aliasadi.tamdid.ui.components.ListRow
import com.aliasadi.tamdid.ui.components.SectionCard
import com.aliasadi.tamdid.ui.components.ServiceAvatar
import com.aliasadi.tamdid.ui.theme.TamdidColors

@Composable
fun InsightsScreen(viewModel: InsightsViewModel, contentPadding: PaddingValues, onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val format = LocalAppFormat.current
    if (state.loading) return
    if (!state.hasData) {
        EmptyState(Icons.Rounded.Insights, stringResource(R.string.tab_insights), stringResource(R.string.insights_empty), Modifier.padding(contentPadding))
        return
    }
    val currency = state.monthly.primary.currency
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionCard {
            KeyValueRow(stringResource(R.string.home_monthly_total), format.money(state.monthly.primary))
            KeyValueRow(stringResource(R.string.insights_per_year), format.money(state.yearly))
            KeyValueRow(stringResource(R.string.insights_per_day), format.money(state.daily))
        }

        for (overlap in state.overlaps) {
            val total = overlap.subscriptions.sumOf { it.monthlyCostMinor }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            Digits.localize(
                                pluralStringResource(R.plurals.insights_overlap_title, overlap.subscriptions.size, overlap.subscriptions.size, overlap.category.displayName(format.language)),
                                format.language,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            overlap.subscriptions.joinToString(" · ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (overlap.subscriptions.all { it.price.currency == overlap.subscriptions.first().price.currency }) {
                            Text(
                                stringResource(R.string.insights_overlap_body, format.money(Money(total, overlap.subscriptions.first().price.currency))),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.insights_last_12_months)) {
            MonthlyBars(state)
        }

        SectionCard(title = stringResource(R.string.home_by_category)) {
            val max = state.categories.maxOfOrNull { it.monthlyMinor }?.takeIf { it > 0 } ?: 1
            val colors = TamdidColors.Palette.map(TamdidColors::parse)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.categories.forEachIndexed { i, c ->
                    Column {
                        Row {
                            Text(c.category.displayName(format.language), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(format.money(Money(c.monthlyMinor, currency)), style = MaterialTheme.typography.bodyMedium)
                        }
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(c.monthlyMinor.toFloat() / max)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors[i % colors.size]),
                        )
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.insights_most_expensive)) {
            for (ranked in state.mostExpensive) {
                val sub = ranked.subscription
                ListRow(
                    onClick = { onOpen(sub.id) },
                    leading = { ServiceAvatar(sub.name, sub.colorHex) },
                    title = sub.name,
                    subtitle = stringResource(R.string.detail_monthly_equivalent, format.money(Money(ranked.monthlyMinor, currency))),
                    trailing = { Text(format.money(sub.price), style = MaterialTheme.typography.titleSmall) },
                )
            }
        }
    }
}

@Composable
private fun MonthlyBars(state: InsightsUiState) {
    val format = LocalAppFormat.current
    val max = state.months.maxOfOrNull { it.total.primary.amountMinor }?.takeIf { it > 0 } ?: 1
    val current = state.months.lastOrNull()
    Column(Modifier.padding(16.dp)) {
        current?.let {
            Text(format.money(it.total.primary), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                DateText.monthName(it.month, state.calendar, format.language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (month in state.months) {
                val fraction = (month.total.primary.amountMinor.toFloat() / max).coerceIn(0.02f, 1f)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction * 0.85f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (month == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            .semantics { contentDescription = "${DateText.monthName(month.month, state.calendar, format.language)}: ${format.money(month.total.primary)}" },
                    )
                    Text(
                        // Jalali month names are long and share prefixes, so label bars by month number.
                        if (state.calendar == com.aliasadi.tamdid.core.CalendarSystem.Jalali || format.language.isRightToLeft) format.number(month.month)
                        else DateText.monthName(month.month, state.calendar, format.language).take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
