package com.xmu.course.ui.background

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** 将用户选中的图片复制到应用私有存储，后续显示不依赖外部内容提供方。 */
object BackgroundImageStore {
    suspend fun copyToPrivateStorage(context: Context, source: Uri): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "timetable_backgrounds")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("无法创建背景目录")
        val target = File(directory, UUID.randomUUID().toString())
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("无法读取背景图片")
            input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
            Uri.fromFile(target).toString()
        } catch (error: Exception) {
            // 只清除本次创建的单个未完成文件，不删除用户已有图片。
            if (target.exists()) target.delete()
            throw error
        }
    }
}
