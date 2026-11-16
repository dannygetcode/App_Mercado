package co.daniel.mercado.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.daniel.mercado.MainActivity
import co.daniel.mercado.MercadoApp
import co.daniel.mercado.R
import co.daniel.mercado.data.settings.SettingsStore
import co.daniel.mercado.di.DataGraph
import co.daniel.mercado.domain.port.Clock
import co.daniel.mercado.domain.rules.ReminderRules
import java.util.concurrent.TimeUnit

/** Lo que el recordatorio muestra. Sin nombres ni montos: solo cuántos sugeridos. */
interface ReminderNotifier {
    fun shopping(suggestedCount: Int)
    fun backup()
}

/**
 * La revisión diaria (RF-11 y respaldo mensual), separada del worker para probarla en JVM.
 * Una notificación por ciclo: guarda el ciclo avisado en los ajustes.
 */
class ReminderCheck(
    private val graph: suspend () -> DataGraph,
    private val settings: SettingsStore,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) {
    suspend fun run() {
        val s = settings.current()
        if (!s.remindersEnabled) return
        val g = graph()
        g.shouldRemindShopping(s.shoppingFrequencyDays, s.lastReminderCycle)?.let { reminder ->
            notifier.shopping(reminder.suggestedCount)
            settings.setLastReminderCycle(reminder.cycle)
        }
        val now = clock.nowMillis()
        if (ReminderRules.shouldRemindBackup(s.lastBackupAt, s.lastBackupReminderAt, now, hasData = g.trips.lastClosedAt() != null)) {
            notifier.backup()
            settings.setLastBackupReminderAt(now)
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MercadoApp).container
        ReminderCheck(container::data, container.settings, AndroidReminderNotifier(applicationContext), container.clock).run()
        return Result.success()
    }
}

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {

    override fun shopping(suggestedCount: Int) = notify(
        NOTIFICATION_SHOPPING,
        context.getString(R.string.reminder_shopping_title),
        if (suggestedCount > 0) {
            context.resources.getQuantityString(R.plurals.reminder_shopping_text, suggestedCount, suggestedCount)
        } else {
            context.getString(R.string.reminder_shopping_text_none)
        },
    )

    override fun backup() = notify(
        NOTIFICATION_BACKUP,
        context.getString(R.string.reminder_backup_title),
        context.getString(R.string.reminder_backup_text),
    )

    private fun notify(id: Int, title: String, text: String) {
        if (!Reminders.canNotify(context)) return
        Reminders.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permiso retirado entre la revisión y el aviso: no hay nada que hacer.
        }
    }

    private companion object {
        const val NOTIFICATION_SHOPPING = 1
        const val NOTIFICATION_BACKUP = 2
    }
}

/** Programación del trabajo diario y canal de notificaciones. */
object Reminders {
    private const val WORK = "mercado-recordatorios"

    fun schedule(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
            work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(WORK)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}

private const val CHANNEL = "recordatorios"
