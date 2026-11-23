package com.xmu.course.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val examZone: ZoneId = ZoneId.of("Asia/Shanghai")
private val examDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val examTime = DateTimeFormatter.ofPattern("HH:mm")

data class Exam(
    val courseCode: String,
    val name: String,
    val teachingClass: String,
    val startAt: Long,
    val endAt: Long,
    val location: String,
) {
    fun date() = Instant.ofEpochMilli(startAt).atZone(examZone).toLocalDate()

    fun scheduleText(): String {
        val start = Instant.ofEpochMilli(startAt).atZone(examZone)
        val end = Instant.ofEpochMilli(endAt).atZone(examZone)
        return "${start.format(examDateTime)}–${end.format(examTime)}  ${location.ifBlank { "地点待定" }}"
    }
}
