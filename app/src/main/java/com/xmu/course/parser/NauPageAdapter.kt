package com.xmu.course.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** 学校页面的三种状态。仅在真实课表 DOM 已渲染后返回 READY。 */
enum class TimetablePageState { WRONG_PAGE, LOADING, READY }

data class ParsedSemester(val code: String, val name: String)

/**
 * 南审页面结构的唯一适配边界。
 *
 * 尚无真实页面，因此当前没有此接口的生产实现。
 * 收到脱敏 HTML 后，在 parser 包内实现它；禁止把 selector 放入 UI、Repository 或 Room。
 * 此接口只读取传入的 Jsoup DOM，不读取 Cookie、不请求网络、不保存 HTML。
 */
interface NauPageAdapter {
    fun pageState(document: Document): TimetablePageState
    /** 供动态页面轮询使用，适配器可要求实际排课节点出现。 */
    fun isReady(document: Document): Boolean = pageState(document) == TimetablePageState.READY
    fun semester(document: Document): ParsedSemester
    fun courseNodes(document: Document): List<Element>
    fun course(node: Element): ParsedCourse
}
