package com.xmu.course.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xmu.course.domain.Exam
import java.time.LocalDate

internal fun examsInWeek(exams: List<Exam>, startDate: String?, week: Int): List<Exam> {
    val start = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return emptyList()
    val from = start.plusWeeks(week - 1L)
    val until = from.plusWeeks(1)
    return exams.filter { it.date() >= from && it.date() < until }
}

@Composable
internal fun WeekExams(exams: List<Exam>, startDate: String?, week: Int, onRemove: (Exam) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Exam?>(null) }
    val weekly = remember(exams, startDate, week) { examsInWeek(exams, startDate, week) }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().clickable { showAll = true }.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (startDate == null) "学期考试" else "本周考试 · ${weekly.size} 场")
                Text("查看全部 ${exams.size} 场")
            }
            if (startDate == null) Text("设置开学日期后可按周查看", style = MaterialTheme.typography.bodySmall)
            weekly.take(2).forEach { Text("${it.name}  ${it.scheduleText()}", style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showAll) AlertDialog(onDismissRequest = { showAll = false }, title = { Text("本学期考试") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exams.forEach { exam ->
                    Text("${exam.name}\n${exam.scheduleText()}")
                    TextButton(onClick = { removing = exam }) { Text("移除此场") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showAll = false }) { Text("关闭") } },
    )
    removing?.let { exam ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("移除这场考试？") },
            text = { Text("${exam.name}\n${exam.scheduleText()}\n对应的考前提醒也会取消。") },
            confirmButton = { TextButton(onClick = { onRemove(exam); removing = null }) { Text("移除") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } })
    }
}
