package com.example.skinhistoryscanner.notifications

import android.content.Context
import androidx.work.*
import com.example.skinhistoryscanner.data.domain.ReminderUnit
import java.util.concurrent.TimeUnit

object ReminderManager {

    private const val REMINDER_WORK_NAME = "skin_check_reminder_work"

    fun scheduleReminder(context: Context, enabled: Boolean, value: Int, unit: ReminderUnit) {
        val workManager = WorkManager.getInstance(context)

        if (!enabled) {
            workManager.cancelUniqueWork(REMINDER_WORK_NAME)
            return
        }

        val repeatInterval = if (unit == ReminderUnit.DAYS) {
            value.toLong()
        } else {
            value.toLong() * 30 // Approximate months as 30 days
        }

        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            repeatInterval, TimeUnit.DAYS
        )
        .setInitialDelay(repeatInterval, TimeUnit.DAYS)
        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
        .build()

        workManager.enqueueUniquePeriodicWork(
            REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
