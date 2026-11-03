package com.xmu.course.parser

/**
 * 周次文本解析器。
 *
 * 支持金智教务出现的全部格式（实测样张）：
 * - 区间全周：`1-16周`、`3-16周`
 * - 区间单/双周：`1-15单周`、`2-16双周`
 * - 离散周：`1,4,7`
 * - 混合列表：`1-3单周,6,8-9,11-13单周,15-16周`
 *
 * 解析失败的分段直接跳过，绝不让异常格式导致崩溃。
 */
object WeekPatternParser {

    // 防止异常输入展开数十亿个周号；与学校 DOM 无关。
    private const val MAX_WEEK = 100

    fun parse(text: String): Set<Int> {
        val result = sortedSetOf<Int>()
        val normalized = text.replace("，", ",")
            .replace("（", "(").replace("）", ")")
            .replace("之单周", "单周").replace("之双周", "双周")
            .filterNot { it.isWhitespace() }.trim()
        if (normalized.isEmpty()) return result

        // 整个列表后的括号单双周限定作用于整个列表；分段“单周/双周”仍按上游规则处理。
        val precedingText = normalized.substringBeforeLast('(', "")
        val hasEarlierParity = precedingText.contains("单") || precedingText.contains("双")
        val globalParity = when {
            !hasEarlierParity && (normalized.endsWith("(单)") || normalized.endsWith("(单周)")) -> 1
            !hasEarlierParity && (normalized.endsWith("(双)") || normalized.endsWith("(双周)")) -> 0
            else -> null
        }
        val body = if (globalParity != null) normalized.substringBeforeLast('(') else normalized
        for (segment in body.split(',')) {
            if (segment.isBlank()) continue
            runCatching { parseSegment(segment, result) }
        }
        if (globalParity != null) result.removeAll { it % 2 != globalParity }
        return result
    }

    /** 解析单段，如 `1-16周`、`2-16双周`、`6`、`8-9`。 */
    private fun parseSegment(segment: String, out: MutableSet<Int>) {
        // 判定单/双周标记（区间后缀，如 `1-15单周`）。
        var parity: Int? = null
        var core = segment.removePrefix("第")
        when {
            core.endsWith("(单)") -> { parity = 1; core = core.removeSuffix("(单)") }
            core.endsWith("(双)") -> { parity = 2; core = core.removeSuffix("(双)") }
            core.endsWith("(单周)") -> { parity = 1; core = core.removeSuffix("(单周)") }
            core.endsWith("(双周)") -> { parity = 2; core = core.removeSuffix("(双周)") }
            core.endsWith("单周") -> { parity = 1; core = core.removeSuffix("单周") }
            core.endsWith("双周") -> { parity = 2; core = core.removeSuffix("双周") }
        }
        core = core.removeSuffix("周")

        val rangeParts = core.split('-')
        val range = when (rangeParts.size) {
            1 -> {
                val week = rangeParts[0].toIntOrNull() ?: return
                week..week
            }
            2 -> {
                val start = rangeParts[0].toIntOrNull() ?: return
                val end = rangeParts[1].toIntOrNull() ?: return
                if (start > end) return
                start..end
            }
            else -> return
        }

        if (range.first !in 1..MAX_WEEK || range.last !in 1..MAX_WEEK) return
        for (week in range) {
            val matchesParity = parity == null ||
                (parity == 1 && week % 2 == 1) ||
                (parity == 2 && week % 2 == 0)
            if (matchesParity && week > 0) out.add(week)
        }
    }
}
