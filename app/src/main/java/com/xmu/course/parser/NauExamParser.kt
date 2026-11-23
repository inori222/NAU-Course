package com.xmu.course.parser

import com.xmu.course.domain.Exam
import com.xmu.course.domain.examZone
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

data class ExamSchedule(val semester: ParsedSemester, val exams: List<Exam>, val skippedRows: Int)

class NauExamParser {
    private val interval = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日(\d{1,2}):(\d{2})[-－–](\d{1,2}):(\d{2})""")
    private val columns = listOf("课程编号", "课程名称", "教学班", "考试时间", "考试地点")

    private fun table(html: String): Element? = Jsoup.parse(html).selectFirst("table#content")
        ?.takeIf { it.selectFirst(".tdTitle")?.text()?.contains("考试日程列表") == true }

    fun hasExamRows(html: String): Boolean = table(html)?.select("tr")?.any { row ->
        row.children().size >= columns.size && row.child(0).tagName() == "td"
    } == true

    fun parse(html: String): ExamSchedule {
        val table = requireNotNull(table(html)) { "请进入修学信息中的考试日程查询" }
        val semester = parseNauSemester(table.selectFirst(".tdTitle")!!.text())
        require(semester.code.isNotEmpty()) { "未识别考试学期" }
        val rows = table.select("tr")
        val header = rows.indexOfFirst { it.children().any { cell -> cell.tagName() == "th" } }
        require(header >= 0) { "考试列表缺少表头" }
        val headings = rows[header].children().map { it.text().trim() }
        require(columns.all { name -> headings.count { it == name } == 1 }) { "考试列表缺少必要列或表头重复" }
        val indices = columns.map(headings::indexOf)
        val exams = linkedSetOf<Exam>()
        var skipped = 0
        for (row in rows.drop(header + 1)) {
            val cells = row.children()
            fun field(index: Int) = cells.getOrNull(indices[index])?.text().orEmpty().trim()
            val match = interval.matchEntire(field(3).replace(Regex("""[\s\p{Z}]+"""), ""))
            val times = match?.let { toTimes(it) }
            if (times == null || field(1).isBlank() || cells.size != headings.size) {
                skipped++
            } else {
                exams += Exam(field(0), field(1), field(2), times.first, times.second, field(4))
            }
        }
        require(exams.isNotEmpty()) { "未找到有明确日期和起止时间的考试，本地日程已保留" }
        return ExamSchedule(semester, exams.sortedBy { it.startAt }, skipped)
    }

    private fun toTimes(match: MatchResult): Pair<Long, Long>? {
        val n = match.groupValues.drop(1).map(String::toInt)
        return try {
            val date = LocalDate.of(n[0], n[1], n[2])
            fun at(hour: Int, minute: Int) = date.atTime(LocalTime.of(hour, minute))
                .atZone(examZone).toInstant().toEpochMilli()
            val start = at(n[3], n[4])
            val end = at(n[5], n[6])
            if (end > start) start to end else null
        } catch (_: DateTimeException) {
            null
        }
    }
}
