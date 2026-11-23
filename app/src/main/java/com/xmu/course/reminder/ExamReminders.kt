package com.xmu.course.reminder

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.xmu.course.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class ExamLead(val hours: Long, val label: String) {
    DAY(24, "提前一天"), HOUR(1, "提前一小时");

    fun dueAt(start: Long) = start - TimeUnit.HOURS.toMillis(hours)
}

internal data class ReminderKey(val examId: Long, val lead: ExamLead) {
    val workName get() = "exam-$examId-${lead.name}"
}

object ExamReminders {
    private fun prefs(context: Context) = context.getSharedPreferences("exam_reminders", Context.MODE_PRIVATE)

    fun enabledSince(context: Context, lead: ExamLead) = prefs(context).getLong(lead.name, 0L)

    fun setEnabled(context: Context, lead: ExamLead, enabled: Boolean) {
        prefs(context).edit().putLong(lead.name, if (enabled) System.currentTimeMillis() else 0L).apply()
    }

    fun preferences(context: Context) = callbackFlow {
        val settings = prefs(context)
        fun emitSettings() { trySend(ExamLead.entries.associateWith { enabledSince(context, it) }) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> emitSettings() }
        settings.registerOnSharedPreferenceChangeListener(listener)
        emitSettings()
        awaitClose { settings.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun start(context: Context) {
        val manager = WorkManager.getInstance(context)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var previous = emptySet<ReminderKey>()
            combine(AppDatabase.getInstance(context).examDao().observeAll(), preferences(context)) { exams, settings ->
                buildMap {
                    for (row in exams) for ((lead, since) in settings) {
                        val due = lead.dueAt(row.exam.startAt)
                        if (since > 0L && due >= since) put(ReminderKey(row.id, lead), due)
                    }
                }
            }.collect { deadlines ->
                for (key in previous - deadlines.keys) manager.cancelUniqueWork(key.workName)
                val now = System.currentTimeMillis()
                for ((key, due) in deadlines) {
                    if (key in previous || due <= now) continue
                    val request = OneTimeWorkRequestBuilder<ExamReminderWorker>()
                        .setInputData(workDataOf("examId" to key.examId, "lead" to key.lead.name))
                        .setInitialDelay(due - now, TimeUnit.MILLISECONDS)
                        .build()
                    manager.enqueueUniqueWork(key.workName, ExistingWorkPolicy.KEEP, request)
                }
                previous = deadlines.keys
            }
        }
    }
}
