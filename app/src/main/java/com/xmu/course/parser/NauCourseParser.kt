package com.xmu.course.parser

import java.util.concurrent.CancellationException
import org.jsoup.Jsoup

/**
 * 南审导入解析流水线；默认使用基于脱敏样本的学生页面适配器。
 * 不对未知 DOM 使用猜测或回退到其他学校解析器。
 */
class NauCourseParser(private val adapter: NauPageAdapter? = NauStudentPageAdapter()) : CourseParser {
    val isConfigured: Boolean get() = adapter != null
    override fun parse(html: String): ParseResult {
        if (html.isBlank()) return failure(ParseFailure.EMPTY_HTML, "输入 HTML 为空")
        val pageAdapter = adapter
            ?: return failure(ParseFailure.ADAPTATION_REQUIRED, ADAPTATION_MESSAGE)
        try {
            val document = Jsoup.parse(html)
            when (pageAdapter.pageState(document)) {
                TimetablePageState.WRONG_PAGE -> return failure(ParseFailure.WRONG_PAGE, "当前不是已适配的我的课表页面，请自行登录并进入课表")
                TimetablePageState.LOADING -> return failure(ParseFailure.NOT_READY, "课表尚未渲染完成，请等待课程加载后重试")
                TimetablePageState.READY -> Unit
            }
            val semester = pageAdapter.semester(document)
            if (semester.code.isBlank() || semester.name.isBlank()) {
                return failure(ParseFailure.MISSING_SEMESTER, "未识别到学期，请确认页面显示了学期信息")
            }
            val nodes = pageAdapter.courseNodes(document)
            if (nodes.isEmpty()) return failure(ParseFailure.NO_COURSES, "没有找到课程节点，不会覆盖本地课表")

            val warnings = mutableListOf<String>()
            val courses = linkedSetOf<ParsedCourse>()
            nodes.forEachIndexed { index, node ->
                val label = "第 ${index + 1} 条课程"
                try {
                    val raw = pageAdapter.course(node)
                    val course = raw.copy(
                        name = raw.name.trim(),
                        teacher = raw.teacher.trim(),
                        location = raw.location.trim(),
                        weeks = raw.weeks.toSortedSet(),
                    )
                    if (!isValid(course)) {
                        warnings += "$label 缺少名称或包含无效星期、节次、周次，已跳过"
                    } else if (!courses.add(course)) {
                        warnings += "$label 与已有记录重复，已合并"
                    } else {
                        if (course.teacher.isBlank()) warnings += "$label 缺少教师，已保留课程"
                        if (course.location.isBlank()) warnings += "$label 缺少教室，已保留课程"
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 异常消息可能包含页面内容，不复制到日志/提示；只标识记录序号。
                    warnings += "$label 读取失败，已跳过"
                }
            }
            if (courses.isEmpty()) warnings += "没有可导入的有效课程，不会覆盖本地课表"
            return ParseResult(
                semesterCode = semester.code.trim(),
                semesterName = semester.name.trim(),
                courses = courses.toList(),
                warnings = warnings,
                failure = if (courses.isEmpty()) ParseFailure.NO_COURSES else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return failure(ParseFailure.INVALID_HTML, "无法读取页面结构，请提供脱敏 HTML 以检查适配")
        }
    }

    /** 动态页面轮询的独立就绪检查；将来只需通过真实页面适配器识别 DOM。 */
    fun isTimetableReady(html: String): Boolean {
        val pageAdapter = adapter ?: return false
        if (html.isBlank()) return false
        return try {
            val document = Jsoup.parse(html)
            pageAdapter.pageState(document) == TimetablePageState.READY && pageAdapter.isReady(document)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun isValid(course: ParsedCourse): Boolean =
        course.name.isNotBlank() && course.dayOfWeek in 1..7 &&
            course.startSection in 1..100 && course.duration in 1..100 &&
            course.startSection + course.duration - 1 <= 100 &&
            course.weeks.isNotEmpty() && course.weeks.all { it in 1..100 }

    private fun failure(reason: ParseFailure, message: String) =
        ParseResult("", "", emptyList(), listOf(message), reason)

    companion object {
        const val ADAPTATION_MESSAGE = "南审课表页面尚未适配，需要提供南京审计大学我的课表页面完整 HTML"
    }
}
