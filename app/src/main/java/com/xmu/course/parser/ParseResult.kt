package com.xmu.course.parser

/** 无数据库 ID 的排课记录；周次沿用上游 Set，避免重复周号。 */
data class ParsedCourse(
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val duration: Int,
    val weeks: Set<Int>,
)

/** 可恢复的问题放在 warnings；阻止导入的问题放在 failure。 */
data class ParseResult(
    val semesterCode: String,
    val semesterName: String,
    val courses: List<ParsedCourse>,
    val warnings: List<String> = emptyList(),
    val failure: ParseFailure? = null,
)

enum class ParseFailure {
    EMPTY_HTML,
    ADAPTATION_REQUIRED,
    WRONG_PAGE,
    NOT_READY,
    NO_COURSES,
    MISSING_SEMESTER,
    INVALID_HTML,
}
