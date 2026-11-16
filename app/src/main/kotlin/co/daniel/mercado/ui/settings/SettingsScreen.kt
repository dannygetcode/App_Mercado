package co.daniel.mercado.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.daniel.mercado.BuildConfig
import co.daniel.mercado.R
import co.daniel.mercado.data.seed.SeedError
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.domain.model.MonthStatus
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.TrafficLight
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.components.Dates
import co.daniel.mercado.ui.components.KeypadBottomSheet
import co.daniel.mercado.ui.components.ScreenTitle
import co.daniel.mercado.ui.theme.MercadoTheme
import java.time.YearMonth

/** Ajustes (04-ux): Presupuesto, Mercado, Escáner, Seguridad, Datos y Acerca de. */
@Composable
fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pickCatalog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onEvent(SettingsEvent.ImportCatalog { context.contentResolver.openInputStream(uri) })
    }
    val s = state.settings
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            ScreenTitle(stringResource(R.string.nav_settings))

            Section(R.string.settings_budget)
            val month = state.month
            ListItem(
                modifier = Modifier.clickable(onClick = { onEvent(SettingsEvent.EditBudget) }),
                headlineContent = {
                    Text(stringResource(R.string.settings_budget_month, month?.let { Dates.monthName(it.yearMonth) }.orEmpty()))
                },
                supportingContent = {
                    val budget = month?.budget
                    Text(
                        when {
                            budget == null -> stringResource(R.string.settings_budget_none)
                            month.inherited -> stringResource(R.string.settings_budget_inherited, MoneyFormat.format(budget))
                            else -> MoneyFormat.format(budget)
                        },
                    )
                },
            )
            DisabledRow(R.string.settings_category_caps, R.string.settings_coming_l7)

            Section(R.string.settings_market)
            FrequencyRow(s.shoppingFrequencyDays, onChange = { onEvent(SettingsEvent.FrequencyDays(it)) })
            SwitchRow(R.string.settings_reminders, R.string.settings_reminders_hint, s.remindersEnabled) {
                onEvent(SettingsEvent.Reminders(it))
            }

            Section(R.string.settings_scanner)
            SwitchRow(R.string.settings_scan_sound, null, s.scanSound) { onEvent(SettingsEvent.ScanSound(it)) }
            SwitchRow(R.string.settings_scan_vibration, null, s.scanVibration) { onEvent(SettingsEvent.ScanVibration(it)) }

            Section(R.string.settings_security)
            SwitchRow(R.string.settings_lock, R.string.settings_coming_l6, s.lockEnabled, enabled = false) {}
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_grace)) },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsStore.GRACE_OPTIONS.sorted().forEach { minutes ->
                            FilterChip(
                                selected = s.lockGraceMinutes == minutes,
                                onClick = {},
                                enabled = false,
                                label = { Text(stringResource(R.string.settings_grace_minutes, minutes)) },
                            )
                        }
                    }
                },
            )
            SwitchRow(R.string.settings_secure_screen, R.string.settings_coming_l6, s.secureScreen, enabled = false) {}

            Section(R.string.settings_data)
            DisabledRow(R.string.settings_export_backup, R.string.settings_coming_l6)
            DisabledRow(R.string.settings_restore_backup, R.string.settings_coming_l6)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_last_backup)) },
                supportingContent = {
                    Text(if (s.lastBackupAt > 0) Dates.dayMonthYear(s.lastBackupAt) else stringResource(R.string.settings_never))
                },
            )
            ListItem(
                modifier = Modifier.clickable(enabled = !state.importing) {
                    pickCatalog.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
                headlineContent = { Text(stringResource(R.string.settings_import_catalog)) },
                supportingContent = { Text(stringResource(R.string.settings_import_catalog_hint)) },
                trailingContent = if (state.importing) {
                    { CircularProgressIndicator(Modifier.size(24.dp)) }
                } else {
                    null
                },
            )
            state.importMessage?.let { ImportResult(it, onDismiss = { onEvent(SettingsEvent.DismissImportMessage) }) }

            Section(R.string.settings_about)
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
            )
            ListItem(headlineContent = { Text(stringResource(R.string.no_internet_note)) })
        }
    }
    if (state.budgetSheet) {
        KeypadBottomSheet(
            title = stringResource(R.string.budget_title, state.month?.let { Dates.monthName(it.yearMonth) }.orEmpty()),
            actionLabel = stringResource(R.string.save),
            initial = state.month?.budget,
            hint = stringResource(R.string.budget_hint),
            onSubmit = { onEvent(SettingsEvent.SaveBudget(it)) },
            onDismiss = { onEvent(SettingsEvent.DismissSheet) },
        )
    }
}

@Composable
private fun Section(title: Int) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SwitchRow(title: Int, hint: Int?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = hint?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

@Composable
private fun DisabledRow(title: Int, hint: Int) {
    ListItem(
        headlineContent = { Text(stringResource(title), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
        supportingContent = { Text(stringResource(hint)) },
    )
}

@Composable
private fun FrequencyRow(days: Int, onChange: (Int) -> Unit) {
    val label = pluralStringResource(R.plurals.settings_frequency_days, days, days)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_frequency)) },
        supportingContent = { Text(label) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val less = stringResource(R.string.settings_frequency_less)
                val more = stringResource(R.string.settings_frequency_more)
                OutlinedButton(
                    onClick = { onChange(days - 1) },
                    enabled = days > SettingsStore.FREQUENCY_RANGE.first,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = less },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("−") }
                OutlinedButton(
                    onClick = { onChange(days + 1) },
                    enabled = days < SettingsStore.FREQUENCY_RANGE.last,
                    modifier = Modifier.padding(start = 8.dp).size(48.dp).semantics { contentDescription = more },
                    contentPadding = PaddingValues(0.dp),
                ) { Text("+") }
            }
        },
    )
}

@Composable
private fun ImportResult(message: ImportMessage, onDismiss: () -> Unit) {
    val text = when (message) {
        is ImportMessage.Imported -> stringResource(R.string.import_ok, message.created, message.updated)
        ImportMessage.Unreadable -> stringResource(R.string.import_unreadable)
        is ImportMessage.Invalid -> {
            val position = (message.index ?: 0) + 1
            when (message.error) {
                SeedError.TOO_LARGE -> stringResource(R.string.import_too_large)
                SeedError.MALFORMED -> stringResource(R.string.import_malformed)
                SeedError.UNSUPPORTED_VERSION -> stringResource(R.string.import_version)
                SeedError.TOO_MANY_PRODUCTS -> stringResource(R.string.import_too_many)
                SeedError.INVALID_NAME -> stringResource(R.string.import_invalid_name, position)
                SeedError.INVALID_CATEGORY -> stringResource(R.string.import_invalid_category, position)
                SeedError.INVALID_UNIT -> stringResource(R.string.import_invalid_unit, position)
                SeedError.INVALID_PRICE -> stringResource(R.string.import_invalid_price, position)
                SeedError.INVALID_BARCODE -> stringResource(R.string.import_invalid_barcode, position)
                SeedError.DUPLICATE_BARCODE -> stringResource(R.string.import_duplicate_barcode, position)
            } + " " + stringResource(R.string.import_nothing_written)
        }
    }
    val isError = message !is ImportMessage.Imported
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.keypad_ok)) }
    }
}

@Preview(heightDp = 1600)
@Composable
private fun SettingsPreview() {
    MercadoTheme {
        SettingsScreen(
            SettingsUiState(
                month = MonthStatus(YearMonth.of(2026, 11), Pesos(900_000), Pesos(0), Pesos(900_000), TrafficLight.GREEN, inherited = true),
                importMessage = ImportMessage.Invalid(SeedError.INVALID_PRICE, 4),
            ),
            {},
        )
    }
}
