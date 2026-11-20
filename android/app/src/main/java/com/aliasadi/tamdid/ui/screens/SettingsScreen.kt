package com.aliasadi.tamdid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliasadi.tamdid.BuildConfig
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.AppLanguage
import com.aliasadi.tamdid.core.CalendarSystem
import com.aliasadi.tamdid.core.CurrencyCode
import com.aliasadi.tamdid.core.Digits
import com.aliasadi.tamdid.core.ThemePreference
import com.aliasadi.tamdid.ui.LocalAppFormat
import com.aliasadi.tamdid.ui.components.ListRow
import com.aliasadi.tamdid.ui.components.SectionCard
import com.aliasadi.tamdid.ui.components.SelectField
import com.aliasadi.tamdid.ui.reminderText
import android.widget.Toast

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, contentPadding: PaddingValues) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val settings = data?.settings ?: return
    val format = LocalAppFormat.current
    val context = LocalContext.current
    val savedMessage = stringResource(R.string.settings_export_done)

    var pendingExport by remember { mutableStateOf<String?>(null) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && text != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
                .onSuccess { Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show() }
        }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            viewModel.prepareImport(text ?: "")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionCard(title = stringResource(R.string.settings_general)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectField(
                    label = stringResource(R.string.setup_language),
                    selected = format.language,
                    options = AppLanguage.entries,
                    optionLabel = { it.nativeName },
                    onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it.code)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectField(
                    label = stringResource(R.string.setup_currency),
                    selected = settings.primaryCurrency,
                    options = CurrencyCode.entries,
                    optionLabel = { "${it.displayName(format.language)} (${it.code})" },
                    onSelect = { currency -> viewModel.update { it.copy(primaryCurrency = currency) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectField(
                    label = stringResource(R.string.setup_calendar),
                    selected = settings.calendar,
                    options = CalendarSystem.entries,
                    optionLabel = { stringResource(if (it == CalendarSystem.Jalali) R.string.calendar_jalali else R.string.calendar_gregorian) },
                    onSelect = { calendar -> viewModel.update { it.copy(calendar = calendar) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectField(
                    label = stringResource(R.string.settings_default_reminder),
                    selected = settings.defaultReminderDays,
                    options = ReminderOptions,
                    optionLabel = { reminderText(it) },
                    onSelect = { days -> viewModel.update { it.copy(defaultReminderDays = days) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectField(
                    label = stringResource(R.string.settings_theme),
                    selected = settings.theme,
                    options = ThemePreference.entries,
                    optionLabel = {
                        stringResource(
                            when (it) {
                                ThemePreference.System -> R.string.theme_system
                                ThemePreference.Light -> R.string.theme_light
                                ThemePreference.Dark -> R.string.theme_dark
                            },
                        )
                    },
                    onSelect = { theme -> viewModel.update { it.copy(theme = theme) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Rates are only needed for currencies the user actually pays in.
        val otherCurrencies = data?.subscriptions.orEmpty().map { it.price.currency }.toSet() - settings.primaryCurrency
        if (otherCurrencies.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.settings_exchange_rates)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (currency in otherCurrencies.sortedBy { it.ordinal }) {
                        RateField(
                            label = Digits.localize(
                                stringResource(R.string.settings_exchange_rates_hint, currency.code, settings.primaryCurrency.code),
                                format.language,
                            ),
                            value = settings.exchangeRates[currency],
                            onChange = { viewModel.setExchangeRate(currency, it) },
                        )
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_data)) {
            ListRow(
                onClick = {
                    pendingExport = viewModel.exportJson()
                    createDocument.launch("tamdid-backup-${format.today}.json")
                },
                leading = { Icon(Icons.Rounded.Download, contentDescription = null) },
                title = stringResource(R.string.settings_export_json),
            )
            ListRow(
                onClick = {
                    pendingExport = viewModel.exportCsv()
                    createDocument.launch("tamdid-${format.today}.csv")
                },
                leading = { Icon(Icons.Rounded.TableChart, contentDescription = null) },
                title = stringResource(R.string.settings_export_csv),
            )
            ListRow(
                onClick = { openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                leading = { Icon(Icons.Rounded.Upload, contentDescription = null) },
                title = stringResource(R.string.settings_import),
            )
        }

        SectionCard(title = stringResource(R.string.settings_about)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        Digits.localize(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), format.language),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.settings_source), style = MaterialTheme.typography.bodyMedium)
                Text("github.com/Ali-Asadi-Dev/tamdid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    when (val state = importState) {
        is ImportState.Confirm -> AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(Digits.localize(pluralStringResource(R.plurals.settings_import_confirm_body, state.replacing, state.replacing), format.language)) },
            confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text(stringResource(R.string.settings_import)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissImport) { Text(stringResource(R.string.action_cancel)) } },
        )
        ImportState.Done, ImportState.Unreadable, ImportState.TooNew -> AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            text = {
                Text(
                    stringResource(
                        when (state) {
                            ImportState.Done -> R.string.settings_import_done
                            ImportState.TooNew -> R.string.settings_import_too_new
                            else -> R.string.settings_import_failed
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissImport) { Text(stringResource(R.string.action_done)) } },
        )
        ImportState.Idle -> Unit
    }
}

@Composable
private fun RateField(label: String, value: Double?, onChange: (Double?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            val normalized = Digits.latinize(input).replace(",", "").replace("٬", "").replace("٫", ".")
            if (normalized.isBlank()) onChange(null) else normalized.toDoubleOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
