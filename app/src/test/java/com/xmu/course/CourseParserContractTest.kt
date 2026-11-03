package com.xmu.course

import com.xmu.course.parser.CourseParser
import com.xmu.course.parser.XmuKingosoftParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseParserContractTest {
    @Test fun `通过统一接口保留厦大真实样本解析能力`() {
        val html = javaClass.getResourceAsStream("/sample_timetable.html")!!.bufferedReader().use { it.readText() }
        val parser: CourseParser = XmuKingosoftParser()
        val result = parser.parse(html)
        assertEquals(XmuKingosoftParser().parse(html), result)
        assertEquals(38, result.courses.size)
        assertTrue(result.semesterCode.isNotBlank())
    }
}
