package com.xmu.course

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.xmu.course.domain.Course
import com.xmu.course.domain.Exam
import com.xmu.course.domain.TimetableConfig
import com.xmu.course.domain.examZone
import com.xmu.course.ui.timetable.TimetableWeekGrid
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExamGridComposeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun saturdayExamSharesItsGridRowWithAnOverlappingLesson() {
        val midnight = LocalDate.of(2026, 11, 28).atStartOfDay(examZone).toInstant().toEpochMilli()
        val exam = Exam("", "微积分", "", midnight + 520 * 60_000, midnight + 540 * 60_000, "A101")
        val course = Course(name = "课堂", dayOfWeek = 6, startSection = 1, duration = 1, weeks = setOf(1))
        var selected: Exam? = null
        rule.setContent {
            TimetableWeekGrid(week = 1, courses = listOf(course), exams = listOf(exam),
                skippedCourseIds = emptySet(), showGrid = true, config = TimetableConfig(timetableId = 1),
                onCourseClick = {}, onExamClick = { selected = it })
        }
        rule.onNodeWithText("周六").assertIsDisplayed()
        val lesson = rule.onNode(hasClickAction() and hasText("课堂")).fetchSemanticsNode().boundsInRoot
        val node = rule.onNode(hasClickAction() and hasText("考试 · 微积分"))
        val sitting = node.fetchSemanticsNode().boundsInRoot
        assertTrue(sitting.top > lesson.top && sitting.bottom < lesson.bottom)
        assertTrue(sitting.left >= lesson.right)
        node.performClick()
        rule.runOnIdle { assertEquals(exam, selected) }
    }
}
