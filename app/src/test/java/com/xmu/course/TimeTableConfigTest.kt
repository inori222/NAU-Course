package com.xmu.course

import com.xmu.course.domain.Timetable
import com.xmu.course.ui.timetable.TimeTableConfig
import com.xmu.course.ui.timetable.TimetableViewModel
import com.xmu.course.ui.timetable.effectiveSectionCount
import com.xmu.course.domain.Course
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TimeTableConfigTest {

    @Test fun `自定义节数不会裁掉已有课程`() {
        val late = Course(name = "晚课", dayOfWeek = 1, startSection = 12, duration = 2, weeks = setOf(1))
        assertEquals(8, effectiveSectionCount(8, emptyList()))
        assertEquals(13, effectiveSectionCount(8, listOf(late)))
        assertEquals(14, effectiveSectionCount(8, listOf(late.copy(startSection = 13))))
    }

    @Test fun `南审公开作息为13节`() {
        assertEquals(13, TimeTableConfig.sectionCount)
        assertEquals(11, TimeTableConfig.xmuSections.size)
    }

    @Test fun `节次时间与作息一致`() {
        assertEquals("08:30-09:10", TimeTableConfig.timeOf(1))
        assertEquals("11:10-11:50", TimeTableConfig.timeOf(4))
        assertEquals("12:00-12:40", TimeTableConfig.timeOf(5))
        assertEquals("20:10-20:50", TimeTableConfig.timeOf(13))
        assertEquals("", TimeTableConfig.timeOf(0))
        assertEquals("", TimeTableConfig.timeOf(14))
    }
}

class CurrentWeekTest {

    @Test fun `根据开学日期计算当前周`() {
        val semester = Timetable(name = "test", semesterId = 1L, startDate = "2026-09-14")
        // 开学当天 = 第1周周一
        assertEquals(1, TimetableViewModel.computeCurrentWeek(semester, LocalDate.parse("2026-09-14")))
        // 第7天（周日）仍是第1周
        assertEquals(1, TimetableViewModel.computeCurrentWeek(semester, LocalDate.parse("2026-09-20")))
        // 第8天进入第2周
        assertEquals(2, TimetableViewModel.computeCurrentWeek(semester, LocalDate.parse("2026-09-21")))
    }

    @Test fun `开学前与未设置日期回退第1周`() {
        val semester = Timetable(name = "test", semesterId = 1L, startDate = "2026-09-14")
        assertEquals(1, TimetableViewModel.computeCurrentWeek(semester, LocalDate.parse("2026-09-01")))
        assertEquals(1, TimetableViewModel.computeCurrentWeek(Timetable(name = "n", semesterId = 1L), LocalDate.now()))
        // 非法日期格式
        assertEquals(1, TimetableViewModel.computeCurrentWeek(
            Timetable(name = "n", semesterId = 1L, startDate = "bad"), LocalDate.now(),
        ))
    }
}
