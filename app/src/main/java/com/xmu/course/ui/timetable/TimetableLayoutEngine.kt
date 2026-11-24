package com.xmu.course.ui.timetable

import com.xmu.course.domain.Course
import com.xmu.course.domain.occursInWeek

/** 一门课在某周网格中的布局位置：laneIndex/laneCount 为其所在冲突组内的横向槽位。 */
data class TimetableLayoutItem(
    val course: Course,
    val laneIndex: Int = 0,
    val laneCount: Int = 1,
    val isCurrentWeek: Boolean = true,
)

/**
 * 课表布局引擎（纯 Kotlin，可单元测试）。
 *
 * 三个维度严格解耦：
 * - 周次（occursInWeek）决定"该课程本周是否存在"，在碰撞计算【之前】过滤；
 * - 节次（startSection + duration）决定纵向位置和高度，绝不参与横向计算；
 * - 真实时间重叠（interval overlap）决定横向分栏，绝不改变纵向高度。
 *
 * 流程：allCourses → filter(occursInWeek) → mergeSameCourse → 分天 → 冲突组分栏。
 * 非本周课程（ghost）单独合并与分栏，绝不影响本周课程的布局。
 */
object TimetableLayoutEngine {

    /** 输入全部课程与目标周，输出 天 -> 布局项。onlyCurrentWeek=true 时隐藏非本周课程。 */
    fun layoutForWeek(
        courses: List<Course>,
        week: Int,
        onlyCurrentWeek: Boolean = false,
    ): Map<Int, List<TimetableLayoutItem>> {
        val cleaned = courses.filter { it.dayOfWeek in 1..7 }
        val active = cleaned.filter { it.occursInWeek(week) }
        val inactive = if (onlyCurrentWeek) emptyList() else cleaned.filter { !it.occursInWeek(week) }

        val result = mutableMapOf<Int, MutableList<TimetableLayoutItem>>()

        // 本周课程：合并同课记录 → 分天 → 冲突组分栏。
        mergeSameCourse(active).groupBy { it.dayOfWeek }.forEach { (day, dayCourses) ->
            val items = assignLanesItems(dayCourses.map { TimetableLayoutItem(it, isCurrentWeek = true) })
            result.getOrPut(day) { mutableListOf() }.addAll(items)
        }

        // 非本周课程：独立 ghost 布局（独立合并与分栏，不参与本周碰撞）。
        mergeSameCourse(inactive).groupBy { it.dayOfWeek }.forEach { (day, dayCourses) ->
            val items = assignLanesItems(dayCourses.map { TimetableLayoutItem(it, isCurrentWeek = false) })
            result.getOrPut(day) { mutableListOf() }.addAll(items)
        }
        return result
    }

    /**
     * 合并同一天上、同名同教师同教室、节次重叠或相邻的课程记录。
     * 例如金智把"1-2周"和"3-16周"拆成两条记录：合并后为一张 1-16 周的整卡。
     * 不同课程名永远不会被合并（同位置允许并存多门课）。
     */
    fun mergeSameCourse(courses: List<Course>): List<Course> {
        return courses
            .groupBy { listOf(it.dayOfWeek, it.name, it.teacher, it.location) }
            .flatMap { (_, group) ->
                val sorted = group.sortedBy { it.startSection }
                val merged = mutableListOf(sorted.first())
                for (course in sorted.drop(1)) {
                    val last = merged.last()
                    val lastEnd = last.startSection + last.duration - 1
                    val end = course.startSection + course.duration - 1
                    if (course.startSection <= lastEnd + 1) {
                        merged[merged.lastIndex] = last.copy(
                            duration = maxOf(lastEnd, end) - last.startSection + 1,
                            weeks = last.weeks + course.weeks,
                        )
                    } else {
                        merged.add(course)
                    }
                }
                merged
            }
    }

    /** 单天内的冲突分栏（兼容旧签名）：整组按冲突连通分量分栏。 */
    fun assignLanes(courses: List<Course>): List<TimetableLayoutItem> =
        assignLanesItems(courses.map { TimetableLayoutItem(it, isCurrentWeek = true) })

    /**
     * 单天分栏：
     * 1. 按开始节次排序；
     * 2. 用"interval overlap 链"切分冲突组（A 与 B 重叠、B 与 C 重叠 → A/B/C 同组）；
     * 3. 组内贪婪分配 lane；laneCount 只在组内统计，不影响其他时间段的课程宽度。
     */
    private fun assignLanesItems(items: List<TimetableLayoutItem>): List<TimetableLayoutItem> =
        placeIntervals(items, { it.course.startSection }, { it.course.startSection + it.course.duration })
            .map { it.item.copy(laneIndex = it.lane, laneCount = it.count) }
}

internal data class Lane<T>(val item: T, val lane: Int, val count: Int)

/** Allocate equal widths within each connected group of half-open intervals. */
internal fun <T> placeIntervals(items: List<T>, start: (T) -> Int, end: (T) -> Int): List<Lane<T>> {
    val result = mutableListOf<Lane<T>>()
    val group = mutableListOf<Pair<T, Int>>()
    val ends = mutableListOf<Int>()
    var groupEnd = Int.MIN_VALUE

    fun flush() {
        group.forEach { (item, lane) -> result += Lane(item, lane, ends.size) }
        group.clear()
        ends.clear()
    }
    for (item in items.sortedWith(compareBy(start).thenByDescending(end))) {
        val from = start(item)
        val until = end(item)
        if (from >= groupEnd) {
            flush()
            groupEnd = until
        }
        var lane = ends.indexOfFirst { it <= from }
        if (lane < 0) {
            lane = ends.size
            ends.add(until)
        } else ends[lane] = until
        group += item to lane
        groupEnd = maxOf(groupEnd, until)
    }
    flush()
    return result
}
