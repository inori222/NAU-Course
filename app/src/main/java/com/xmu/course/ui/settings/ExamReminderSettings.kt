package com.xmu.course.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.xmu.course.reminder.ExamLead
import com.xmu.course.reminder.ExamReminders

@Composable
internal fun ExamReminderSettings() {
    val context = LocalContext.current
    val settings by remember(context) { ExamReminders.preferences(context) }.collectAsState(initial = emptyMap())
    var requested by rememberSaveable { mutableStateOf<ExamLead?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        requested?.let { if (granted) ExamReminders.setEnabled(context, it, true) }
        requested = null
        showSettings = !granted
    }
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("考前提醒", style = MaterialTheme.typography.titleMedium)
            Text("按北京时间安排本地通知，系统省电时可能延后。", style = MaterialTheme.typography.bodySmall)
            ExamLead.entries.forEach { lead ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(lead.label)
                    Switch(checked = settings.getOrDefault(lead, 0L) > 0L, onCheckedChange = { enabled ->
                        if (!enabled || NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                            ExamReminders.setEnabled(context, lead, enabled)
                        } else if (Build.VERSION.SDK_INT >= 33) {
                            requested = lead
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else showSettings = true
                    })
                }
            }
        }
    }
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false },
        title = { Text("请允许考试通知") }, text = { Text("打开系统通知权限后，返回这里开启所需的提醒。") },
        confirmButton = { TextButton(onClick = {
            showSettings = false
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("系统设置") } },
        dismissButton = { TextButton(onClick = { showSettings = false }) { Text("关闭") } })
}
