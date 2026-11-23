package com.xmu.course

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import com.xmu.course.data.ExamRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.parser.NauExamParser
import com.xmu.course.reminder.*
import com.xmu.course.ui.timetable.examsInWeek
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExamReminderTest {
    private val schedule = NauExamParser().parse(javaClass.getResource("/sample_nau_exam.html")!!.readText())

    @Test fun teachingWeekIncludesWeekendAndUsesSemesterStart() {
        assertEquals(schedule.exams, examsInWeek(schedule.exams, "1999-12-27", 1))
        assertEquals(schedule.exams, examsInWeek(schedule.exams, "1999-12-20", 2))
        assertTrue(examsInWeek(schedule.exams, "1999-12-27", 2).isEmpty())
        assertTrue(examsInWeek(schedule.exams, null, 1).isEmpty())
    }

    @Test fun notificationRechecksConsentDeadlineAndCurrentExam() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val notifications = app.getSystemService(NotificationManager::class.java)
        AppDatabase.closeAndResetForTest()
        val repository = ExamRepository(AppDatabase.getInstance(app))
        try {
            val now = System.currentTimeMillis()
            val start = now + TimeUnit.DAYS.toMillis(3)
            val exam = schedule.exams.first().copy(startAt = start, endAt = start + 3_600_000L)
            val future = schedule.copy(exams = listOf(exam))
            repository.commit(repository.preview(future))
            val imported = repository.preview(future)
            val id = imported.previous.single().id
            val due = ExamLead.HOUR.dueAt(start)
            assertEquals(start - 3_600_000L, due)
            assertEquals(start - 86_400_000L, ExamLead.DAY.dueAt(start))
            deliver(app, id, ExamLead.HOUR, due)
            assertTrue(notifications.activeNotifications.isEmpty())
            ExamReminders.setEnabled(app, ExamLead.HOUR, true)
            shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            deliver(app, id, ExamLead.HOUR, due)
            assertTrue(notifications.activeNotifications.isEmpty())
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            assertEquals(Result.retry(), deliver(app, id, ExamLead.HOUR, due - 1))
            deliver(app, id, ExamLead.HOUR, start)
            assertTrue(notifications.activeNotifications.isEmpty())
            deliver(app, id, ExamLead.HOUR, due)
            assertEquals(1, notifications.activeNotifications.size)
            notifications.cancelAll()
            repository.commit(repository.preview(future.copy(exams = listOf(exam.copy(startAt = start + 1000L)))))
            deliver(app, id, ExamLead.HOUR, due)
            assertTrue(notifications.activeNotifications.isEmpty())
            val moved = repository.preview(future).previous.single()
            repository.remove(moved.semesterId, moved.exam)
            deliver(app, moved.id, ExamLead.HOUR, due + 1000L)
            assertTrue(notifications.activeNotifications.isEmpty())
        } finally {
            ExamReminders.setEnabled(app, ExamLead.HOUR, false)
            AppDatabase.closeAndResetForTest()
        }
    }
}
