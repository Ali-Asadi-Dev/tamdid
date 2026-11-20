package com.aliasadi.tamdid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.BillingCycle
import com.aliasadi.tamdid.core.CurrencyCode
import com.aliasadi.tamdid.core.CycleUnit
import com.aliasadi.tamdid.core.Digits
import com.aliasadi.tamdid.core.SubscriptionCategory
import com.aliasadi.tamdid.ui.LocalAppFormat
import com.aliasadi.tamdid.ui.components.CalendarDatePickerDialog
import com.aliasadi.tamdid.ui.components.SelectField
import com.aliasadi.tamdid.ui.reminderText
import com.aliasadi.tamdid.ui.theme.TamdidColors
import com.aliasadi.tamdid.ui.unitText
import java.time.LocalDate

val ReminderOptions: List<Int?> = listOf(null, 0, 1, 2, 3, 7)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditScreen(viewModel: EditViewModel, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val format = LocalAppFormat.current
    val errors = if (viewModel.showErrors) viewModel.errors else emptySet()
    var pickingDate by remember { mutableStateOf<DateField?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isNew) R.string.edit_new_title else R.string.edit_title)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel)) } },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved) }, modifier = Modifier.testTag("save")) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Text(stringResource(R.string.action_save), modifier = Modifier.padding(start = 4.dp))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text(stringResource(R.string.edit_name)) },
                isError = FormError.Name in errors,
                supportingText = if (FormError.Name in errors) ({ Text(stringResource(R.string.edit_error_name)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("name"),
            )

            val monthlyPreview = viewModel.monthlyPreview?.takeIf { viewModel.cycle != BillingCycle.Monthly }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = viewModel.priceText,
                    onValueChange = { viewModel.priceText = it },
                    label = { Text(stringResource(R.string.edit_price)) },
                    isError = FormError.Price in errors,
                    supportingText = when {
                        FormError.Price in errors -> {
                            { Text(stringResource(R.string.edit_error_price)) }
                        }
                        monthlyPreview != null -> {
                            { Text(stringResource(R.string.detail_monthly_equivalent, format.money(monthlyPreview))) }
                        }
                        else -> null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1.4f).testTag("price"),
                )
                SelectField(
                    label = stringResource(R.string.edit_currency),
                    selected = viewModel.currency,
                    options = CurrencyCode.entries,
                    optionLabel = { it.code },
                    onSelect = { viewModel.currency = it },
                    modifier = Modifier.weight(1f),
                )
            }
            if (viewModel.priceWillChange) {
                Text(
                    stringResource(R.string.edit_price_change_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(stringResource(R.string.edit_cycle), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SelectField(
                    label = stringResource(R.string.edit_every),
                    selected = viewModel.interval,
                    options = (1..12).toList(),
                    optionLabel = { Digits.localize(it.toString(), format.language) },
                    onSelect = { viewModel.interval = it },
                    modifier = Modifier.weight(1f),
                )
                SelectField(
                    label = " ",
                    selected = viewModel.cycleUnit,
                    options = CycleUnit.entries,
                    optionLabel = { unitText(it, viewModel.interval) },
                    onSelect = { viewModel.cycleUnit = it },
                    modifier = Modifier.weight(1.4f),
                )
            }

            DateRow(stringResource(R.string.edit_first_payment), viewModel.startDate) { pickingDate = DateField.Start }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.edit_has_trial), modifier = Modifier.weight(1f))
                Switch(checked = viewModel.hasTrial, onCheckedChange = { viewModel.hasTrial = it })
            }
            if (viewModel.hasTrial) {
                DateRow(stringResource(R.string.edit_trial_end), viewModel.trialEnd) { pickingDate = DateField.Trial }
                if (FormError.Trial in errors) {
                    Text(stringResource(R.string.edit_error_trial), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            SelectField(
                label = stringResource(R.string.edit_reminder),
                selected = viewModel.reminderDays,
                options = ReminderOptions,
                optionLabel = { reminderText(it) },
                onSelect = { viewModel.reminderDays = it },
                modifier = Modifier.fillMaxWidth(),
            )

            SelectField(
                label = stringResource(R.string.edit_category),
                selected = viewModel.category,
                options = SubscriptionCategory.entries,
                optionLabel = { it.displayName(format.language) },
                onSelect = { viewModel.category = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.edit_color), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (hex in TamdidColors.Palette) {
                    val isSelected = hex.equals(viewModel.colorHex, ignoreCase = true)
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(TamdidColors.parse(hex))
                            .then(if (isSelected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), CircleShape) else Modifier)
                            .clickable(role = Role.RadioButton) { viewModel.colorHex = hex }
                            .semantics { contentDescription = hex; selected = isSelected },
                    )
                }
            }

            OutlinedTextField(
                value = viewModel.paymentMethod,
                onValueChange = { viewModel.paymentMethod = it },
                label = { Text(stringResource(R.string.edit_payment_method)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.url,
                onValueChange = { viewModel.url = it },
                label = { Text(stringResource(R.string.edit_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it },
                label = { Text(stringResource(R.string.edit_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    pickingDate?.let { field ->
        CalendarDatePickerDialog(
            initial = if (field == DateField.Start) viewModel.startDate else viewModel.trialEnd,
            onDismiss = { pickingDate = null },
            onConfirm = { date ->
                if (field == DateField.Start) viewModel.startDate = date else viewModel.trialEnd = date
                pickingDate = null
            },
        )
    }
}

private enum class DateField { Start, Trial }

@Composable
private fun DateRow(label: String, date: LocalDate, onClick: () -> Unit) {
    val format = LocalAppFormat.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(format.date(date), color = Color.Unspecified, style = MaterialTheme.typography.bodyLarge)
    }
}
