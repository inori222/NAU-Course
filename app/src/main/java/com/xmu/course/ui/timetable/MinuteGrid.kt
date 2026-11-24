package com.xmu.course.ui.timetable

import com.xmu.course.domain.Exam
import java.time.LocalTime

internal data class MinuteSpan(val start: Int, val end: Int)

internal sealed interface GridEvent {
    val span: MinuteSpan
    data class Lesson(val item: TimetableLayoutItem, override val span: MinuteSpan, val leaveBreakFree: Boolean) : GridEvent
    data class ExamSlot(val exam: Exam) : GridEvent {
        override val span = MinuteSpan(exam.startTime().toSecondOfDay() / 60, exam.endTime().toSecondOfDay() / 60)
    }
}

private val schoolPeriods = TimeTableConfig.nauSections.map { range ->
    fun minute(clock: String) = LocalTime.parse(clock).toSecondOfDay() / 60
    MinuteSpan(minute(range.substringBefore('-')), minute(range.substringAfter('-')))
}

/** Positions use the existing section height as a unit. An exam in a break uses the
 * bottom of the preceding section, without introducing another visible grid line. */
internal class MinuteGrid(val sectionCount: Int, exams: List<Exam>) {
    private val periods = schoolPeriods.take(sectionCount)
    private val examSpans = exams.map { GridEvent.ExamSlot(it).span }
    private val first = periods.first().start
    private val last = periods.last().end
    val start = minOf(first, examSpans.minOfOrNull { it.start } ?: first) / 10 * 10
    val end = (maxOf(last, examSpans.maxOfOrNull { it.end } ?: last) + 9) / 10 * 10
    val leadingRows = edgeHeight(first - start)
    val trailingRows = edgeHeight(end - last)
    val knownCount = periods.size
    val height = leadingRows + sectionCount + trailingRows

    private fun edgeHeight(minutes: Int) = if (minutes == 0) 0f else maxOf(1f, minutes / 40f)

    private val rowEnds = periods.mapIndexed { index, period ->
        val next = periods.getOrNull(index + 1)?.start ?: period.end
        if (examSpans.any { it.start < next && it.end > period.end }) next else period.end
    }

    fun sectionTop(index: Int) = leadingRows + index + if (index >= knownCount) trailingRows else 0f

    fun position(minute: Int): Float {
        if (minute < first) return leadingRows * (minute - start) / (first - start)
        if (minute >= last) {
            val tail = if (end > last) trailingRows * (minute - last) / (end - last) else 0f
            return leadingRows + knownCount + tail
        }
        val i = periods.indexOfLast { it.start <= minute }
        val ticks = (rowEnds[i] - periods[i].start) / 10f
        val elapsedTicks = (minute - periods[i].start) / 10f
        return sectionTop(i) + (elapsedTicks / ticks).coerceAtMost(1f)
    }

    fun bounds(event: GridEvent): Pair<Float, Float> = when (event) {
        is GridEvent.ExamSlot -> position(event.span.start) to position(event.span.end)
        is GridEvent.Lesson -> {
            val course = event.item.course
            val lastIndex = course.startSection + course.duration - 2
            val bottom = if (event.leaveBreakFree) position(event.span.end) else sectionTop(lastIndex) + 1
            sectionTop(course.startSection - 1) to bottom
        }
    }

    fun layout(courses: List<TimetableLayoutItem>, exams: List<Exam>): List<Lane<GridEvent>> {
        // Legacy sections with no school clock times retain numbered rows after the timed grid.
        fun period(index: Int): MinuteSpan = periods.getOrNull(index)
            ?: (end + (index - knownCount) * 40).let { MinuteSpan(it, it + 40) }

        val sittings = exams.map(GridEvent::ExamSlot)
        val lessons = courses.mapNotNull { item ->
            val course = item.course
            val lastSection = course.startSection.toLong() + course.duration - 1
            if (course.startSection !in 1..sectionCount || lastSection !in course.startSection.toLong()..sectionCount.toLong()) return@mapNotNull null
            val lastIndex = lastSection.toInt() - 1
            val span = MinuteSpan(period(course.startSection - 1).start, period(lastIndex).end)
            val rowEnd = rowEnds.getOrNull(lastIndex) ?: span.end
            val leaveBreakFree = span.end < rowEnd && sittings.any { it.span.start < rowEnd && it.span.end > span.end }
            GridEvent.Lesson(item, span, leaveBreakFree)
        }
        val ghosts = lessons.filterNot { it.item.isCurrentWeek }.map {
            Lane<GridEvent>(it, it.item.laneIndex, it.item.laneCount)
        }
        val active = lessons.filter { it.item.isCurrentWeek }
        if (exams.isEmpty()) return ghosts + active.map { Lane(it, it.item.laneIndex, it.item.laneCount) }
        return ghosts + placeIntervals(active + sittings, { it.span.start }, { it.span.end })
    }
}
