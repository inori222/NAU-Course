package com.xmu.course

import com.xmu.course.parser.*
import java.util.concurrent.CancellationException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.Assert.*
import org.junit.Test

/**
 * 流水线契约测试，不是南京审计大学 DOM 测试。
 * 记录由测试替身在内存中构造；没有虚构学校 selector 或学校 HTML 样本。
 * 真实样本及字段定位测试必须等用户提供脱敏 HTML 后补充。
 */
class NauCourseParserTest {
    private val contractPage = "<html><body>解析接口契约测试，不是学校课表</body></html>"

    private fun record(name: String = "测试课程", weeks: String = "1-16周") = ParsedCourse(
        name = name, teacher = "测试教师", location = "测试教室",
        dayOfWeek = 1, startSection = 1, duration = 2, weeks = WeekPatternParser.parse(weeks),
    )

    private class Adapter(
        val records: List<ParsedCourse>,
        val state: TimetablePageState = TimetablePageState.READY,
        val term: ParsedSemester = ParsedSemester("contract-term", "测试学期"),
        val brokenIndex: Int? = null,
    ) : NauPageAdapter {
        override fun pageState(document: Document) = state
        override fun semester(document: Document) = term
        override fun courseNodes(document: Document) = records.indices.map { Element("record").attr("index", it.toString()) }
        override fun course(node: Element): ParsedCourse {
            val index = node.attr("index").toInt()
            check(index != brokenIndex) { "局部记录异常" }
            return records[index]
        }
    }

    @Test fun `空HTML与尚未适配明确区分`() {
        val parser = NauCourseParser(null)
        assertEquals(ParseFailure.EMPTY_HTML, parser.parse(" \n ").failure)
        val result = parser.parse(contractPage)
        assertEquals(ParseFailure.ADAPTATION_REQUIRED, result.failure)
        assertTrue(result.warnings.single().contains("完整 HTML"))
        assertTrue(result.courses.isEmpty())
        assertFalse(parser.isTimetableReady(contractPage))
    }

    @Test fun `不把厦大样本误认成南审`() {
        val html = javaClass.getResourceAsStream("/sample_timetable.html")!!.bufferedReader().use { it.readText() }
        assertEquals(ParseFailure.WRONG_PAGE, NauCourseParser().parse(html).failure)
    }

    @Test fun `错误页面与动态加载状态`() {
        val wrong = NauCourseParser(Adapter(listOf(record()), state = TimetablePageState.WRONG_PAGE))
        val loading = NauCourseParser(Adapter(listOf(record()), state = TimetablePageState.LOADING))
        assertEquals(ParseFailure.WRONG_PAGE, wrong.parse(contractPage).failure)
        assertEquals(ParseFailure.NOT_READY, loading.parse(contractPage).failure)
        assertFalse(loading.isTimetableReady(contractPage))
        assertTrue(NauCourseParser(Adapter(listOf(record()))).isTimetableReady(contractPage))
    }

    @Test fun `缺少学期或课程节点拒绝导入`() {
        val noTerm = NauCourseParser(Adapter(listOf(record()), term = ParsedSemester("", "")))
        assertEquals(ParseFailure.MISSING_SEMESTER, noTerm.parse(contractPage).failure)
        assertEquals(ParseFailure.NO_COURSES, NauCourseParser(Adapter(emptyList())).parse(contractPage).failure)
    }

    @Test fun `保留连堂单双周和离散周`() {
        val records = listOf(record(weeks = "1-16周(单)"), record("双周课", "1-16周(双)"), record("混合周", "1-5,7,9-12周"))
        val result = NauCourseParser(Adapter(records)).parse(contractPage)
        assertNull(result.failure)
        assertEquals(records, result.courses)
        assertEquals(2, result.courses.first().duration)
        assertEquals((1..16 step 2).toSet(), result.courses.first().weeks)
        assertEquals((2..16 step 2).toSet(), result.courses[1].weeks)
    }

    @Test fun `缺教师教室保留并警告单条损坏不影响其他记录`() {
        val missingFields = record().copy(teacher = " ", location = "")
        val result = NauCourseParser(Adapter(listOf(missingFields, record("损坏课程"), record("正常课程")), brokenIndex = 1)).parse(contractPage)
        assertNull(result.failure)
        assertEquals(2, result.courses.size)
        assertEquals("", result.courses.first().teacher)
        assertTrue(result.warnings.any { it.contains("教师") })
        assertTrue(result.warnings.any { it.contains("教室") })
        assertTrue(result.warnings.any { it.contains("读取失败") })
    }

    @Test fun `去重但不删除同时间冲突课程`() {
        val first = record()
        val result = NauCourseParser(Adapter(listOf(first, first.copy(name = " ${first.name} "), record("冲突课程")))).parse(contractPage)
        assertEquals(2, result.courses.size)
        assertTrue(result.warnings.single().contains("重复"))
        assertEquals(result.courses[0].startSection, result.courses[1].startSection)
    }

    @Test fun `无效记录全部跳过不会形成空覆盖`() {
        val records = listOf(record().copy(dayOfWeek = 8), record().copy(duration = Int.MAX_VALUE), record().copy(weeks = emptySet()))
        val result = NauCourseParser(Adapter(records)).parse(contractPage)
        assertEquals(ParseFailure.NO_COURSES, result.failure)
        assertTrue(result.courses.isEmpty())
        assertEquals(4, result.warnings.size)
    }

    @Test fun `适配器异常只返回安全提示且取消不被吞掉`() {
        val adapter = object : NauPageAdapter by Adapter(emptyList()) {
            override fun pageState(document: Document): TimetablePageState = error("不应泄露的网页字段")
        }
        val result = NauCourseParser(adapter).parse(contractPage)
        assertEquals(ParseFailure.INVALID_HTML, result.failure)
        assertFalse(result.warnings.joinToString().contains("不应泄露"))
        assertFalse(NauCourseParser(adapter).isTimetableReady(contractPage))
        val cancelled = object : NauPageAdapter by adapter {
            override fun pageState(document: Document): TimetablePageState = throw CancellationException()
        }
        assertTrue(runCatching { NauCourseParser(cancelled).parse(contractPage) }.exceptionOrNull() is CancellationException)
    }
}
