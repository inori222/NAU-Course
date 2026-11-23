package com.xmu.course.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.xmu.course.MainActivity
import com.xmu.course.data.local.AppDatabase

internal const val EXAM_CHANNEL = "exam_reminders"

class ExamReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val lead = ExamLead.entries.firstOrNull { it.name == inputData.getString("lead") } ?: return Result.success()
        return deliver(applicationContext, inputData.getLong("examId", -1), lead, System.currentTimeMillis())
    }
}

internal suspend fun deliver(context: Context, examId: Long, lead: ExamLead, now: Long): Result {
    val since = ExamReminders.enabledSince(context, lead)
    val row = AppDatabase.getInstance(context).examDao().get(examId) ?: return Result.success()
    val exam = row.exam
    val due = lead.dueAt(exam.startAt)
    if (since == 0L || due < since || now >= exam.startAt) return Result.success()
    if (now < due) return Result.retry()
    // A delayed day reminder may coincide with the shorter reminder during device sleep.
    if (lead == ExamLead.DAY && now >= ExamLead.HOUR.dueAt(exam.startAt) &&
        ExamReminders.enabledSince(context, ExamLead.HOUR) in 1L..ExamLead.HOUR.dueAt(exam.startAt)) return Result.success()
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) return Result.success()
    val notifications = context.getSystemService(NotificationManager::class.java)
    notifications.createNotificationChannel(NotificationChannel(EXAM_CHANNEL, "考前提醒", NotificationManager.IMPORTANCE_HIGH))
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return Result.success()
    val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val notice = NotificationCompat.Builder(context, EXAM_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("考试提醒 · ${exam.name}")
        .setContentText(exam.scheduleText())
        .setStyle(NotificationCompat.BigTextStyle().bigText(exam.scheduleText()))
        .setContentIntent(open).setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .build()
    notifications.notify("exam-${row.id}", 1, notice)
    return Result.success()
}
