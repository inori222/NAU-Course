package com.xmu.course

import com.xmu.course.domain.Course
import com.xmu.course.domain.Exam
import com.xmu.course.domain.examZone
import com.xmu.course.ui.timetable.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MinuteGridTest {
    private val midnight = LocalDate.of(2026, 11, 23).atStartOfDay(examZone).toInstant().toEpochMilli()
    private fun exam(start: Int, end: Int) = Exam("", "考试", "", midnight + 60_000L * start, midnight + 60_000L * end, "101")
    private fun lesson(section: Int, count: Int) = TimetableLayoutItem(Course(
        name = "课程", dayOfWeek = 1, startSection = section, duration = count, weeks = setOf(1)))

    @Test fun originalRowsAndFractionalPlacement() {
        val exam = exam(515, 540)
        val grid = MinuteGrid(13, listOf(exam))
        assertEquals(13f, grid.height, 0f)
        val lanes = grid.layout(listOf(lesson(1, 2)), listOf(exam))
        assertTrue(lanes.all { it.count == 2 })
        assertEquals(0f to 2f, grid.bounds(lanes.first().item))
        assertEquals(0.125f to 0.75f, grid.bounds(lanes.last().item))
        assertEquals(0.25f, grid.position(520), 0f)
    }

    @Test fun lunchBreakAndBackToBackExamsHaveDistinctPositions() {
        val lunch = exam(760, 790)
        val second = exam(790, 810)
        val exams = listOf(lunch, second)
        val grid = MinuteGrid(13, exams)
        val lanes = grid.layout(listOf(lesson(5, 1), lesson(6, 1)), exams)
        assertTrue(lanes.all { it.count == 1 })
        assertTrue(grid.position(760) > 4f && grid.position(790) < 5f)
        assertEquals(5f, grid.position(810), 0f)
        val otherDay = grid.layout(listOf(lesson(5, 1)), emptyList())
        assertEquals(4f to 5f, grid.bounds(otherDay.single().item))
        assertEquals(13f, grid.height, 0f)
    }

    @Test fun edgeExamsAndLegacyRowsStayInsideGrid() {
        val early = exam(420, 480)
        val late = exam(1290, 1320)
        val grid = MinuteGrid(14, listOf(early, late))
        assertEquals(0f, grid.position(420), 0f)
        assertEquals(grid.sectionTop(13), grid.position(1320), 0f)
        val extra = grid.layout(listOf(lesson(14, 1)), emptyList()).single().item
        assertEquals(grid.sectionTop(13) to grid.height, grid.bounds(extra))
    }
}
