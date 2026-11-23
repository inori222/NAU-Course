package com.xmu.course.parser

private val termPattern = Regex("""(\d{4})\s*-\s*(\d{4})\s*学年\s*第([一二三123])学期""")

internal fun parseNauSemester(title: String): ParsedSemester {
    val match = termPattern.find(title) ?: return ParsedSemester("", "")
    val (start, end, termText) = match.destructured
    val term = when (termText) {
        "一", "1" -> 1
        "二", "2" -> 2
        else -> 3
    }
    return ParsedSemester("$start-$end-$term", match.value.replace(Regex("""\s+"""), ""))
}
