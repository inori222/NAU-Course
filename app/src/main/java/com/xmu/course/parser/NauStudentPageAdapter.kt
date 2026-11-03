package com.xmu.course.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** 基于脱敏的学生课表 iframe 样本；每个上课时间 span 对应一条排课。 */
class NauStudentPageAdapter : NauPageAdapter {
    private val semesterPattern = Regex("(\\d{4})\\s*-\\s*(\\d{4})\\s*学年\\s*第([一二三123])学期")
    private val timePattern = Regex("^(.+?)\\s+周\\s*([1-7])\\s+第\\s*(\\d+)\\s*[-至]\\s*(\\d+)\\s*节$")

    override fun pageState(document: Document): TimetablePageState {
        val table = document.selectFirst("table#content")
        if (table == null) {
            return if (document.selectFirst("iframe#FrameContent") != null) TimetablePageState.LOADING
            else TimetablePageState.WRONG_PAGE
        }
        val headings = table.select("tr:first-child th").map { it.text().trim() }
        return if (headings.getOrNull(2) == "课程名称" && headings.getOrNull(7) == "任课教师") {
            TimetablePageState.READY
        } else TimetablePageState.WRONG_PAGE
    }

    override fun isReady(document: Document): Boolean =
        pageState(document) == TimetablePageState.READY &&
            document.select("table#content tr td:nth-child(9) > span").isNotEmpty()

    override fun semester(document: Document): ParsedSemester {
        val title = document.selectFirst(".tdTitle")?.ownText().orEmpty()
        val match = semesterPattern.find(title) ?: return ParsedSemester("", "")
        val (start, end, termText) = match.destructured
        val term = when (termText) { "一", "1" -> 1; "二", "2" -> 2; else -> 3 }
        return ParsedSemester("$start-$end-$term", match.value.replace(Regex("\\s+"), ""))
    }

    override fun courseNodes(document: Document): List<Element> =
        document.select("table#content tr").filter { it.select("> td").isNotEmpty() }.flatMap { row ->
            val fields = row.select("> td")
            val spans = fields.getOrNull(8)?.select("> span").orEmpty()
            // 保留缺损行供解析器产生警告，避免漏报。
            if (spans.isEmpty()) listOf(row) else spans
        }

    override fun course(node: Element): ParsedCourse {
        require(node.tagName() == "span") { "missing time" }
        val fields = requireNotNull(node.closest("tr")).select("> td")
        require(fields.size >= 9)
        val time = requireNotNull(timePattern.matchEntire(node.text().trim()))
        val (weekText, day, start, end) = time.destructured
        val first = start.toInt()
        val last = end.toInt()
        require(last >= first)
        var previous = node.previousSibling()
        var location = ""
        while (previous != null) {
            if (previous is Element && previous.tagName() == "span") break
            if (previous is TextNode) {
                val text = previous.wholeText.trim()
                if (text.startsWith("上课地点：")) {
                    location = text.substringAfter("上课地点：").trim()
                    break
                }
            }
            previous = previous.previousSibling()
        }
        return ParsedCourse(
            name = fields[2].text(), teacher = fields[7].text(), location = location,
            dayOfWeek = day.toInt(), startSection = first, duration = last - first + 1,
            weeks = WeekPatternParser.parse(weekText),
        )
    }
}
