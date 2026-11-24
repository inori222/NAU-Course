package com.xmu.course.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.xmu.course.domain.TimetableConfig
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
internal fun SemesterExams(exams: List<Exam>, startDate: String?, onSelect: (Exam) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("本学期考试") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (startDate == null) Text("尚未设置开学日期，当前自然周暂作第 1 周。可在设置中填写开学日期。")
                exams.forEach { exam ->
                    Text("${exam.name}\n${exam.scheduleText()}",
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(exam) }.padding(vertical = 4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("关闭") } })
}

@Composable
internal fun ExamDetails(exam: Exam, onRemove: () -> Unit, onClose: () -> Unit) {
    var confirming by remember(exam) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose,
        title = { Text(if (confirming) "移除这场考试？" else "考试 · ${exam.name}") },
        text = { Text(if (confirming) "${exam.name}\n对应的考前提醒也会取消。" else exam.scheduleText()) },
        confirmButton = { TextButton(onClick = if (confirming) onRemove else onClose) {
            Text(if (confirming) "确认移除" else "关闭")
        } },
        dismissButton = { TextButton(onClick = { confirming = !confirming }) {
            Text(if (confirming) "取消" else "移除此场")
        } })
}

@Composable
internal fun OutsidePeriods(label: String, height: androidx.compose.ui.unit.Dp, fontColor: Color) {
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(label, color = fontColor, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ExamCard(exam: Exam, config: TimetableConfig, textColor: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(config.cornerRadius.dp))
        .background(Color(0xFFAF3B30)).clickable(onClick = onClick).padding(3.dp),
        verticalArrangement = Arrangement.Center) {
        Text("考试 · ${exam.name}", color = textColor, fontSize = config.textSize.sp,
            lineHeight = (config.textSize + 1).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(exam.timeRange(), color = textColor, fontSize = (config.textSize - 2).coerceAtLeast(8).sp,
            lineHeight = config.textSize.sp)
        if (config.showLocation && exam.location.isNotBlank()) Text(exam.location,
            color = textColor, fontSize = (config.textSize - 2).coerceAtLeast(8).sp,
            lineHeight = config.textSize.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
