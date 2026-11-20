package com.aliasadi.tamdid.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aliasadi.tamdid.SampleData
import com.aliasadi.tamdid.core.AppData
import com.aliasadi.tamdid.core.AppLanguage
import com.aliasadi.tamdid.core.Money
import com.aliasadi.tamdid.core.CurrencyCode
import com.aliasadi.tamdid.core.PlannedReminder
import com.aliasadi.tamdid.core.ReminderPlanner
import com.aliasadi.tamdid.data.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))

    // Use the real today so alarm times are in the future.
    private val today = LocalDate.now(ZoneId.systemDefault())
    private val clock = Clock { today }

    private fun dataRenewingSoon(): AppData {
        val base = SampleData.english()
        // Shift every start date so each subscription renews within the next month.
        return base.copy(subscriptions = base.subscriptions.mapIndexed { i, s ->
            s.copy(startDate = today.plusDays(3L + i).minusMonths(2), trialEndDate = null, reminderDaysBefore = 1)
        })
    }

    @Test
    fun schedulesOneAlarmPerPlannedReminderAndCancelsRemovedOnes() {
        val scheduler = ReminderScheduler(context, clock)
        val data = dataRenewingSoon()
        val expected = ReminderPlanner.plan(data.subscriptions, today).count {
            it.fireDate.atTime(data.settings.reminderHour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() > System.currentTimeMillis()
        }
        scheduler.reschedule(data)
        assertTrue(expected > 0)
        assertEquals(expected, alarms.scheduledAlarms.size)

        // Removing everything cancels every alarm that was set.
        scheduler.reschedule(data.copy(subscriptions = emptyList()))
        assertEquals(0, alarms.scheduledAlarms.size)
    }

    @Test
    fun alarmsFireAtTheReminderHour() {
        val scheduler = ReminderScheduler(context, clock)
        val data = dataRenewingSoon().let { it.copy(settings = it.settings.copy(reminderHour = 8)) }
        scheduler.reschedule(data)
        for (alarm in alarms.scheduledAlarms) {
            val time = java.time.Instant.ofEpochMilli(alarm.triggerAtTime).atZone(ZoneId.systemDefault())
            assertEquals(8, time.hour)
            assertEquals(0, time.minute)
        }
    }

    @Test
    @Config(qualifiers = "fa")
    fun notificationTextIsLocalised() {
        val reminder = PlannedReminder("x", "فیلیمو", PlannedReminder.Kind.Renewal, LocalDate.of(2026, 11, 22),
            LocalDate.of(2026, 11, 21), Money(159_000, CurrencyCode.Toman))
        val data = SampleData.persian()
        assertEquals("فیلیمو فردا تمدید می‌شود", ReminderScheduler.title(context, reminder, AppLanguage.Persian))
        assertEquals("۱۵۹٬۰۰۰ تومان در تاریخ ۱ آذر ۱۴۰۵ پرداخت می‌شود.", ReminderScheduler.body(context, reminder, AppLanguage.Persian, data))
        val trial = reminder.copy(kind = PlannedReminder.Kind.TrialEnding, fireDate = LocalDate.of(2026, 11, 19))
        assertEquals("دوره‌ی رایگان فیلیمو ۳ روز دیگر تمام می‌شود", ReminderScheduler.title(context, trial, AppLanguage.Persian))
    }
}
