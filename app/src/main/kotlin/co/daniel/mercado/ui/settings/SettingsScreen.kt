package co.daniel.mercado.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.ColumnScope
import co.daniel.mercado.ui.components.MercadoTextField
import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.LifecycleResumeEffect
import co.daniel.mercado.reminders.Reminders
import co.daniel.mercado.security.BackupError
import co.daniel.mercado.security.isDeviceSecure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem as M3ListItem
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
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<SettingsEffect> = emptyFlow(),
    onOpenCategoryCaps: () -> Unit = {},
    onOpenCleanup: () -> Unit = {},
) {
    val context = LocalContext.current
    val pickCatalog = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onEvent(SettingsEvent.ImportCatalog { context.contentResolver.openInputStream(uri) })
    }
    // El selector de documentos del sistema: la app no pide permisos de almacenamiento (03-datos).
    val pickExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        if (uri != null) {
            onEvent(SettingsEvent.ExportTo { context.contentResolver.openOutputStream(uri, "wt") })
        } else {
            onEvent(SettingsEvent.DismissBackupDialog)
        }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onEvent(SettingsEvent.RestoreFrom { context.contentResolver.openInputStream(uri) })
    }
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is SettingsEffect.PickExportFile -> pickExport.launch(effect.suggestedName)
                SettingsEffect.PickRestoreFile -> pickRestore.launch(arrayOf("*/*"))
            }
        }
    }
    var deviceSecure by remember { mutableStateOf(context.isDeviceSecure()) }
    var canNotify by remember { mutableStateOf(Reminders.canNotify(context)) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted || Reminders.canNotify(context)
    }
    LifecycleResumeEffect(Unit) {
        deviceSecure = context.isDeviceSecure()
        canNotify = Reminders.canNotify(context)
        onPauseOrDispose { }
    }
    val s = state.settings
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            ScreenTitle(stringResource(R.string.nav_settings))

            Group(R.string.settings_budget) {
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
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenCategoryCaps),
                    headlineContent = { Text(stringResource(R.string.settings_category_caps)) },
                    supportingContent = { Text(stringResource(R.string.caps_hint)) },
                )
            }
            Group(R.string.settings_market) {
                FrequencyRow(s.shoppingFrequencyDays, onChange = { onEvent(SettingsEvent.FrequencyDays(it)) })
                SwitchRow(R.string.settings_reminders, R.string.settings_reminders_hint, s.remindersEnabled) { on ->
                    onEvent(SettingsEvent.Reminders(on))
                    // Permiso en contexto: al activar los recordatorios (Android 13+).
                    if (on && !canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (s.remindersEnabled && !canNotify) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.settings_notifications_off),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }) { Text(stringResource(R.string.settings_notifications_allow)) }
                    }
                }
            }
            Group(R.string.settings_scanner) {
                SwitchRow(R.string.settings_scan_sound, null, s.scanSound) { onEvent(SettingsEvent.ScanSound(it)) }
                SwitchRow(R.string.settings_scan_vibration, null, s.scanVibration) { onEvent(SettingsEvent.ScanVibration(it)) }
            }
            Group(R.string.settings_security) {
                if (!deviceSecure) {
                    Text(
                        text = stringResource(R.string.settings_no_device_lock),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                SwitchRow(R.string.settings_lock, R.string.settings_lock_hint, s.lockEnabled && deviceSecure, enabled = deviceSecure) {
                    onEvent(SettingsEvent.LockEnabled(it))
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_grace)) },
                    supportingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsStore.GRACE_OPTIONS.sorted().forEach { minutes ->
                                FilterChip(
                                    selected = s.lockGraceMinutes == minutes,
                                    onClick = { onEvent(SettingsEvent.GraceMinutes(minutes)) },
                                    enabled = s.lockEnabled && deviceSecure,
                                    label = { Text(stringResource(R.string.settings_grace_minutes, minutes)) },
                                )
                            }
                        }
                    },
                )
                SwitchRow(R.string.settings_secure_screen, R.string.settings_secure_screen_hint, s.secureScreen) {
                    onEvent(SettingsEvent.SecureScreen(it))
                }
            }
            Group(R.string.settings_data) {
                ListItem(
                    modifier = Modifier.clickable(enabled = !state.backupBusy) { onEvent(SettingsEvent.ExportBackup) },
                    headlineContent = { Text(stringResource(R.string.settings_export_backup)) },
                    supportingContent = { Text(stringResource(R.string.settings_export_backup_hint)) },
                    trailingContent = if (state.backupBusy) {
                        { CircularProgressIndicator(Modifier.size(24.dp)) }
                    } else {
                        null
                    },
                )
                ListItem(
                    modifier = Modifier.clickable(enabled = !state.backupBusy) { onEvent(SettingsEvent.RestoreBackup) },
                    headlineContent = { Text(stringResource(R.string.settings_restore_backup)) },
                    supportingContent = { Text(stringResource(R.string.settings_restore_backup_hint)) },
                )
                if (state.backupDialog == null) {
                    state.backupMessage?.let { BackupResult(it, onDismiss = { onEvent(SettingsEvent.DismissBackupMessage) }) }
                }
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
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenCleanup),
                    headlineContent = { Text(stringResource(R.string.cleanup_open), color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text(stringResource(R.string.cleanup_open_hint)) },
                )
            }
            Group(R.string.settings_about) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                )
                ListItem(headlineContent = { Text(stringResource(R.string.no_internet_note)) })
            }
        }
    }
    state.backupDialog?.let { BackupDialogs(it, state.backupMessage, state.backupBusy, onEvent) }
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
private fun Group(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
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
                SeedError.IS_BACKUP -> stringResource(R.string.import_is_backup)
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

@Composable
private fun backupMessageText(message: BackupMessage): String = when (message) {
    BackupMessage.Exported -> stringResource(R.string.backup_exported)
    BackupMessage.Restored -> stringResource(R.string.backup_restored)
    BackupMessage.PasswordTooShort -> stringResource(R.string.backup_password_short)
    BackupMessage.PasswordMismatch -> stringResource(R.string.backup_password_mismatch)
    BackupMessage.WriteFailed -> stringResource(R.string.backup_write_failed)
    BackupMessage.ReadFailed -> stringResource(R.string.import_unreadable)
    is BackupMessage.Failed -> when (message.error) {
        BackupError.TOO_LARGE -> stringResource(R.string.backup_too_large)
        BackupError.NOT_A_BACKUP -> stringResource(R.string.backup_not_a_backup)
        BackupError.WRONG_PASSWORD_OR_TAMPERED -> stringResource(R.string.backup_wrong_password)
        BackupError.NEWER_VERSION -> stringResource(R.string.backup_newer)
        BackupError.INVALID_CONTENT -> stringResource(R.string.backup_invalid)
    } + " " + stringResource(R.string.backup_nothing_changed)
}

@Composable
private fun BackupResult(message: BackupMessage, onDismiss: () -> Unit) {
    val ok = message == BackupMessage.Exported || message == BackupMessage.Restored
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = backupMessageText(message),
            modifier = Modifier.weight(1f),
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.keypad_ok)) }
    }
}

@Composable
private fun BackupDialogs(dialog: BackupDialog, message: BackupMessage?, busy: Boolean, onEvent: (SettingsEvent) -> Unit) {
    val dismiss = { onEvent(SettingsEvent.DismissBackupDialog) }
    when (dialog) {
        BackupDialog.ExportPassword -> {
            var password by remember { mutableStateOf("") }
            var repeat by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.backup_password_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.backup_password_hint))
                        PasswordField(password, { password = it }, R.string.backup_password)
                        PasswordField(repeat, { repeat = it }, R.string.backup_password_repeat)
                        message?.let { Text(backupMessageText(it), color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onEvent(SettingsEvent.ExportPassword(password, repeat)) }) {
                        Text(stringResource(R.string.backup_continue))
                    }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
        BackupDialog.RestorePassword -> {
            var password by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.backup_open_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PasswordField(password, { password = it }, R.string.backup_password)
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                        message?.let { Text(backupMessageText(it), color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onEvent(SettingsEvent.RestorePassword(password)) }, enabled = !busy) {
                        Text(stringResource(R.string.backup_open))
                    }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
        is BackupDialog.ConfirmRestore -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_confirm_text,
                        Dates.dayMonthYear(dialog.exportedAt),
                        pluralStringResource(R.plurals.backup_products, dialog.products, dialog.products),
                        pluralStringResource(R.plurals.backup_trips, dialog.trips, dialog.trips),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { onEvent(SettingsEvent.ConfirmRestore) }) {
                    Text(stringResource(R.string.backup_confirm_yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: Int) {
    MercadoTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(label),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val BACKUP_MIME = "application/octet-stream"

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

/** Fila de ajustes: transparente para que la tarjeta del grupo sea el fondo. */
@Composable
private fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    M3ListItem(
        headlineContent = headlineContent,
        modifier = modifier,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

