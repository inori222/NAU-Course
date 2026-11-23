package com.xmu.course

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.data.ExamRepository
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.data.local.CourseEntity
import com.xmu.course.parser.NauExamParser
import com.xmu.course.ui.import.NauDomCapture
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExamImportTest {
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readText()
    private val html = fixture("sample_nau_exam.html")
    private val parser = NauExamParser()

    @Test fun renderedExamAndFrame() {
        val frame = Jsoup.parse(fixture("sample_nau_exam_frame.html")).selectFirst("iframe")!!
        assertTrue(NauDomCapture.examScript.contains(frame.id()))
        assertFalse(parser.hasExamRows(frame.outerHtml()))
        assertFalse(parser.hasExamRows(fixture("sample_nau_timetable.html")))
        assertTrue(parser.hasExamRows(html))
        val schedule = parser.parse(html)
        assertEquals("2026-2027-1", schedule.semester.code)
        assertEquals(2, schedule.exams.size)
        val first = schedule.exams.first()
        assertEquals("00000001", first.courseCode)
        assertEquals(LocalDate.of(2000, 1, 1), first.date())
        assertTrue(first.scheduleText().contains("12:00–13:30"))
        assertEquals("示例楼101", first.location)
        assertEquals(0, schedule.skippedRows)
    }

    @Test fun invalidAndDuplicateRows() {
        val doc = Jsoup.parse(html)
        val row = doc.select("tr").last()!!
        row.after(row.clone())
        assertEquals(2, parser.parse(doc.outerHtml()).exams.size)
        val partial = parser.parse(html.replace("14:30-16:30", "25:00-16:30"))
        assertEquals(1, partial.skippedRows)
        assertEquals(1, partial.exams.size)
        listOf("", fixture("sample_nau_timetable.html"), html.replace("2000年", "待定")).forEach {
            assertThrows(IllegalArgumentException::class.java) { parser.parse(it) }
        }
    }

    @Test fun importsAreTransactionalAndScopedToExams() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = ExamRepository(db)
            val schedule = parser.parse(html)
            val pending = repository.preview(schedule)
            val timetable = repository.commit(pending)
            val existing = repository.preview(schedule)
            val semesterId = requireNotNull(existing.semesterId)
            db.courseDao().insert(CourseEntity(semesterId = semesterId, name = "保留课程", teacher = "",
                location = "", dayOfWeek = 2, startSection = 1, duration = 2, weeks = "1,2",
                source = "MANUAL", color = "#888888", note = ""))
            assertEquals(2, existing.previous.size)
            assertTrue(existing.added.isEmpty() && existing.removed.isEmpty())
            assertEquals(timetable, repository.commit(existing))
            assertEquals(existing.previous, repository.preview(schedule).previous)
            val partial = repository.preview(schedule.copy(exams = schedule.exams.take(1), skippedRows = 1))
            assertTrue(partial.removed.isEmpty())
            val changed = repository.preview(schedule.copy(exams = listOf(schedule.exams.first().copy(location = "新考场"))))
            assertEquals(2, changed.removed.size)
            assertEquals(1, changed.added.size)
            repository.commit(changed)
            assertEquals(1, db.courseDao().countBySemester(semesterId))
            assertTrue(runCatching { repository.commit(existing) }.isFailure)
            db.semesterDao().deleteById(semesterId)
            assertTrue(db.examDao().list(semesterId).isEmpty())
        } finally {
            db.close()
        }
    }
}
