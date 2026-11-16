package co.daniel.mercado.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Ajustes no sensibles (D-11). Claves y valores por defecto de `03-datos.md`. */
data class Settings(
    val lockEnabled: Boolean = true,
    val lockGraceMinutes: Int = 5,
    val secureScreen: Boolean = true,
    val scanSound: Boolean = true,
    val scanVibration: Boolean = true,
    val shoppingFrequencyDays: Int = 15,
    val remindersEnabled: Boolean = true,
    val lastReminderCycle: Long = 0,
    val lastBackupAt: Long = 0,
    val seedImported: Boolean = false,
    /** El usuario omitió la tarjeta "Define tu presupuesto del mes" del Inicio. */
    val budgetHintDismissed: Boolean = false,
    /** Último aviso de "haz un respaldo" (RF-12, mensual). */
    val lastBackupReminderAt: Long = 0,
)

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * [secureScreenDefault]: S-06 lo pide activo por defecto en release y desactivado en debug.
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val secureScreenDefault: Boolean,
) {
    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun setLockEnabled(value: Boolean) = set(Keys.LOCK_ENABLED, value)

    suspend fun setLockGraceMinutes(minutes: Int) {
        require(minutes in GRACE_OPTIONS) { "gracia fuera de las opciones" }
        set(Keys.LOCK_GRACE_MINUTES, minutes)
    }

    suspend fun setSecureScreen(value: Boolean) = set(Keys.SECURE_SCREEN, value)

    suspend fun setScanSound(value: Boolean) = set(Keys.SCAN_SOUND, value)

    suspend fun setScanVibration(value: Boolean) = set(Keys.SCAN_VIBRATION, value)

    suspend fun setShoppingFrequencyDays(days: Int) {
        require(days in FREQUENCY_RANGE) { "frecuencia fuera de rango" }
        set(Keys.SHOPPING_FREQUENCY_DAYS, days)
    }

    suspend fun setRemindersEnabled(value: Boolean) = set(Keys.REMINDERS_ENABLED, value)

    suspend fun setLastReminderCycle(value: Long) = set(Keys.LAST_REMINDER_CYCLE, value)

    suspend fun setLastBackupAt(value: Long) = set(Keys.LAST_BACKUP_AT, value)

    suspend fun setSeedImported(value: Boolean) = set(Keys.SEED_IMPORTED, value)

    suspend fun setBudgetHintDismissed(value: Boolean) = set(Keys.BUDGET_HINT_DISMISSED, value)

    suspend fun setLastBackupReminderAt(value: Long) = set(Keys.LAST_BACKUP_REMINDER_AT, value)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings(secureScreen = secureScreenDefault)
        return Settings(
            lockEnabled = this[Keys.LOCK_ENABLED] ?: defaults.lockEnabled,
            lockGraceMinutes = this[Keys.LOCK_GRACE_MINUTES] ?: defaults.lockGraceMinutes,
            secureScreen = this[Keys.SECURE_SCREEN] ?: defaults.secureScreen,
            scanSound = this[Keys.SCAN_SOUND] ?: defaults.scanSound,
            scanVibration = this[Keys.SCAN_VIBRATION] ?: defaults.scanVibration,
            shoppingFrequencyDays = this[Keys.SHOPPING_FREQUENCY_DAYS] ?: defaults.shoppingFrequencyDays,
            remindersEnabled = this[Keys.REMINDERS_ENABLED] ?: defaults.remindersEnabled,
            lastReminderCycle = this[Keys.LAST_REMINDER_CYCLE] ?: defaults.lastReminderCycle,
            lastBackupAt = this[Keys.LAST_BACKUP_AT] ?: defaults.lastBackupAt,
            seedImported = this[Keys.SEED_IMPORTED] ?: defaults.seedImported,
            budgetHintDismissed = this[Keys.BUDGET_HINT_DISMISSED] ?: defaults.budgetHintDismissed,
            lastBackupReminderAt = this[Keys.LAST_BACKUP_REMINDER_AT] ?: defaults.lastBackupReminderAt,
        )
    }

    private object Keys {
        val LOCK_ENABLED = booleanPreferencesKey("lock_enabled")
        val LOCK_GRACE_MINUTES = intPreferencesKey("lock_grace_minutes")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val SCAN_SOUND = booleanPreferencesKey("scan_sound")
        val SCAN_VIBRATION = booleanPreferencesKey("scan_vibration")
        val SHOPPING_FREQUENCY_DAYS = intPreferencesKey("shopping_frequency_days")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val LAST_REMINDER_CYCLE = longPreferencesKey("last_reminder_cycle")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val SEED_IMPORTED = booleanPreferencesKey("seed_imported")
        val BUDGET_HINT_DISMISSED = booleanPreferencesKey("budget_hint_dismissed")
        val LAST_BACKUP_REMINDER_AT = longPreferencesKey("last_backup_reminder_at")
    }

    companion object {
        val GRACE_OPTIONS = setOf(1, 5, 15)
        val FREQUENCY_RANGE = 1..90
    }
}
