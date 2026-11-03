package com.xmu.course.parser

/** 纯 HTML 解析契约。调用方负责在后台 dispatcher 执行，不访问网络或数据库。 */
fun interface CourseParser {
    fun parse(html: String): ParseResult
}
