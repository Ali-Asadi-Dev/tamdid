package com.aliasadi.tamdid.ui.screens

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.Money
import com.aliasadi.tamdid.core.SubscriptionStatus
import com.aliasadi.tamdid.ui.LocalAppFormat
import com.aliasadi.tamdid.ui.components.KeyValueRow
import com.aliasadi.tamdid.ui.components.SectionCard
import com.aliasadi.tamdid.ui.components.ServiceAvatar
import com.aliasadi.tamdid.ui.components.StatusBadge
import com.aliasadi.tamdid.ui.cycleText
import com.aliasadi.tamdid.ui.reminderText
import com.aliasadi.tamdid.ui.theme.TamdidColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: DetailViewModel, onBack: () -> Unit, onEdit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val format = LocalAppFormat.current
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val sub = state.subscription

    LaunchedEffect(state.loading, sub) {
        if (!state.loading && sub == null) onBack() // deleted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = sub != null) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
                    IconButton(onClick = { confirmDelete = true }, enabled = sub != null) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        if (sub == null) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ServiceAvatar(sub.name, sub.colorHex, size = 64.dp)
                Column(Modifier.weight(1f)) {
                    Text(sub.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(sub.category.displayName(format.language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    when {
                        sub.status == SubscriptionStatus.Paused -> StatusBadge(stringResource(R.string.status_paused), TamdidColors.Warning)
                        sub.status == SubscriptionStatus.Cancelled -> StatusBadge(stringResource(R.string.status_cancelled), MaterialTheme.colorScheme.onSurfaceVariant)
                        state.isInTrial -> StatusBadge(stringResource(R.string.status_trial), TamdidColors.Positive)
                        else -> StatusBadge(stringResource(R.string.status_active), MaterialTheme.colorScheme.primary)
                    }
                }
            }

            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    Text(format.money(sub.price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(cycleText(sub.cycle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.detail_monthly_equivalent, format.money(Money(sub.monthlyCostMinor, sub.price.currency))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                KeyValueRow(
                    stringResource(R.string.detail_next_renewal),
                    state.nextRenewal?.let { "${format.date(it)} (${format.relative(it)})" } ?: stringResource(R.string.detail_no_renewal),
                )
                sub.trialEndDate?.let { KeyValueRow(stringResource(R.string.detail_trial_ends), format.date(it)) }
                KeyValueRow(stringResource(R.string.detail_reminder), reminderText(sub.reminderDaysBefore))
            }

            SectionCard {
                state.totalSpent?.let { KeyValueRow(stringResource(R.string.detail_total_spent), format.money(it)) }
                KeyValueRow(stringResource(R.string.detail_payments), format.number(state.paymentCount))
                KeyValueRow(stringResource(R.string.detail_started), format.date(sub.startDate))
                sub.endDate?.let { KeyValueRow(stringResource(R.string.detail_ended), format.date(it)) }
                sub.paymentMethod?.let { KeyValueRow(stringResource(R.string.detail_payment_method), it) }
            }

            if (sub.priceHistory.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.detail_price_history)) {
                    val current = sub.currentPriceSince
                    if (current != null) {
                        KeyValueRow(stringResource(R.string.detail_since, format.date(current)), format.money(sub.price))
                    }
                    for (change in sub.priceHistory.asReversed()) {
                        KeyValueRow(
                            stringResource(R.string.detail_since, format.date(change.effectiveFrom)),
                            format.money(Money(change.amountMinor, sub.price.currency)),
                        )
                    }
                }
            }

            sub.notes?.let {
                SectionCard(title = stringResource(R.string.detail_notes)) {
                    Text(it, modifier = Modifier.padding(16.dp))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sub.url?.let { url ->
                    OutlinedButton(
                        onClick = {
                            val uri = (if (url.startsWith("http")) url else "https://$url").toUri()
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                        Text(stringResource(R.string.action_open_link), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                when (sub.status) {
                    SubscriptionStatus.Active -> {
                        OutlinedButton(onClick = viewModel::pause, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_pause)) }
                        OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_cancel_subscription))
                        }
                    }
                    SubscriptionStatus.Paused, SubscriptionStatus.Cancelled ->
                        OutlinedButton(onClick = viewModel::resume, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_resume)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && sub != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, sub.name)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
