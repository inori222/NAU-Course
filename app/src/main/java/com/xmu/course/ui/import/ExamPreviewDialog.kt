package com.xmu.course.ui.import

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xmu.course.data.ExamImportPreview

@Composable
internal fun ExamPreviewDialog(preview: ExamImportPreview, confirm: () -> Unit, cancel: () -> Unit) {
    AlertDialog(onDismissRequest = cancel, title = { Text("确认考试日程") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(preview.schedule.semester.name)
                Text("新增 ${preview.added.size} 场，移除 ${preview.removed.size} 场")
                if (preview.schedule.skippedRows > 0) Text("有 ${preview.schedule.skippedRows} 行无法识别。本次只添加有效记录，保留已有场次。")
                preview.removed.forEach { Text("移除 · ${it.exam.name}\n${it.exam.scheduleText()}") }
                preview.added.forEach { Text("新增 · ${it.name}\n${it.scheduleText()}") }
            }
        },
        confirmButton = { TextButton(onClick = confirm) { Text("确认导入") } },
        dismissButton = { TextButton(onClick = cancel) { Text("取消") } },
    )
}
