package com.xmu.course.ui.import

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmu.course.data.CourseRepository
import com.xmu.course.data.ImportPreparation
import com.xmu.course.data.ImportResult
import com.xmu.course.data.TimetablePrefs
import com.xmu.course.data.local.AppDatabase
import com.xmu.course.domain.Course
import com.xmu.course.parser.NauCourseParser
import com.xmu.course.parser.NauStudentPageAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "XmuImport"

/** 导入页 UI 状态。[conflict] 非 null 时 UI 弹出“覆盖/取消”对话框。 */
data class ImportUiState(
    val message: String? = null,
    val conflict: ImportPreparation.Conflict? = null,
    val importCompleted: Boolean = false,
    val isImporting: Boolean = false,
)

/**
 * 导入 ViewModel：HTML（WebView 抓取或本地文件）→ Parser → Repository → Room。
 */
class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(AppDatabase.getInstance(application))
    private val parser = NauCourseParser(NauStudentPageAdapter())
    val isParserConfigured: Boolean get() = parser.isConfigured
    suspend fun isTimetableReady(html: String): Boolean =
        withContext(Dispatchers.Default) { parser.isTimetableReady(html) }

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /** 保存 WebView 抓取的 HTML 并导入数据库。 */
    fun saveCourseHtml(html: String) {
        import(html, source = "保存课表")
    }

    /** 保存用户选择的本地 HTML 并导入数据库。 */
    fun saveImportedHtml(html: String) {
        if (html.isBlank()) {
            _uiState.update { it.copy(message = "读取文件失败") }
            return
        }
        import(html, source = "本地导入")
    }

    private fun import(html: String, source: String) {
        if (_uiState.value.isImporting) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) { parser.parse(html) }
                when (val prep = repository.prepareImport(parsed)) {
                    is ImportPreparation.Ready -> {
                        Log.d(TAG, "parser 输出：${prep.courses.size} 门课程")
                        when (val r = repository.commitImport(prep.semester, prep.courses, overwrite = false)) {
                            is ImportResult.Success -> {
                                TimetablePrefs.setCurrent(getApplication(), r.timetableId)
                                _uiState.update {
                                    it.copy(message = importSuccessMessage(prep.semester.name, r.count, prep.courses, prep.warnings), importCompleted = true)
                                }
                            }
                            ImportResult.Cancelled ->
                                _uiState.update { it.copy(message = "导入已取消") }
                        }
                    }
                    is ImportPreparation.Conflict -> {
                        Log.d(TAG, "parser 输出：冲突（新 ${prep.pendingCourses.size} 门）")
                        _uiState.update { it.copy(conflict = prep) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.xmu.course.data.ImportEmptyException) {
                _uiState.update { it.copy(message = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "$source 失败：${e.message}") }
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    /** 用户确认覆盖导入。 */
    fun confirmOverwrite() {
        val conflict = _uiState.value.conflict ?: return
        _uiState.update { it.copy(conflict = null, isImporting = true) }
        viewModelScope.launch {
            try {
                when (val r = repository.commitImport(conflict.semester, conflict.pendingCourses, overwrite = true)) {
                    is ImportResult.Success -> {
                        TimetablePrefs.setCurrent(getApplication(), r.timetableId)
                        _uiState.update {
                            it.copy(message = importSuccessMessage(conflict.semester.name, r.count, conflict.pendingCourses, conflict.warnings), importCompleted = true)
                        }
                    }
                    ImportResult.Cancelled -> _uiState.update { it.copy(message = "导入已取消") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "导入失败，请检查课表后重试") }
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    /** 用户取消覆盖。 */
    fun cancelOverwrite() {
        _uiState.update { it.copy(conflict = null, message = "导入已取消") }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    fun importConsumed() {
        _uiState.update { it.copy(importCompleted = false) }
    }

    /** 成功提示：学期 + 课程数 + 周数范围。 */
    private fun importSuccessMessage(semesterName: String, count: Int, courses: List<Course>, warnings: List<String>): String {
        val weeks = courses.flatMap { it.weeks }
        val range = if (weeks.isEmpty()) "" else "，第 ${weeks.min()}-${weeks.max()} 周"
        val warningText = if (warnings.isEmpty()) "" else "\n" + warnings.joinToString("\n")
        return "导入成功：$semesterName，$count 门课程$range$warningText"
    }

    companion object {
        const val IMPORTED_TEMP_FILE = "imported_course.html"
    }
}


