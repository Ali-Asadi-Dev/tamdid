package com.aliasadi.tamdid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.SubscriptionStatus
import com.aliasadi.tamdid.ui.LocalAppFormat
import com.aliasadi.tamdid.ui.components.EmptyState
import com.aliasadi.tamdid.ui.components.ListRow
import com.aliasadi.tamdid.ui.components.SectionCard
import com.aliasadi.tamdid.ui.components.ServiceAvatar
import com.aliasadi.tamdid.ui.components.StatusBadge
import com.aliasadi.tamdid.ui.cycleText
import com.aliasadi.tamdid.ui.theme.TamdidColors

@Composable
fun SubscriptionsScreen(viewModel: SubscriptionsViewModel, contentPadding: PaddingValues, onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val format = LocalAppFormat.current
    if (state.loading) return
    if (!state.hasAny) {
        EmptyState(
            Icons.Rounded.Subscriptions,
            stringResource(R.string.empty_title),
            stringResource(R.string.empty_body),
            Modifier.padding(contentPadding),
        )
        return
    }
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.list_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
            )
            SortMenu(state.sort, viewModel::setSort)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (filter in ListFilter.entries) {
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(stringResource(filter.label())) },
                )
            }
        }
        if (state.items.isEmpty()) {
            Text(
                stringResource(R.string.list_no_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp),
        ) {
            item {
                SectionCard {
                    state.items.forEachIndexed { index, item ->
                        val sub = item.subscription
                        val cycle = cycleText(sub.cycle)
                        val subtitle = item.nextRenewal?.let { "$cycle · ${format.relative(it)}" } ?: cycle
                        ListRow(
                            onClick = { onOpen(sub.id) },
                            leading = { ServiceAvatar(sub.name, sub.colorHex) },
                            title = sub.name,
                            subtitle = subtitle,
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(format.money(sub.price), style = MaterialTheme.typography.titleSmall)
                                    when {
                                        sub.status == SubscriptionStatus.Paused -> StatusBadge(stringResource(R.string.status_paused), TamdidColors.Warning)
                                        sub.status == SubscriptionStatus.Cancelled -> StatusBadge(stringResource(R.string.status_cancelled), MaterialTheme.colorScheme.onSurfaceVariant)
                                        item.isTrial -> StatusBadge(stringResource(R.string.status_trial), TamdidColors.Positive)
                                    }
                                }
                            },
                        )
                        if (index < state.items.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

private fun ListFilter.label(): Int = when (this) {
    ListFilter.All -> R.string.filter_all
    ListFilter.Active -> R.string.status_active
    ListFilter.Paused -> R.string.status_paused
    ListFilter.Cancelled -> R.string.status_cancelled
}

@Composable
private fun SortMenu(current: ListSort, onSelect: (ListSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.sort_by))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (sort in ListSort.entries) {
                val label = when (sort) {
                    ListSort.NextRenewal -> R.string.sort_next
                    ListSort.MonthlyCost -> R.string.sort_price
                    ListSort.Name -> R.string.sort_name
                }
                DropdownMenuItem(
                    text = { Text((if (sort == current) "✓ " else "") + stringResource(label)) },
                    onClick = { onSelect(sort); open = false },
                )
            }
        }
    }
}
