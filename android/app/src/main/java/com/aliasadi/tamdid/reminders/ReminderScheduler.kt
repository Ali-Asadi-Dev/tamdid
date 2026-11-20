package com.aliasadi.tamdid.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.aliasadi.tamdid.MainActivity
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.appContainer
import com.aliasadi.tamdid.core.AppData
import com.aliasadi.tamdid.core.AppLanguage
import com.aliasadi.tamdid.core.DateText
import com.aliasadi.tamdid.core.MoneyFormatter
import com.aliasadi.tamdid.core.PlannedReminder
import com.aliasadi.tamdid.core.ReminderPlanner
import com.aliasadi.tamdid.data.Clock
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Turns the shared [ReminderPlanner] plan into inexact alarms (no special permission needed),
 * the Android counterpart of iOS pending local notifications. The plan is rebuilt whenever
 * data changes, the app starts, the device reboots, or the time zone changes.
 */
class ReminderScheduler(private val context: Context, private val clock: Clock) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    init {
        createChannel(context)
    }

    fun reschedule(data: AppData) {
        val localized = localizedContext(context)
        val language = languageOf(localized)
        val plan = ReminderPlanner.plan(data.subscriptions, clock.today())
        val zone = ZoneId.systemDefault()
        val previous = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty()
        val current = plan.map { it.id }.toSet()

        for (id in previous - current) alarmManager.cancel(pendingIntent(id, null))
        for (reminder in plan) {
            val fireAt = reminder.fireDate.atTime(data.settings.reminderHour, 0).atZone(zone).toInstant().toEpochMilli()
            if (fireAt < System.currentTimeMillis()) continue
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra(EXTRA_ID, reminder.id)
                .putExtra(EXTRA_TITLE, title(localized, reminder, language))
                .putExtra(EXTRA_BODY, body(localized, reminder, language, data))
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent(reminder.id, intent))
        }
        prefs.edit { putStringSet(KEY_IDS, current) }
    }

    private fun pendingIntent(id: String, intent: Intent?): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        intent ?: Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "renewals"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        private const val KEY_IDS = "scheduled_ids"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                localizedContext(context).getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Resources in the language the user picked for this app (per-app language). */
        fun localizedContext(context: Context): Context {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return context
            val configuration = Configuration(context.resources.configuration).apply { setLocale(locales[0]) }
            return context.createConfigurationContext(configuration)
        }

        fun languageOf(context: Context): AppLanguage {
            val locale: Locale = context.resources.configuration.locales[0]
            return AppLanguage.forLocale(locale.language)
        }

        internal fun title(context: Context, reminder: PlannedReminder, language: AppLanguage): String {
            val days = ChronoUnit.DAYS.between(reminder.fireDate, reminder.chargeDate).toInt()
            val whenText = when (days) {
                0 -> context.getString(R.string.relative_today)
                1 -> context.getString(R.string.relative_tomorrow)
                else -> context.resources.getQuantityString(R.plurals.relative_in_days, days, days)
            }
            val res = if (reminder.kind == PlannedReminder.Kind.TrialEnding) R.string.notification_trial_title else R.string.notification_renewal_title
            return com.aliasadi.tamdid.core.Digits.localize(context.getString(res, reminder.subscriptionName, whenText), language)
        }

        internal fun body(context: Context, reminder: PlannedReminder, language: AppLanguage, data: AppData): String =
            context.getString(
                R.string.notification_body,
                MoneyFormatter.format(reminder.amount, language),
                DateText.format(reminder.chargeDate, data.settings.calendar, language, DateText.Style.Medium),
            )
    }
}

/** Posts the notification for one reminder. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) {
            return
        }
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tamdid)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(intent.getStringExtra(ReminderScheduler.EXTRA_TITLE))
            .setContentText(intent.getStringExtra(ReminderScheduler.EXTRA_BODY))
            .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra(ReminderScheduler.EXTRA_BODY)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call.
        }
    }
}

/** Rebuilds the alarms after a reboot, app update, or clock/time-zone change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val container = context.appContainer
        container.repository.data.value?.let(container.reminderScheduler::reschedule)
        // If data is still loading, the repository reschedules as soon as it has loaded.
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
