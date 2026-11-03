package com.xmu.course

import com.xmu.course.parser.NauCourseParser
import com.xmu.course.parser.NauStudentPageAdapter
import com.xmu.course.parser.ParseFailure
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

/** 用户提供的真实页面结构，所有课程、教师、地点等均已替换为示例值。 */
class NauStudentPageAdapterTest {
    private val parser = NauCourseParser(NauStudentPageAdapter())
    private fun sample(): String = javaClass.getResourceAsStream("/sample_nau_timetable.html")!!
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test fun `真实样本解析全部排课与学期`() {
        val result = parser.parse(sample())
        assertNull(result.failure)
        assertEquals("2026-2027-1", result.semesterCode)
        assertEquals("2026-2027学年第一学期", result.semesterName)
        assertEquals(18, result.courses.size)
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        val first = result.courses.first()
        assertEquals("示例016", first.name)
        assertEquals("示例020", first.teacher)
        assertEquals("示例021", first.location)
        assertEquals(5, first.dayOfWeek)
        assertEquals(3, first.startSection)
        assertEquals(2, first.duration)
        assertEquals((2..16 step 2).toSet(), first.weeks)
        assertTrue(result.courses.any { it.weeks == (1..17 step 2).toSet() })
        assertTrue(result.courses.any { it.dayOfWeek == 7 && it.duration == 5 && it.weeks == setOf(10) })
        assertTrue(parser.isTimetableReady(sample()))
    }

    @Test fun `重复记录去重同时间不同课程保留`() {
        val doc = Jsoup.parse(sample())
        val table = doc.selectFirst("table#content tbody")!!
        val row = table.select("tr")[1]
        table.appendChild(row.clone())
        val result = parser.parse(doc.outerHtml())
        assertEquals(18, result.courses.size)
        assertTrue(result.warnings.any { it.contains("重复") })
        val different = row.clone()
        different.select("> td")[2].text("冲突课程")
        table.appendChild(different)
        assertEquals(20, parser.parse(doc.outerHtml()).courses.size)
    }

    @Test fun `局部缺失与无效时间不会丢失其他记录`() {
        val doc = Jsoup.parse(sample())
        val rows = doc.select("table#content tr")
        rows[1].select("> td")[7].text("")
        rows[1].select("> td")[8].selectFirst("span")!!.text("格式损坏")
        rows[2].select("> td")[8].select("span").remove()
        val result = parser.parse(doc.outerHtml())
        assertNull(result.failure)
        assertEquals(15, result.courses.size)
        assertTrue(result.warnings.any { it.contains("读取失败") })
        assertTrue(result.warnings.any { it.contains("教师") })
    }

    @Test fun `无学期无课程及错误页面拒绝写入`() {
        val doc = Jsoup.parse(sample())
        doc.select(".tdTitle").remove()
        assertEquals(ParseFailure.MISSING_SEMESTER, parser.parse(doc.outerHtml()).failure)
        val empty = Jsoup.parse(sample())
        empty.select("table#content tr").drop(1).forEach { it.remove() }
        assertEquals(ParseFailure.NO_COURSES, parser.parse(empty.outerHtml()).failure)
        assertFalse(parser.isTimetableReady(empty.outerHtml()))
        assertEquals(ParseFailure.WRONG_PAGE, parser.parse("<html><body>登录</body></html>").failure)
        assertFalse(parser.isTimetableReady("<iframe id='FrameContent'></iframe>"))
        assertEquals(ParseFailure.EMPTY_HTML, parser.parse("").failure)
    }
}
